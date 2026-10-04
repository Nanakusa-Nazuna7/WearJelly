package dev.wearjelly.data.offline

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.wearjelly.data.JellyfinException
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.JellyfinRepository
import dev.wearjelly.data.ServerSession
import dev.wearjelly.data.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 2 的真实 HTTP 验证：走 JellyfinRepository 的登录、`/Users/{id}/Items?IncludeItemTypes=Playlist`
 * 播放列表发现与 `/Playlists/{id}/Items` 子项分页端点，覆盖分页、顺序、重复同步幂等、
 * 陈旧关系清理、缓存文件保留与失败保留旧快照。
 *
 * 服务器地址带 `/jellyfin` 前缀，同时验证反向代理路径前缀在同步全程不丢失。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineSyncRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var database: OfflineDatabase
    private lateinit var repository: JellyfinRepository
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val userId = "user-1"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(), OfflineDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = JellyfinRepository(OkHttpClient(), json, SessionStore(ApplicationProvider.getApplicationContext(), json))
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    // ---- 测试夹具 ----

    private fun login(): ServerSession = runBlocking {
        enqueueAll(jsonResponse("""{"AccessToken":"token-1","User":{"Id":"$userId","Name":"tester"}}"""))
        repository.login(server.url("/jellyfin").toString(), "tester", "pw")
    }

    private fun sync(pageSize: Int = 100) = OfflineLibrarySync(
        database = database,
        loadSession = { repository.session.value },
        fetchPlaylists = { offset, limit -> repository.getItems(dev.wearjelly.data.LibraryKind.PLAYLISTS, startIndex = offset, limit = limit) },
        fetchPlaylistItems = { playlistId, offset, limit -> repository.getPlaylistItems(playlistId, startIndex = offset, limit = limit) },
        fetchLyrics = { dev.wearjelly.data.LyricsResult.NotFound },
        fetchAllTracks = { _, _ -> dev.wearjelly.data.ItemPage(emptyList(), 0) },
        pageSize = pageSize
    )

    private fun key(): String {
        val session = repository.session.value ?: error("未登录")
        return offlineServerKey(session.serverUrl, session.userId)
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    /** MockWebServer 4.x 的 enqueue 只收单个响应；按同步的串行请求顺序批量入队。 */
    private fun enqueueAll(vararg responses: MockResponse) = responses.forEach(server::enqueue)

    private fun page(total: Int, vararg items: JellyfinItem): MockResponse =
        jsonResponse("""{"Items":${json.encodeToString(items.toList())},"TotalRecordCount":$total}""")

    private fun playlistItem(id: String) = JellyfinItem(
        id = id,
        name = "列表 $id",
        type = "Playlist",
        imageTags = mapOf("Primary" to "pl-tag-$id")
    )

    private fun trackItem(id: String) = JellyfinItem(
        id = id,
        name = "Song $id",
        type = "Audio",
        artists = listOf("Artist $id"),
        albumArtist = "Album Artist",
        album = "Record",
        albumId = "album-$id",
        imageTags = mapOf("Primary" to "tag-$id")
    )

    /** 按入队顺序取回已完成的请求（同步是严格串行的，同步返回时请求均已记录）。 */
    private fun taken(count: Int): List<RecordedRequest> = (0 until count).map { server.takeRequest() }

    private fun queryParam(path: String, name: String): String? =
        path.substringAfter("$name=", "").substringBefore("&").ifEmpty { null }

    // ---- 用例 ----

    @Test
    fun syncDiscoversPlaylistsAcrossPagesKeepingProxyPrefixAndAuthHeader() = runBlocking {
        login()
        // 播放列表共 3 个、分页大小 2：列表端点两次请求，随后每个播放列表一次子项请求
        enqueueAll(
            page(3, playlistItem("p1"), playlistItem("p2")),
            page(3, playlistItem("p3")),
            page(0),
            page(0),
            page(0),
        )

        val result = sync(pageSize = 2).syncAllPlaylists()

        assertEquals(3, result.playlistCount)
        assertEquals(0, result.trackCount)
        assertEquals(listOf("p1", "p2", "p3"), database.playlists().all(key()).map { it.itemId })
        assertNotNull(database.images().get(key(), "p1:Primary"))

        val requests = taken(6)
        val auth = requests[0]
        assertEquals("/jellyfin/Users/AuthenticateByName", auth.path)

        val firstPage = requests[1]
        val secondPage = requests[2]
        assertTrue(firstPage.path!!.startsWith("/jellyfin/Users/$userId/Items"))
        assertTrue(firstPage.path!!.contains("IncludeItemTypes=Playlist"))
        assertEquals("0", queryParam(firstPage.path!!, "StartIndex"))
        assertEquals("2", queryParam(secondPage.path!!, "StartIndex"))
        // 鉴权头沿用现有 X-Emby-Authorization 约定且带会话 token
        assertTrue(firstPage.getHeader("X-Emby-Authorization")!!.contains("""Token="token-1""""))

        val itemRequests = requests.drop(3)
        assertEquals(listOf("p1", "p2", "p3"), itemRequests.map { playlistIdOf(it.path!!) })
        itemRequests.forEach { assertTrue(it.path!!.startsWith("/jellyfin/Playlists/")) }
    }

    @Test
    fun syncFetchesEveryPlaylistChildPageAndKeepsServerOrder() = runBlocking {
        login()
        enqueueAll(
            page(1, playlistItem("p1")),
            page(5, trackItem("t0"), trackItem("t1")),
            page(5, trackItem("t2"), trackItem("t3")),
            page(5, trackItem("t4")),
        )

        val result = sync(pageSize = 2).syncAllPlaylists()

        assertEquals(5, result.trackCount)
        val relations = database.playlistTracks().tracksInOrder(key(), "p1")
        assertEquals(listOf("t0", "t1", "t2", "t3", "t4"), relations.map { it.trackId })
        assertEquals(listOf(0, 1, 2, 3, 4), relations.map { it.orderIndex })

        val itemRequests = taken(5).filter { it.path!!.startsWith("/jellyfin/Playlists/") }
        assertEquals(listOf("0", "2", "4"), itemRequests.map { queryParam(it.path!!, "StartIndex") })
    }

    @Test
    fun repeatedSyncOverHttpIsIdempotent() = runBlocking {
        login()
        enqueueAll(page(1, playlistItem("p1")), page(2, trackItem("t1"), trackItem("t2")))
        val first = sync().syncAllPlaylists()

        enqueueAll(page(1, playlistItem("p1")), page(2, trackItem("t1"), trackItem("t2")))
        val secondSync = sync()
        val second = secondSync.syncAllPlaylists()

        assertEquals(first.playlistCount, second.playlistCount)
        assertEquals(first.trackCount, second.trackCount)
        assertEquals(1, database.playlists().all(key()).size)
        assertEquals(2, database.tracks().count(key()))
        val relations = database.playlistTracks().tracksInOrder(key(), "p1")
        assertEquals(listOf("t1", "t2"), relations.map { it.trackId })
        assertEquals(listOf(0, 1), relations.map { it.orderIndex })
        assertEquals(SyncState.Completed(second), secondSync.state.value)
    }

    @Test
    fun resyncRemovesStaleRelationsButKeepsCachedTrackFiles() = runBlocking {
        login()
        enqueueAll(page(1, playlistItem("p1")), page(3, trackItem("t1"), trackItem("t2"), trackItem("t3")))
        sync().syncAllPlaylists()

        // 模拟 t1 已被下载缓存：走下载完成的媒体字段更新通道，随后元数据刷新必须保留本地文件
        database.tracks().updateMediaState(key(), "t1", "audio/t1.flac", null, 123L, "原音质")

        // 第二次同步：t3 从服务器端消失，t1/t2 仍在
        enqueueAll(page(1, playlistItem("p1")), page(2, trackItem("t1"), trackItem("t2")))
        sync().syncAllPlaylists()

        val relations = database.playlistTracks().tracksInOrder(key(), "p1")
        assertEquals(listOf("t1", "t2"), relations.map { it.trackId })
        assertEquals("audio/t1.flac", database.tracks().get(key(), "t1")?.localAudioPath)
        // 陈旧关系被清理，但元数据行（含缓存文件引用）不被删除
        assertEquals(3, database.tracks().count(key()))
    }

    @Test
    fun httpFailureDuringPaginationKeepsPreviousSnapshot() = runBlocking {
        login()
        enqueueAll(page(1, playlistItem("p1")), page(1, trackItem("t1")))
        val first = sync().syncAllPlaylists()
        val profileBefore = database.profiles().get(key(), userId)
        val relationsBefore = database.playlistTracks().tracksInOrder(key(), "p1")

        // 第二个同步发现新播放列表 p2，但其子项请求返回 500
        enqueueAll(
            page(2, playlistItem("p1"), playlistItem("p2")),
            page(1, trackItem("t1")),
            MockResponse().setResponseCode(500),
        )
        val second = sync()
        val failure = runCatching { second.syncAllPlaylists() }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is JellyfinException)
        assertTrue(second.state.value is SyncState.Failed)

        // 旧快照、成功时间戳与关系全部保持第一次同步后的状态
        assertEquals(listOf("p1"), database.playlists().all(key()).map { it.itemId })
        assertEquals(relationsBefore, database.playlistTracks().tracksInOrder(key(), "p1"))
        assertEquals(profileBefore, database.profiles().get(key(), userId))
        assertEquals(first.completedAtMs, database.profiles().get(key(), userId)?.lastSuccessfulSyncMs)
        assertTrue(database.profiles().get(key(), userId)!!.isOfflineAvailable)
    }

    private fun playlistIdOf(path: String): String =
        path.substringAfter("/Playlists/").substringBefore("/")
}
