package dev.wearjelly.data.offline

import androidx.room.withTransaction
import dev.wearjelly.data.ItemPage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.JellyfinRepository
import dev.wearjelly.data.LibraryKind
import dev.wearjelly.data.ServerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 同步进度：供 UI 显示“同步中/已完成/失败”，失败时保留上一次快照。 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val playlistsDone: Int, val playlistsTotal: Int) : SyncState
    data class Completed(val result: SyncResult) : SyncState
    data class Failed(val message: String) : SyncState
}

data class SyncResult(
    val serverKey: String,
    val playlistCount: Int,
    val trackCount: Int,
    val completedAtMs: Long
)

/**
 * 只同步元数据快照，音频文件由下载队列按需处理。
 *
 * 一致性策略：先把所有分页全部拉取成功，再在单个 Room 事务里落库。任何一步抛异常都会回滚事务，
 * 因此同步失败时数据库仍保留上一次完整快照，`isOfflineAvailable` 也只在整个事务成功后才可见。
 *
 * 抓取动作以函数参数注入，便于在单元测试里替换成假的服务器分页结果。
 */
class OfflineLibrarySync(
    private val database: OfflineDatabase,
    private val loadSession: suspend () -> ServerSession?,
    private val fetchPlaylists: suspend (offset: Int, limit: Int) -> ItemPage,
    private val fetchPlaylistItems: suspend (playlistId: String, offset: Int, limit: Int) -> ItemPage,
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {
    constructor(
        repository: JellyfinRepository,
        database: OfflineDatabase,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ) : this(
        database = database,
        loadSession = { repository.session.value },
        fetchPlaylists = { offset, limit ->
            repository.getItems(LibraryKind.PLAYLISTS, startIndex = offset, limit = limit)
        },
        fetchPlaylistItems = { playlistId, offset, limit ->
            repository.getPlaylistItems(playlistId, startIndex = offset, limit = limit)
        },
        pageSize = pageSize
    )

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun syncAllPlaylists(): SyncResult = withContext(Dispatchers.IO) {
        try {
            runSync()
        } catch (t: Throwable) {
            _state.value = SyncState.Failed(t.message ?: "同步失败")
            throw t
        }
    }

    private suspend fun runSync(): SyncResult {
        val session = loadSession() ?: error("未登录，无法同步曲库")
        val serverKey = offlineServerKey(session.serverUrl, session.userId)

        val playlists = fetchAll { offset -> fetchPlaylists(offset, pageSize) }
        _state.value = SyncState.Running(0, playlists.size)

        val snapshots = ArrayList<Pair<JellyfinItem, List<JellyfinItem>>>(playlists.size)
        playlists.forEachIndexed { index, playlist ->
            snapshots += playlist to fetchAll { offset ->
                fetchPlaylistItems(playlist.id, offset, pageSize)
            }
            _state.value = SyncState.Running(index + 1, playlists.size)
        }

        val now = System.currentTimeMillis()
        var trackCount = 0
        database.withTransaction {
            database.profiles().upsert(
                ServerProfileEntity(
                    serverKey = serverKey,
                    userId = session.userId,
                    serverUrl = session.serverUrl,
                    userName = session.userName,
                    lastSuccessfulSyncMs = now,
                    isOfflineAvailable = true
                )
            )
            database.playlistTracks().deleteAll(serverKey)
            database.playlists().deleteAll(serverKey)
            snapshots.forEach { (playlist, items) ->
                database.playlists().upsert(
                    PlaylistEntity(
                        serverKey = serverKey,
                        itemId = playlist.id,
                        name = playlist.name,
                        imageTag = playlist.imageTags["Primary"],
                        lastSyncedMs = now
                    )
                )
                indexImage(serverKey, playlist.id, playlist.imageTags["Primary"])
                val trackIds = items.map { item ->
                    database.tracks().upsert(item.toTrackEntity(serverKey))
                    upsertAlbumAndArtists(serverKey, item)
                    indexImages(serverKey, item)
                    item.id
                }
                database.playlistTracks().replaceSnapshot(serverKey, playlist.id, trackIds)
                trackCount += trackIds.size
            }
        }

        val result = SyncResult(serverKey, playlists.size, trackCount, now)
        _state.value = SyncState.Completed(result)
        return result
    }

    /** 逐页取完：空页、已取满总数、或不足一页即停。 */
    private suspend fun fetchAll(fetch: suspend (Int) -> ItemPage): List<JellyfinItem> {
        val all = ArrayList<JellyfinItem>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            all += page.items
            if (page.items.isEmpty()) break
            if (page.totalRecordCount in 1..all.size) break
            if (page.items.size < pageSize) break
            offset += page.items.size
        }
        return all
    }

    private suspend fun upsertAlbumAndArtists(serverKey: String, item: JellyfinItem) {
        item.albumId?.let { albumId ->
            database.albums().upsert(
                listOf(
                    AlbumEntity(
                        serverKey = serverKey,
                        itemId = albumId,
                        name = item.album ?: "未知专辑",
                        albumArtist = item.albumArtist
                    )
                )
            )
        }
        // 艺人表索引曲目署名艺人（Artists 字段）；专辑艺人保存在 AlbumEntity.albumArtist，
        // 仅当曲目没有署名艺人时才回退索引，与 artistText 的展示口径保持一致。
        val names = item.artists.distinct().ifEmpty { listOfNotNull(item.albumArtist) }
        if (names.isNotEmpty()) {
            database.artists().upsert(
                names.map { name ->
                    ArtistEntity(serverKey = serverKey, itemId = artistKey(name), name = name)
                }
            )
        }
    }

    private suspend fun indexImages(serverKey: String, item: JellyfinItem) {
        val images = item.imageTags.map { (type, tag) ->
            ImageEntity(
                serverKey = serverKey,
                imageKey = "${item.id}:$type",
                itemId = item.id,
                imageType = type,
                tag = tag
            )
        }
        if (images.isNotEmpty()) database.images().upsert(images)
        if (!item.albumId.isNullOrBlank()) indexImage(serverKey, item.albumId, item.albumPrimaryImageTag)
    }

    private suspend fun indexImage(serverKey: String, itemId: String, tag: String?) {
        if (tag.isNullOrBlank()) return
        database.images().upsert(
            listOf(
                ImageEntity(
                    serverKey = serverKey,
                    imageKey = "$itemId:Primary",
                    itemId = itemId,
                    imageType = "Primary",
                    tag = tag
                )
            )
        )
    }

    private fun artistKey(name: String): String = "name:$name"

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

/**
 * 应用级同步触发器：会话可用（登录成功或冷启动恢复会话）后在后台异步触发同步，不阻塞 UI。
 *
 * 失败时 [OfflineLibrarySync.syncAllPlaylists] 已把状态置为 [SyncState.Failed] 并保留旧快照，
 * 此处吞掉异常（避免协程崩溃），用户可通过 [retry] 手动重试；Mutex 保证同一时刻只有一个同步在跑。
 */
class OfflineSyncCoordinator(
    repository: JellyfinRepository,
    private val sync: OfflineLibrarySync,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    init {
        scope.launch {
            // session 是 StateFlow：等值不重复发射；登出产生的 null 被过滤，重新登录会再次触发。
            repository.session.filterNotNull().collect {
                runSync()
            }
        }
    }

    /** 同步进度/结果/失败状态，供 UI 展示进度与错误并提供重试入口。 */
    val state: StateFlow<SyncState> = sync.state

    /** 手动重试（如用户点击“重试同步”），同样在后台执行。 */
    fun retry() {
        scope.launch { runSync() }
    }

    private suspend fun runSync() {
        try {
            mutex.withLock { sync.syncAllPlaylists() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 失败详情已写入 SyncState.Failed，此处只避免协程被异常终止
        }
    }
}