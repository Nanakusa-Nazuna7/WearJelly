package dev.wearjelly.data.offline

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.wearjelly.data.ItemPage
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.ServerSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineLibrarySyncTest {
    private lateinit var database: OfflineDatabase
    private val session = ServerSession(
        serverUrl = "https://jellyfin.example/music",
        userId = "user-1",
        userName = "tester",
        accessToken = "token"
    )
    private val serverKey = offlineServerKey(session.serverUrl, session.userId)

    @Before
    fun setUp() {
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), OfflineDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun track(id: String, name: String = id) = JellyfinItem(
        id = id,
        name = name,
        type = "Audio",
        artists = listOf("Artist $id"),
        albumArtist = "Album Artist",
        album = "Record",
        albumId = "album-$id",
        imageTags = mapOf("Primary" to "tag-$id"),
        albumPrimaryImageTag = "album-tag-$id"
    )

    private fun sync(
        playlists: List<JellyfinItem>,
        pageSize: Int = 100,
        items: suspend (String, Int, Int) -> ItemPage
    ) = OfflineLibrarySync(
        database = database,
        loadSession = { session },
        fetchPlaylists = { offset, limit -> ItemPage(playlists.drop(offset).take(limit), playlists.size) },
        fetchPlaylistItems = { playlistId, offset, limit -> items(playlistId, offset, limit) },
        pageSize = pageSize
    )

    @Test
    fun syncIndexesPlaylistsTracksRelationsAndImages() = runBlocking {
        val playlist = JellyfinItem(id = "playlist-1", name = "收藏", imageTags = mapOf("Primary" to "pl-tag"))
        val first = track("track-1")
        val second = track("track-2")

        val result = sync(listOf(playlist)) { _, _, _ -> ItemPage(listOf(first, second), 2) }.syncAllPlaylists()

        assertEquals(serverKey, result.serverKey)
        assertEquals(1, result.playlistCount)
        assertEquals(2, result.trackCount)

        val stored = database.playlists().all(serverKey)
        assertEquals(listOf("playlist-1"), stored.map { it.itemId })
        assertEquals("pl-tag", stored.single().imageTag)

        assertEquals(
            listOf("track-1", "track-2"),
            database.playlistTracks().tracksInOrder(serverKey, "playlist-1").map { it.trackId }
        )
        assertEquals(2, database.tracks().count(serverKey))
        assertEquals(listOf("track-1", "track-2"), database.tracks().all(serverKey).map { it.itemId }.sorted())
        assertEquals(2, database.albums().all(serverKey).size)
        assertEquals(2, database.artists().all(serverKey).size)
        assertNotNull(database.images().get(serverKey, "playlist-1:Primary"))
        assertNotNull(database.images().get(serverKey, "track-1:Primary"))
        assertNotNull(database.images().get(serverKey, "album-track-1:Primary"))

        val profile = database.profiles().get(serverKey, session.userId)
        assertNotNull(profile)
        assertTrue(profile!!.isOfflineAvailable)
        assertEquals(result.completedAtMs, profile.lastSuccessfulSyncMs)
    }

    @Test
    fun pagingReadsEveryPageAndKeepsPlaylistOrder() = runBlocking {
        val playlist = JellyfinItem(id = "playlist-1", name = "长列表")
        val all = (0 until 5).map { track("track-$it") }
        var calls = 0

        val result = sync(listOf(playlist), pageSize = 2) { _, offset, limit ->
            calls++
            ItemPage(all.drop(offset).take(limit), all.size)
        }.syncAllPlaylists()

        assertEquals(5, result.trackCount)
        assertEquals(3, calls)
        assertEquals(
            (0 until 5).map { "track-$it" },
            database.playlistTracks().tracksInOrder(serverKey, "playlist-1").map { it.trackId }
        )
    }

    @Test
    fun repeatedSyncReplacesSnapshotWithoutDuplicatingRelations() = runBlocking {
        val playlist = JellyfinItem(id = "playlist-1", name = "收藏")
        sync(listOf(playlist)) { _, _, _ -> ItemPage(listOf(track("track-1"), track("track-2")), 2) }.syncAllPlaylists()
        sync(listOf(playlist)) { _, _, _ -> ItemPage(listOf(track("track-2")), 1) }.syncAllPlaylists()

        val relations = database.playlistTracks().tracksInOrder(serverKey, "playlist-1")
        assertEquals(listOf("track-2"), relations.map { it.trackId })
        assertEquals(listOf(0), relations.map { it.orderIndex })
        assertEquals(2, database.tracks().count(serverKey))
    }

    @Test
    fun failedSyncRollsBackAndKeepsPreviousSnapshot() = runBlocking {
        val playlist = JellyfinItem(id = "playlist-1", name = "收藏")
        val firstSync = sync(listOf(playlist)) { _, _, _ -> ItemPage(listOf(track("track-1")), 1) }.syncAllPlaylists()
        val profileBefore = database.profiles().get(serverKey, session.userId)

        val failing = sync(listOf(playlist, JellyfinItem(id = "playlist-2", name = "第二个"))) { playlistId, _, _ ->
            if (playlistId == "playlist-2") throw IllegalStateException("服务器断开")
            ItemPage(listOf(track("track-1")), 1)
        }
        val failure = runCatching { failing.syncAllPlaylists() }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failing.state.value is SyncState.Failed)
        assertEquals("服务器断开", (failing.state.value as SyncState.Failed).message)

        // 旧快照与成功时间戳都保持不变
        assertEquals(listOf("playlist-1"), database.playlists().all(serverKey).map { it.itemId })
        assertEquals(
            listOf("track-1"),
            database.playlistTracks().tracksInOrder(serverKey, "playlist-1").map { it.trackId }
        )
        assertEquals(profileBefore, database.profiles().get(serverKey, session.userId))
        assertEquals(firstSync.completedAtMs, database.profiles().get(serverKey, session.userId)?.lastSuccessfulSyncMs)
    }

    @Test
    fun syncWithoutSessionFailsFastWithoutTouchingDatabase() = runBlocking {
        val offline = OfflineLibrarySync(
            database = database,
            loadSession = { null },
            fetchPlaylists = { _, _ -> error("不应被调用") },
            fetchPlaylistItems = { _, _, _ -> error("不应被调用") }
        )

        val failure = runCatching { offline.syncAllPlaylists() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, database.tracks().count(serverKey))
        assertTrue(database.profiles().lastUsable() == null)
    }
}