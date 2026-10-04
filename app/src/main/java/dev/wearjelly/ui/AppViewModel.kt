package dev.wearjelly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wearjelly.data.JellyfinException
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.JellyfinRepository
import dev.wearjelly.data.LibraryKind
import dev.wearjelly.data.ServerSession
import dev.wearjelly.data.SongLyrics
import dev.wearjelly.data.normalizeServerUrl
import dev.wearjelly.data.offline.OfflineLibraryReader
import dev.wearjelly.data.offline.SyncState
import dev.wearjelly.playback.PlaybackConnection
import dev.wearjelly.playback.PlaybackState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LibraryQuery(
    val kind: LibraryKind,
    val parentId: String? = null,
    val artistId: String? = null,
    /** 非空表示这是某个播放列表的内容视图，走 `/Playlists/{id}/Items` 并按列表顺序展示。 */
    val playlistId: String? = null,
)

internal enum class BatchSource { LIBRARY, DOWNLOADS, HISTORY, QUEUE, ARTISTS }

internal sealed interface AppScreen {
    data object Login : AppScreen
    data object Home : AppScreen
    data class Library(val query: LibraryQuery, val title: String) : AppScreen
    data class Track(val item: JellyfinItem, val source: LibraryQuery) : AppScreen
    data object Player : AppScreen
    data object Queue : AppScreen
    data object Lyrics : AppScreen
    data object LyricsSettings : AppScreen
    data object Downloads : AppScreen
    data object History : AppScreen
    data class BatchActions(
        val query: LibraryQuery,
        val title: String,
        val source: BatchSource = BatchSource.LIBRARY,
    ) : AppScreen
    /** 列表级长按菜单（原详情页顶部"更多"迁移至此，按 [ListMenu] 类型给菜单项）。 */
    data class ListActions(val menu: ListMenu, val title: String) : AppScreen
    data class SongInfo(val item: JellyfinItem) : AppScreen
    data object Settings : AppScreen
    data class Confirm(
        val action: ConfirmAction,
        val query: LibraryQuery? = null,
        val menu: ListMenu? = null,
    ) : AppScreen
}

internal enum class ConfirmAction { CLEAR_QUEUE, SWITCH_SERVER, LOGOUT, DELETE_LIST_CACHES }

// AccountUi contains no token. Login values are deliberately never put into saved state.
internal data class AccountUi(val serverUrl: String, val userName: String)

