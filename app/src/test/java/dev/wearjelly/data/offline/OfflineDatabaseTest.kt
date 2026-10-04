package dev.wearjelly.data.offline

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.content.Context
import dev.wearjelly.data.DownloadedSong
import dev.wearjelly.data.JellyfinItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineDatabaseTest {
    private lateinit var database: OfflineDatabase
    private val serverKey = "https://jellyfin.example/music|user-1"

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

    @Test
    fun trackRoundTripPreservesCompleteJellyfinMetadata() = runBlocking {
        val item = JellyfinItem(
            id = "track-1", name = "Song", type = "Audio", artists = listOf("A", "B"),
            albumArtist = "Album Artist", album = "Record", albumId = "album-1",
            runTimeTicks = 123456789L, container = "flac", imageTags = mapOf("Primary" to "tag-1"),
            albumPrimaryImageTag = "album-tag", indexNumber = 4, parentIndexNumber = 2
        )
        database.tracks().upsert(item.toTrackEntity(serverKey))

        assertEquals(item, database.tracks().get(serverKey, item.id)?.toJellyfinItem())
    }

    @Test
    fun replacingPlaylistSnapshotPreservesOrderAndIsIdempotent() = runBlocking {
        val playlist = PlaylistEntity(serverKey, "playlist-1", "Favorites", null, null)
        val first = JellyfinItem(id = "track-1", name = "First")
        val second = JellyfinItem(id = "track-2", name = "Second")
        database.playlists().upsert(playlist)
        database.tracks().upsert(listOf(first.toTrackEntity(serverKey), second.toTrackEntity(serverKey)))

        database.withTransaction {
            database.playlistTracks().replaceSnapshot(serverKey, playlist.itemId, listOf(second.id, first.id))
        }
        database.withTransaction {
            database.playlistTracks().replaceSnapshot(serverKey, playlist.itemId, listOf(second.id, first.id))
        }

        assertEquals(listOf(second.id, first.id), database.playlistTracks().tracksInOrder(serverKey, playlist.itemId).map { it.trackId })
    }

    @Test
    fun replacingPlaylistSnapshotDeletesStaleRelationsWithoutDeletingCachedTrack() = runBlocking {
        val playlist = PlaylistEntity(serverKey, "playlist-1", "Favorites", null, null)
        val stale = JellyfinItem(id = "stale", name = "Cached")
        val current = JellyfinItem(id = "current", name = "Current")
        database.playlists().upsert(playlist)
        database.tracks().upsert(listOf(stale.toTrackEntity(serverKey).copy(localAudioPath = "audio/stale.flac"), current.toTrackEntity(serverKey)))
        database.playlistTracks().replaceSnapshot(serverKey, playlist.itemId, listOf(stale.id, current.id))

        database.withTransaction { database.playlistTracks().replaceSnapshot(serverKey, playlist.itemId, listOf(current.id)) }

        assertEquals(listOf(current.id), database.playlistTracks().tracksInOrder(serverKey, playlist.itemId).map { it.trackId })
        assertEquals("audio/stale.flac", database.tracks().get(serverKey, stale.id)?.localAudioPath)
    }

    @Test
    fun repeatedMetadataUpsertDoesNotDuplicateTrack() = runBlocking {
        val item = JellyfinItem(id = "track", name = "Original", artists = listOf("Artist"))
        val track = item.toTrackEntity(serverKey)
        database.tracks().upsert(track)
        database.tracks().upsert(track.copy(metadataJson = Json.encodeToString(item.copy(name = "Updated"))))

        assertEquals("Updated", database.tracks().get(serverKey, item.id)?.toJellyfinItem()?.name)
        assertEquals(1, database.tracks().count(serverKey))
    }

    @Test
    fun metadataUpsertPreservesCachedMediaFields() = runBlocking {
        val item = JellyfinItem(id = "cached", name = "Before")
        database.tracks().upsert(item.toTrackEntity(serverKey).copy(localAudioPath = "audio/cached.flac", qualityLabel = "Original"))

        database.tracks().upsert(item.copy(name = "After").toTrackEntity(serverKey))

        val stored = database.tracks().get(serverKey, item.id)!!
        assertEquals("After", stored.toJellyfinItem().name)
        assertEquals("audio/cached.flac", stored.localAudioPath)
        assertEquals("Original", stored.qualityLabel)
    }

    @Test
    fun legacyDownloadIndexDecodesDownloadedSongsWithoutLosingMetadata() {
        val item = JellyfinItem(id = "legacy", name = "Legacy", artists = listOf("Artist"), album = "Album", container = "flac")
        val source = File.createTempFile("downloads-index", ".json")
        try {
            source.writeText(Json.encodeToString(listOf(DownloadedSong(item, "audio/legacy.flac", null, 99L, "原音质"))))
            val imported = LegacyDownloadIndexImporter(Json).read(source)

            assertEquals(item, imported.single().item)
            assertEquals("audio/legacy.flac", imported.single().localFilePath)
            assertEquals("原音质", imported.single().qualityLabel)
        } finally {
            source.delete()
        }
    }
}
