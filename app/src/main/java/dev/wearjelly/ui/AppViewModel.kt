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
)

internal sealed interface AppScreen {
    data object Login : AppScreen
    data object Home : AppScreen
    data class Library(val query: LibraryQuery, val title: String) : AppScreen
    data class Track(val item: JellyfinItem, val source: LibraryQuery) : AppScreen
    data object Player : AppScreen
    data object Queue : AppScreen
    data object Lyrics : AppScreen
    data object Settings : AppScreen
    data class Confirm(val action: ConfirmAction) : AppScreen
}

internal enum class ConfirmAction { CLEAR_QUEUE, SWITCH_SERVER, LOGOUT }

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
)

internal data class AppUiState(
    val account: AccountUi? = null,
    val login: LoginUi = LoginUi(),
    val backStack: List<AppScreen> = listOf(AppScreen.Login),
    val libraries: Map<LibraryQuery, LibraryUi> = emptyMap(),
    val lyrics: LyricsUi = LyricsUi(),
    val notice: String? = null,
    val sessionGeneration: Long = 0,
) {
    val screen: AppScreen get() = backStack.last()
}

class AppViewModel(
    private val repository: JellyfinRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {
    val session: StateFlow<ServerSession?> = repository.session
    val playbackState: StateFlow<PlaybackState> = playback.state
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
            LibraryKind.ARTISTS -> navigate(
                AppScreen.Library(LibraryQuery(LibraryKind.ALBUMS, artistId = item.id), item.name),
            )
            LibraryKind.ALBUMS -> navigate(
                AppScreen.Library(LibraryQuery(LibraryKind.SONGS, parentId = item.id), item.name),
            )
            LibraryKind.SONGS -> navigate(AppScreen.Track(item, query))
        }
    }

    internal fun openArtistSongs(screen: AppScreen.Library) {
        val artistId = screen.query.artistId ?: return
        navigate(AppScreen.Library(LibraryQuery(LibraryKind.SONGS, artistId = artistId), screen.title))
    }

    internal fun navigate(screen: AppScreen) {
        if (repository.session.value == null && screen != AppScreen.Login) return
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
        if (repository.session.value == null) return
        val old = mutableUi.value.libraries[query] ?: LibraryUi()
        if (old.loading && !fromStart) return
        libraryJobs.remove(query)?.cancel()
        val requestGeneration = generation
        val offset = if (fromStart) 0 else old.nextOffset
        updateLibrary(query) { copy(loading = true, error = null, retryFromStart = fromStart) }
        libraryJobs[query] = viewModelScope.launch {
            try {
                val page = repository.getItems(
                    kind = query.kind,
                    parentId = query.parentId,
                    artistId = query.artistId,
                    startIndex = offset,
                    limit = PAGE_SIZE,
                )
                if (requestGeneration != generation) return@launch
                val combined = ((if (fromStart) emptyList() else old.items) + page.items).distinctBy { it.id }
                val next = offset + page.items.size
                updateLibrary(query) {
                    copy(
                        items = combined,
                        total = maxOf(page.totalRecordCount, combined.size),
                        nextOffset = next,
                        loading = false,
                        loaded = true,
                        endReached = page.items.isEmpty() || next >= page.totalRecordCount,
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

    internal fun playTrack(item: JellyfinItem) {
        if (playbackAction { playback.play(listOf(item)) }) navigate(AppScreen.Player)
    }

    internal fun playLoaded(query: LibraryQuery, itemId: String? = null) {
        val items = mutableUi.value.libraries[query]?.items.orEmpty()
        if (query.kind != LibraryKind.SONGS || items.isEmpty()) return
        val index = if (itemId == null) 0 else items.indexOfFirst { it.id == itemId }
        if (index < 0) {
            showNotice("歌曲列表已变化，请返回列表重试。")
            return
        }
        if (playbackAction { playback.play(items, index) }) navigate(AppScreen.Player)
    }

    internal fun enqueue(item: JellyfinItem) {
        if (playbackAction { playback.enqueue(item) }) showNotice("已加入播放队列")
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
                val result = repository.lyrics(itemId)
                if (requestGeneration != generation || playback.state.value.current?.id != itemId) return@launch
                mutableUi.update {
                    it.copy(lyrics = LyricsUi(itemId = itemId, loaded = true, lyrics = result))
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

    internal fun requestConfirmation(action: ConfirmAction) = navigate(AppScreen.Confirm(action))

    internal fun confirm(action: ConfirmAction) {
        if (mutableUi.value.screen != AppScreen.Confirm(action)) return
        when (action) {
            ConfirmAction.CLEAR_QUEUE -> {
                goBack()
                playbackAction { playback.stopAndClear() }
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
            401 -> "登录已失效或用户名、密码不正确，请重新登录。"
            403 -> "当前账户没有访问权限，请检查服务器设置。"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "服务器请求失败，请重试。"
        }
        is IOException -> "连接失败，请检查手表网络、服务器地址和证书后重试。"
        else -> "操作未完成，请稍后重试。"
    }

    private companion object { const val PAGE_SIZE = 60 }
}

internal val LibraryKind.chineseTitle: String
    get() = when (this) {
        LibraryKind.ARTISTS -> "艺人"
        LibraryKind.ALBUMS -> "专辑"
        LibraryKind.SONGS -> "歌曲"
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