internal data class LoginUi(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

internal data class LibraryUi(
    val items: List<JellyfinItem> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val retryFromStart: Boolean = false,
)

internal data class LyricsUi(
    val itemId: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val lyrics: SongLyrics? = null,
    val error: String? = null,
    /** 服务器确认无歌词（NotFound）：按"纯音乐"展示，与加载失败区分。 */
    val notFound: Boolean = false,
)

internal data class AppUiState(
    val account: AccountUi? = null,
    val login: LoginUi = LoginUi(),
    val backStack: List<AppScreen> = listOf(AppScreen.Login),
    val libraries: Map<LibraryQuery, LibraryUi> = emptyMap(),
    val lyrics: LyricsUi = LyricsUi(),
    val notice: String? = null,
    val sessionGeneration: Long = 0,
    val selection: SelectionSnapshot = SelectionSnapshot(),
) {
    val screen: AppScreen get() = backStack.last()
    val selectionMode: Boolean get() = selection.active
    val selectedSongIds: Set<String> get() = selection.selectedIds
}

class AppViewModel(
    val repository: JellyfinRepository,
    val playback: PlaybackConnection,
    val downloadManager: dev.wearjelly.data.DownloadManager,
    val historyStore: dev.wearjelly.data.HistoryStore,
    private val lastPlaybackStore: dev.wearjelly.data.LastPlaybackStore,
    private val lyricsPrefs: dev.wearjelly.data.LyricsPrefs,
    private val offlineReader: OfflineLibraryReader,
    private val offlineSync: dev.wearjelly.data.offline.OfflineSyncCoordinator,
) : ViewModel() {
    val session: StateFlow<ServerSession?> = repository.session
    val playbackState: StateFlow<PlaybackState> = playback.state
    val bitrate: StateFlow<dev.wearjelly.data.AudioBitrate> = repository.bitrate
    val downloadedSongs: StateFlow<List<dev.wearjelly.data.DownloadedSong>> = downloadManager.downloads
    val downloadingIds: StateFlow<Set<String>> = downloadManager.downloadingIds
    val downloadProgress: StateFlow<Map<String, dev.wearjelly.data.DownloadProgress>> = downloadManager.progress
    val downloadQueue: StateFlow<List<dev.wearjelly.data.QueueEntry>> = downloadManager.queue
    val history: StateFlow<List<dev.wearjelly.data.HistoryEntry>> = historyStore.entries
    val lastPlayback: StateFlow<dev.wearjelly.data.LastPlayback?> = lastPlaybackStore.lastPlayback
    val cachedTrackIds: StateFlow<Set<String>> = downloadManager.cachedTrackIds
    val offlineSyncState: StateFlow<SyncState> = offlineSync.state
    private var offlineMode = false
    private val mutableUi = MutableStateFlow(AppUiState())
    internal val uiState = mutableUi.asStateFlow()

    private var observedSession: ServerSession? = null
    private var generation = 0L
    private var loginJob: Job? = null
    private var lyricsJob: Job? = null
    private val libraryJobs = mutableMapOf<LibraryQuery, Job>()
    private var nextLogin: LoginUi? = null

    init {
        acceptSession(repository.session.value, initial = true)
        viewModelScope.launch {
            if (repository.session.value == null && offlineReader.hasSnapshot()) {
                offlineMode = true
                mutableUi.update { it.copy(backStack = listOf(AppScreen.Home), login = it.login.copy(error = null)) }
            }
        }
        viewModelScope.launch {
            repository.session.collect { value ->
                if (value != observedSession) acceptSession(value)
            }
        }
        viewModelScope.launch {
            playback.state.map { it.current?.id }.distinctUntilChanged().collect { itemId ->
                lyricsJob?.cancel()
                mutableUi.update { it.copy(lyrics = LyricsUi(itemId = itemId)) }
                if (mutableUi.value.screen == AppScreen.Lyrics) loadLyrics()
            }
        }
    }

    private fun acceptSession(value: ServerSession?, initial: Boolean = false) {
        val previous = observedSession
        observedSession = value
        offlineMode = value == null && offlineMode
        generation += 1
        loginJob?.cancel()
        libraryJobs.values.forEach(Job::cancel)
        libraryJobs.clear()
        lyricsJob?.cancel()
        val login = if (value == null) {
            nextLogin ?: LoginUi(server = previous?.serverUrl.orEmpty())
        } else {
            LoginUi()
        }
        nextLogin = null
        mutableUi.value = AppUiState(
            account = value?.let { AccountUi(it.serverUrl, it.userName) },
            login = login,
            backStack = listOf(if (value == null) AppScreen.Login else AppScreen.Home),
            sessionGeneration = generation,
        )
        // Playback is a process singleton: do not release it when this ViewModel is cleared.
        // Its own session observer also revokes the old service queue.
        if (!initial && previous != null) {
            try {
                playback.stopAndClear()
            } catch (_: Exception) {
                showNotice("无法停止旧的播放会话，请在播放器中重试。")
            }
        }
        if (value != null) reconnectPlayback()
    }

    internal fun changeServer(value: String) = changeLogin { copy(server = value) }
    internal fun changeUsername(value: String) = changeLogin { copy(username = value) }
    internal fun changePassword(value: String) = changeLogin { copy(password = value) }

    private fun changeLogin(change: LoginUi.() -> LoginUi) {
        mutableUi.update {
            if (it.login.submitting) it else it.copy(login = it.login.change().copy(error = null))
        }
    }

    internal fun login() {
        val input = mutableUi.value.login
        if (input.submitting || repository.session.value != null) return
        val server = try {
            normalizeServerUrl(input.server.trim())
        } catch (_: IllegalArgumentException) {
            mutableUi.update {
                it.copy(login = it.login.copy(error = "请输入完整的 http:// 或 https:// 服务器地址，可包含反向代理路径。"))
            }
            return
        }
        if (input.username.isBlank() || input.password.isEmpty()) {
            mutableUi.update { it.copy(login = it.login.copy(error = "请填写用户名和密码。")) }
            return
        }
        val requestGeneration = generation
        mutableUi.update { it.copy(login = it.login.copy(submitting = true, error = null)) }
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            try {
                repository.login(server, input.username.trim(), input.password)
                // A repository session emission normally handles this. Cover synchronous saves too.
                val saved = repository.session.value
                if (saved != null && saved != observedSession) acceptSession(saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == generation) {
                    mutableUi.update { it.copy(login = it.login.copy(error = userMessage(error))) }
                }
            } finally {
                if (requestGeneration == generation) {
                    mutableUi.update { it.copy(login = it.login.copy(submitting = false)) }
                }
            }
        }
    }

    internal fun cancelLogin() {
        loginJob?.cancel()
        mutableUi.update { it.copy(login = it.login.copy(submitting = false, password = "", error = null)) }
    }

    internal fun openLibrary(kind: LibraryKind) {
        navigate(AppScreen.Library(LibraryQuery(kind), kind.chineseTitle))
    }

    internal fun openItem(query: LibraryQuery, item: JellyfinItem) {
        when (query.kind) {
            // 点击艺人：直接进入该艺人全部歌曲列表（不经过专辑页）
            LibraryKind.ARTISTS -> navigate(
                AppScreen.Library(LibraryQuery(LibraryKind.SONGS, artistId = item.id), item.name),
            )
            LibraryKind.ALBUMS -> navigate(
                AppScreen.Library(LibraryQuery(LibraryKind.SONGS, parentId = item.id), item.name),
            )
            LibraryKind.SONGS, LibraryKind.DOWNLOADS -> navigate(AppScreen.Track(item, query))
            LibraryKind.PLAYLISTS -> navigate(
                AppScreen.Library(LibraryQuery(LibraryKind.SONGS, playlistId = item.id), item.name),
            )
        }
    }

    /** 打开列表级长按菜单（主页 6 入口与实体条目统一入口）。 */
    internal fun openListMenu(menu: ListMenu, title: String) {
        navigate(AppScreen.ListActions(menu, title))
    }

    internal fun openTrack(item: JellyfinItem, source: LibraryQuery) {
        navigate(AppScreen.Track(item, source))
    }

    internal fun navigate(screen: AppScreen) {
        if (repository.session.value == null && !offlineMode && screen != AppScreen.Login) return
        val stack = mutableUi.value.backStack
        if (stack.last() == screen) return
        val existing = stack.indexOfLast { it == screen }
        val updated = if (existing >= 0) stack.take(existing + 1) else stack + screen
        mutableUi.update { it.copy(backStack = updated, notice = null) }
        reconcileLoads()
    }

    internal fun goBack() {
        val stack = mutableUi.value.backStack
        if (stack.size <= 1) return
        mutableUi.update { it.copy(backStack = stack.dropLast(1), notice = null) }
        reconcileLoads()
    }

    private fun reconcileLoads() {
        val state = mutableUi.value
        val retainedQueries = state.backStack.filterIsInstance<AppScreen.Library>().map { it.query }.toSet()
        libraryJobs.keys.filter { it !in retainedQueries }.forEach { libraryJobs.remove(it)?.cancel() }
        mutableUi.update { it.copy(libraries = it.libraries.filterKeys { query -> query in retainedQueries }) }
        if (state.screen != AppScreen.Lyrics) {
            lyricsJob?.cancel()
            mutableUi.update { it.copy(lyrics = it.lyrics.copy(loading = false)) }
        }
        when (val screen = state.screen) {
            is AppScreen.Library -> {
                val library = mutableUi.value.libraries[screen.query]
                if (library == null || (!library.loaded && !library.loading && library.error == null)) {
                    loadLibrary(screen.query, fromStart = true)
                }
            }
            AppScreen.Lyrics -> loadLyrics()
            else -> Unit
        }
    }

    internal fun refreshLibrary(query: LibraryQuery) = loadLibrary(query, fromStart = true)

    internal fun loadMore(query: LibraryQuery) {
        val current = mutableUi.value.libraries[query] ?: return
        if (!current.loading && !current.endReached) loadLibrary(query, fromStart = false)
    }

    internal fun retryLibrary(query: LibraryQuery) {
        val current = mutableUi.value.libraries[query] ?: LibraryUi()
        loadLibrary(query, fromStart = current.retryFromStart || !current.loaded)
    }

    private fun loadLibrary(query: LibraryQuery, fromStart: Boolean) {
        if (repository.session.value == null && !offlineMode) return
        if (query.kind == LibraryKind.DOWNLOADS) return
        val old = mutableUi.value.libraries[query] ?: LibraryUi()
        if (old.loading && !fromStart) return
        libraryJobs.remove(query)?.cancel()
        val requestGeneration = generation
        updateLibrary(query) { copy(loading = true, error = null, retryFromStart = fromStart) }
        libraryJobs[query] = viewModelScope.launch {
            try {
                if (offlineMode) {
                    val offset = if (fromStart) 0 else old.nextOffset
                    val page = offlineReader.page(
                        query.kind,
                        query.playlistId,
                        query.parentId,
                        query.artistId,
                        offset,
                        PAGE_SIZE
                    )
                    val items = if (fromStart) page.items else old.items + page.items
                    val ordered = if (query.playlistId != null) {
                        items
                    } else {
                        dev.wearjelly.data.PinyinSort.sort(items.distinctBy { it.id })
                    }
                    updateLibrary(query) {
                        copy(items = ordered, total = page.totalRecordCount, nextOffset = ordered.size,
                            loading = false, loaded = true, endReached = ordered.size >= page.totalRecordCount,
                            error = null)
                    }
                    return@launch
                }
                // 全量拉取后按英文/拼音首字母 A-Z# 本地排序，保证字母条顺序正确
                val all = mutableListOf<JellyfinItem>()
                var offset = if (fromStart) 0 else old.nextOffset
                if (!fromStart) all += old.items
                var total = Int.MAX_VALUE
                while (offset < total && offset < MAX_FETCH_ITEMS) {
                    val playlistId = query.playlistId
                    val page = if (playlistId != null) {
                        repository.getPlaylistItems(playlistId, startIndex = offset, limit = PAGE_SIZE)
                    } else {
                        repository.getItems(
                            kind = query.kind,
                            parentId = query.parentId,
                            artistId = query.artistId,
                            startIndex = offset,
                            limit = PAGE_SIZE,
                        )
                    }
                    if (requestGeneration != generation) return@launch
                    total = page.totalRecordCount
                    all += page.items
                    offset += page.items.size
                    if (page.items.isEmpty()) break
                }
                val ordered = dev.wearjelly.data.PinyinSort.sort(all.distinctBy { it.id })
                updateLibrary(query) {
                    copy(
                        items = ordered,
                        total = ordered.size,
                        nextOffset = ordered.size,
                        loading = false,
                        loaded = true,
                        endReached = true,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == generation) {
                    updateLibrary(query) { copy(loading = false, error = userMessage(error)) }
                }
            }
        }
    }

    private fun updateLibrary(query: LibraryQuery, change: LibraryUi.() -> LibraryUi) {
        mutableUi.update { state ->
            state.copy(libraries = state.libraries + (query to (state.libraries[query] ?: LibraryUi()).change()))
        }
    }

    internal fun isDownloaded(itemId: String): Boolean = downloadManager.isDownloaded(itemId)

    internal fun downloadTrack(item: JellyfinItem) {
        downloadManager.enqueue(listOf(item))
        showNotice("已加入缓存队列")
    }

    internal fun deleteDownloadedTrack(itemId: String) {
        downloadManager.deleteDownload(itemId)
        showNotice("已删除本地缓存")
    }

    internal fun setAudioBitrate(bitrate: dev.wearjelly.data.AudioBitrate) {
        repository.setBitrate(bitrate)
        showNotice("已切换音质: ${bitrate.displayName}")
    }

    internal fun playTrack(item: JellyfinItem) {
        if (repository.session.value == null && !offlineMode) return
        if (offlineMode && !downloadManager.isDownloaded(item.id)) {
            showNotice("该歌曲尚未缓存，连接服务器后可播放。")
            return
        }
        if (playback.state.value.current?.id == item.id) {
            navigate(AppScreen.Player)
            return
        }
        android.util.Log.d("WJ", "playTrack ${item.name} connected=${playback.state.value.connected}")
        viewModelScope.launch {
            if (!waitForConnected()) {
                showNotice("播放器连接失败，请重试")
                return@launch
            }
            playback.play(listOf(item))
            navigate(AppScreen.Player)
        }
    }

    private suspend fun waitForConnected(): Boolean {
        var waited = 0
        while (!playback.state.value.connected && waited < 3000) {
            kotlinx.coroutines.delay(100)
            waited += 100
        }
        return playback.state.value.connected
    }

    internal fun playLoaded(
        query: LibraryQuery,
        itemId: String? = null,
        randomizedStart: Boolean = false,
    ) {
        val items = mutableUi.value.libraries[query]?.items.orEmpty()
        if (query.kind != LibraryKind.SONGS || items.isEmpty()) return
        val index = when {
            itemId != null -> items.indexOfFirst { it.id == itemId }
            randomizedStart -> kotlin.random.Random.nextInt(items.size)
            else -> 0
        }
        if (index < 0) {
            showNotice("歌曲列表已变化，请返回列表重试。")
            return
        }
        if (playbackAction { playback.play(items, index) }) navigate(AppScreen.Player)
    }

    internal fun beginSelectionFromSwipe(itemId: String) {
        mutableUi.update { it.copy(selection = SelectionLogic.enterFromSwipe(itemId)) }
    }

    internal fun toggleSongSelection(itemId: String) {
        mutableUi.update { it.copy(selection = SelectionLogic.toggled(it.selection, itemId)) }
    }

    internal fun selectRangeFromSwipe(itemId: String, orderedIds: List<String>) {
        mutableUi.update { it.copy(selection = SelectionLogic.rangeSelected(it.selection, itemId, orderedIds)) }
    }

    internal fun exitSelectionMode() {
        mutableUi.update { it.copy(selection = SelectionLogic.cleared()) }
    }

    internal fun batchSelectedSongsToQueue(items: List<JellyfinItem>) {
        val selected = items.filter { it.id in mutableUi.value.selectedSongIds }
        if (selected.isEmpty()) {
            showNotice("请先选择歌曲")
            return
        }
        viewModelScope.launch {
            val result = playback.enqueueMany(selected)
            result.fold(
                onSuccess = {
                    showNotice("已加入 ${selected.size} 首歌曲")
                    exitSelectionMode()
                },
                onFailure = { showNotice(it.localizedMessage ?: "批量加入队列失败") },
            )
        }
    }

    internal fun batchSelectedSongsToCache(items: List<JellyfinItem>) {
        val selected = items.filter { it.id in mutableUi.value.selectedSongIds }
        if (selected.isEmpty()) {
            showNotice("请先选择歌曲")
            return
        }
        downloadManager.enqueue(selected)
        showNotice("已加入缓存队列 ${selected.size} 首")
        exitSelectionMode()
    }

    /** 下一首播放（单曲或批量选中项）：插到当前曲目之后，完成后退出多选。 */
    internal fun playItemsNext(items: List<JellyfinItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (!waitForConnected()) {
                showNotice("播放器连接失败，请重试")
                return@launch
            }
            playback.insertNext(items).fold(
                onSuccess = {
                    showNotice("已设为下一首播放 ${items.size} 首")
                    exitSelectionMode()
                },
                onFailure = { showNotice(it.localizedMessage ?: "下一首播放失败") },
            )
        }
    }

    /** 删除一批歌曲的本地缓存（不做多选状态处理，由调用方决定）。 */
    internal fun deleteCachedTracks(itemIds: Collection<String>) {
        val deleted = itemIds.count { id ->
            if (downloadManager.isDownloaded(id)) {
                downloadManager.deleteDownload(id)
                true
            } else {
                false
            }
        }
        showNotice(if (deleted > 0) "已删除 $deleted 首缓存" else "这些歌曲没有已缓存文件")
    }

    /** 从当前列表视图移除（仅本次会话，不改动服务端数据）。 */
    internal fun removeFromLibraryView(query: LibraryQuery, itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        updateLibrary(query) {
            val removed = items.count { it.id in itemIds }
            copy(
                items = items.filterNot { it.id in itemIds },
                total = (total - removed).coerceAtLeast(0),
                nextOffset = (nextOffset - removed).coerceAtLeast(0),
            )
        }
        showNotice("已从当前列表移除 ${itemIds.size} 首")
    }

    internal fun selectAllIn(query: LibraryQuery) {
        val items = mutableUi.value.libraries[query]?.items.orEmpty()
        selectAllItems(items)
    }

    internal fun selectAllItems(items: List<JellyfinItem>) {
        val orderedIds = items.map { it.id }
        mutableUi.update { it.copy(selection = SelectionLogic.allSelected(it.selection, orderedIds)) }
    }

    internal fun invertSelectionIn(query: LibraryQuery) {
        val items = mutableUi.value.libraries[query]?.items.orEmpty()
        invertSelectionItems(items)
    }

    internal fun invertSelectionItems(items: List<JellyfinItem>) {
        val orderedIds = items.map { it.id }
        mutableUi.update { it.copy(selection = SelectionLogic.inverted(it.selection, orderedIds)) }
    }

    internal fun removeSelectedFromQueue(items: List<JellyfinItem>) {
        val selectedIds = items.asSequence().map { it.id }.filter { it in mutableUi.value.selectedSongIds }.toSet()
        val indices = playback.state.value.queue.mapIndexedNotNull { index, track ->
            index.takeIf { track.item.id in selectedIds }
        }.asReversed()
        indices.forEach { index -> playbackAction { playback.removeQueueItem(index) } }
        if (indices.isNotEmpty()) showNotice("已从队列移除 ${indices.size} 首")
        exitSelectionMode()
    }

    /**
     * 列表级长按菜单动作统一分发（D1-D3 决策迁移原详情页顶部"更多"能力）。
     * 全部按完整列表语义解析（离线走快照），解析失败仅提示不崩溃。
     */
    internal fun onListMenuAction(menu: ListMenu, action: ListMenuAction) {
        when (action) {
            ListMenuAction.PLAY_ALL -> playFromListMenu(menu, shuffled = false)
            ListMenuAction.SHUFFLE_ALL -> playFromListMenu(menu, shuffled = true)
            ListMenuAction.QUEUE_ALL -> queueListMenu(menu)
            ListMenuAction.CACHE_ALL -> cacheListMenu(menu)
            ListMenuAction.DELETE_CACHES ->
                requestConfirmation(ConfirmAction.DELETE_LIST_CACHES, menu = menu)
        }
    }

    /** 解析菜单对应歌曲列表；null 表示已失败（错误 notice 已提示）或缺少作用域，调用方直接终止。 */
    private suspend fun resolveListMenuItems(menu: ListMenu): List<JellyfinItem>? = try {
        when (menu.source()) {
            ListMenuSource.HISTORY -> history.value.map { it.item }
            ListMenuSource.DOWNLOADS ->
                dev.wearjelly.data.PinyinSort.sort(downloadedSongs.value.map { it.item })
            ListMenuSource.PLAYLIST_UNION -> loadAllSongsOfAllPlaylists()
            ListMenuSource.SONGS_LIBRARY -> menu.scopeQuery()?.let { query -> loadAllSongs(query) }
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        showNotice(userMessage(error))
        null
    }

    private fun playFromListMenu(menu: ListMenu, shuffled: Boolean) {
        viewModelScope.launch {
            val items = resolveListMenuItems(menu) ?: return@launch
            if (items.isEmpty()) {
                showNotice("没有找到歌曲")
                return@launch
            }
            if (repository.session.value == null && !offlineMode) return@launch
            if (shuffled && !playback.state.value.shuffleEnabled) {
                playbackAction { playback.toggleShuffle() }
            }
            if (!waitForConnected()) {
                showNotice("播放器连接失败，请重试")
                return@launch
            }
            val index = if (shuffled) kotlin.random.Random.nextInt(items.size) else 0
            try {
                playback.play(items, index)
                navigate(AppScreen.Player)
            } catch (error: Exception) {
                showNotice(userMessage(error))
            }
        }
    }

    private fun queueListMenu(menu: ListMenu) {
        viewModelScope.launch {
            val items = resolveListMenuItems(menu) ?: return@launch
            if (items.isEmpty()) {
                showNotice("没有找到歌曲")
                return@launch
            }
            playback.enqueueMany(items).fold(
                onSuccess = {
                    goBack()
                    showNotice("已加入 ${items.size} 首歌曲")
                },
                onFailure = { showNotice(it.localizedMessage ?: "批量加入队列失败") },
            )
        }
    }

    private fun cacheListMenu(menu: ListMenu) {
        viewModelScope.launch {
            val items = resolveListMenuItems(menu) ?: return@launch
            if (items.isEmpty()) {
                showNotice("没有找到歌曲")
                return@launch
            }
            downloadManager.enqueue(items)
            goBack()
            showNotice("已加入缓存队列 ${items.size} 首")
        }
    }

    private suspend fun loadAllSongsOfAllPlaylists(): List<JellyfinItem> {
        val result = mutableListOf<JellyfinItem>()
        var offset = 0
        while (true) {
            val page = if (offlineMode) {
                offlineReader.page(LibraryKind.PLAYLISTS, null, null, null, offset, PAGE_SIZE)
            } else {
                repository.getItems(kind = LibraryKind.PLAYLISTS, startIndex = offset, limit = PAGE_SIZE)
            }
            for (playlist in page.items) {
                result += loadAllSongs(LibraryQuery(LibraryKind.SONGS, playlistId = playlist.id))
            }
            offset += page.items.size
            if (page.items.isEmpty() || offset >= page.totalRecordCount) break
        }
        return result.distinctBy { it.id }
    }

    private suspend fun loadAllSongs(query: LibraryQuery): List<JellyfinItem> {
        if (query.kind == LibraryKind.SONGS && mutableUi.value.libraries[query]?.endReached == true) {
            return mutableUi.value.libraries[query]?.items.orEmpty()
        }
        if (offlineMode) {
            val result = mutableListOf<JellyfinItem>()
            var offset = 0
            while (true) {
                val page = offlineReader.page(
                    query.kind,
                    query.playlistId,
                    query.parentId,
                    query.artistId,
                    offset,
                    PAGE_SIZE,
                )
                if (page.items.isEmpty()) break
                result += page.items
                offset += page.items.size
                if (offset >= page.totalRecordCount) break
            }
            return if (query.playlistId != null) result else result.distinctBy { it.id }
        }
        val result = mutableListOf<JellyfinItem>()
        var offset = 0
        val playlistId = query.playlistId
        while (true) {
            val page = if (playlistId != null) {
                repository.getPlaylistItems(playlistId, startIndex = offset, limit = PAGE_SIZE)
            } else {
                repository.getItems(
                    kind = LibraryKind.SONGS,
                    parentId = query.parentId,
                    artistId = query.artistId,
                    startIndex = offset,
                    limit = PAGE_SIZE,
                )
            }
            result += page.items
            offset += page.items.size
            if (page.items.isEmpty() || offset >= page.totalRecordCount) break
        }
        return if (playlistId != null) result else result.distinctBy { it.id }
    }

    internal fun adjustVolume(direction: Int) = playback.adjustVolume(direction)

    internal fun enqueue(item: JellyfinItem) {
        if (repository.session.value == null) return
        if (!playback.state.value.connected) {
            reconnectPlayback()
            showNotice("播放器正在连接，请稍后重试。")
            return
        }
        viewModelScope.launch {
            val result = playback.enqueue(item)
            result.fold(
                onSuccess = { showNotice("已加入播放队列") },
                onFailure = { error -> showNotice(error.localizedMessage ?: "加入播放队列失败") },
            )
        }
    }

    internal fun togglePlayPause() { playbackAction { playback.togglePlayPause() } }
    internal fun previous() { playbackAction { playback.previous() } }
    internal fun next() { playbackAction { playback.next() } }
    internal fun toggleShuffle() { playbackAction { playback.toggleShuffle() } }
    internal fun cycleRepeatMode() { playbackAction { playback.cycleRepeatMode() } }

    internal fun seekTo(positionMs: Long) {
        val state = playback.state.value
        val duration = state.effectiveDurationMs
        if (state.current == null || duration <= 0) return
        playbackAction { playback.seekTo(positionMs.coerceIn(0, duration)) }
    }

    internal fun seekRelative(deltaMs: Long) = seekTo(playback.state.value.positionMs + deltaMs)

    internal fun playQueueIndex(index: Int, expectedId: String) {
        if (!queueMatches(index, expectedId)) return
        if (index == playback.state.value.currentIndex && playback.state.value.current?.id == expectedId) {
            navigate(AppScreen.Player)
            return
        }
        if (playbackAction { playback.playQueueIndex(index) }) navigate(AppScreen.Player)
    }

    internal fun removeQueueItem(index: Int, expectedId: String) {
        if (queueMatches(index, expectedId)) playbackAction { playback.removeQueueItem(index) }
    }

    internal fun moveQueueItem(index: Int, to: Int, expectedId: String) {
        if (!queueMatches(index, expectedId) || to !in playback.state.value.queue.indices) return
        playbackAction { playback.moveQueueItem(index, to) }
    }

    private fun queueMatches(index: Int, expectedId: String): Boolean {
        if (playback.state.value.queue.getOrNull(index)?.item?.id == expectedId) return true
        showNotice("播放队列已变化，请重新选择歌曲。")
        return false
    }

    internal fun retryPlayback() {
        val state = playback.state.value
        if (!state.connected) {
            reconnectPlayback()
        } else if (state.currentIndex in state.queue.indices) {
            playbackAction { playback.playQueueIndex(state.currentIndex) }
        }
    }

    /** 从上次播放记忆的位置继续播放（服务启动时已预载队列，这里只需 play）。 */
    internal fun resumeLastPlayback() {
        if (repository.session.value == null) return
        if (!playback.state.value.connected) reconnectPlayback()
        viewModelScope.launch {
            if (!waitForConnected()) {
                showNotice("播放器连接失败，请重试")
                return@launch
            }
            if (!playback.resumePlayback()) showNotice("没有可恢复的播放")
        }
    }

    internal fun reconnectPlayback() {
        if (repository.session.value == null) return
        try {
            playback.connect()
        } catch (error: Exception) {
            showNotice(userMessage(error))
        }
    }

    private inline fun playbackAction(action: () -> Unit): Boolean {
        if (repository.session.value == null) return false
        if (!playback.state.value.connected) {
            reconnectPlayback()
            showNotice("播放器正在连接，请稍后重试。")
            return false
        }
        return try {
            action()
            true
        } catch (error: Exception) {
            showNotice(userMessage(error))
            false
        }
    }

    internal fun artworkUrl(item: JellyfinItem): String? {
        if (repository.session.value == null) return null
        return try {
            repository.imageUrl(item)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal fun loadLyrics(force: Boolean = false) {
        val itemId = playback.state.value.current?.id ?: return
        if (repository.session.value == null) return
        val old = mutableUi.value.lyrics
        if (!force && old.itemId == itemId && (old.loading || old.loaded || old.error != null)) return
        lyricsJob?.cancel()
        val requestGeneration = generation
        mutableUi.update { it.copy(lyrics = LyricsUi(itemId = itemId, loading = true)) }
        lyricsJob = viewModelScope.launch {
            try {
                when (val result = repository.lyrics(itemId)) {
                    is dev.wearjelly.data.LyricsResult.Found -> {
                        if (requestGeneration != generation || playback.state.value.current?.id != itemId) return@launch
                        mutableUi.update {
                            it.copy(lyrics = LyricsUi(itemId = itemId, loaded = true, lyrics = result.lyrics))
                        }
                    }
                    dev.wearjelly.data.LyricsResult.NotFound -> {
                        if (requestGeneration != generation || playback.state.value.current?.id != itemId) return@launch
                        mutableUi.update {
                            it.copy(lyrics = LyricsUi(itemId = itemId, loaded = true, notFound = true))
                        }
                    }
                    is dev.wearjelly.data.LyricsResult.Error -> {
                        if (requestGeneration == generation && playback.state.value.current?.id == itemId) {
                            mutableUi.update {
                                it.copy(lyrics = LyricsUi(itemId = itemId, error = result.message ?: "歌词加载失败"))
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == generation && playback.state.value.current?.id == itemId) {
                    mutableUi.update {
                        it.copy(lyrics = LyricsUi(itemId = itemId, error = userMessage(error)))
                    }
                }
            }
        }
    }

    // ---- 歌词偏好（REQ-LYRICS-MIN-003）----

    val lyricsOffsets: StateFlow<Map<String, Long>> = lyricsPrefs.offsets
    val lyricsFontSize: StateFlow<dev.wearjelly.data.LyricsFontSize> = lyricsPrefs.fontSize

    fun adjustLyricsOffset(deltaMs: Long) {
        val itemId = playback.state.value.current?.id ?: return
        lyricsPrefs.adjustOffset(itemId, deltaMs)
    }

    fun resetLyricsOffset() {
        val itemId = playback.state.value.current?.id ?: return
        lyricsPrefs.resetOffset(itemId)
    }

    fun setLyricsFontSize(size: dev.wearjelly.data.LyricsFontSize) {
        lyricsPrefs.setFontSize(size)
    }

    internal fun requestConfirmation(
        action: ConfirmAction,
        query: LibraryQuery? = null,
        menu: ListMenu? = null,
    ) = navigate(AppScreen.Confirm(action, query, menu))

    internal fun confirm(action: ConfirmAction, query: LibraryQuery? = null, menu: ListMenu? = null) {
        if (mutableUi.value.screen != AppScreen.Confirm(action, query, menu)) return
        when (action) {
            ConfirmAction.CLEAR_QUEUE -> {
                goBack()
                playbackAction { playback.stopAndClear() }
            }
            ConfirmAction.DELETE_LIST_CACHES -> {
                goBack()
                if (menu != null) {
                    // 菜单语义：删除该菜单完整列表的缓存（D3，全量解析，含离线快照）
                    viewModelScope.launch {
                        val items = resolveListMenuItems(menu) ?: return@launch
                        deleteCachedTracks(items.map { it.id })
                    }
                } else {
                    val ids = mutableUi.value.libraries[query]?.items?.map { it.id }.orEmpty()
                    deleteCachedTracks(ids)
                }
            }
            ConfirmAction.SWITCH_SERVER, ConfirmAction.LOGOUT -> {
                val account = mutableUi.value.account ?: return
                nextLogin = if (action == ConfirmAction.SWITCH_SERVER) LoginUi() else {
                    LoginUi(server = account.serverUrl, username = account.userName)
                }
                // Token revocation must still be attempted if a disconnected controller fails to stop.
                var stopFailed = false
                try {
                    playback.stopAndClear()
                } catch (_: Exception) {
                    stopFailed = true
                }
                try {
                    repository.logout()
                    if (repository.session.value != observedSession) acceptSession(repository.session.value)
                    if (stopFailed) showNotice("已退出登录；若仍有声音，请停止系统媒体播放。")
                } catch (error: Exception) {
                    nextLogin = null
                    goBack()
                    showNotice(userMessage(error))
                }
            }
        }
    }

    internal fun dismissNotice() { mutableUi.update { it.copy(notice = null) } }
    private fun showNotice(message: String) { mutableUi.update { it.copy(notice = message) } }

    private fun userMessage(error: Exception): String = when (error) {
        is JellyfinException -> when (error.statusCode) {
            401 -> "用户名或密码错误 (HTTP 401)"
            403 -> "无权访问此曲库 (HTTP 403)"
            404 -> "服务器端点不存在 (HTTP 404)"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "服务器错误 (HTTP ${error.statusCode ?: "未知"})"
        }
        is java.net.UnknownHostException -> "无法解析域名/主机: ${error.message ?: "找不到服务器"}"
        is java.net.SocketTimeoutException -> "连接服务器超时，请检查手表WiFi或IP"
        is java.net.ConnectException -> "无法连接到该端口: ${error.message ?: "连接被拒绝"}"
        is IOException -> "网络连接异常: ${error.localizedMessage ?: error.javaClass.simpleName}"
        else -> "错误: ${error.localizedMessage ?: error.javaClass.simpleName}"
    }

    private companion object {
        const val PAGE_SIZE = 60
        const val MAX_FETCH_ITEMS = 3000
    }
}

internal val LibraryKind.chineseTitle: String
    get() = when (this) {
        LibraryKind.ARTISTS -> "艺人"
        LibraryKind.ALBUMS -> "专辑"
        LibraryKind.SONGS -> "歌曲"
        LibraryKind.DOWNLOADS -> "已缓存音乐"
        LibraryKind.PLAYLISTS -> "播放列表"
    }

internal val PlaybackState.effectiveDurationMs: Long
    get() = durationMs.takeIf { it > 0 } ?: ((current?.runTimeTicks ?: 0L) / 10_000L).coerceAtLeast(0L)

internal fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600) {
        "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
