package dev.wearjelly.data.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.wearjelly.data.JellyfinItem

@Entity(primaryKeys = ["serverKey", "userId"])
data class ServerProfileEntity(
    val serverKey: String,
    val userId: String,
    val serverUrl: String,
    val userName: String,
    val lastSuccessfulSyncMs: Long? = null,
    val isOfflineAvailable: Boolean = false
)

@Entity(primaryKeys = ["serverKey", "itemId"], indices = [Index("albumId")])
data class TrackEntity(
    val serverKey: String,
    val itemId: String,
    val name: String,
    val type: String?,
    val albumId: String?,
    val album: String?,
    val artistText: String,
    val durationTicks: Long?,
    val container: String?,
    val metadataJson: String,
    val composerText: String = "",
    val lyricistText: String = "",
    val genreJson: String = "[]",
    val overview: String? = null,
    val productionYear: Int? = null,
    val premiereDate: String? = null,
    val sortName: String? = null,
    val bitrate: Int? = null,
    val mediaSourceJson: String = "[]",
    val localAudioPath: String? = null,
    val localCoverPath: String? = null,
    val downloadedTimeMs: Long? = null,
    val qualityLabel: String? = null
)

@Entity(primaryKeys = ["serverKey", "itemId", "language"])
data class LyricsEntity(
    val serverKey: String,
    val itemId: String,
    val language: String = "default",
    val lyricsJson: String = "{}",
    val isSynchronized: Boolean = false,
    val state: String = "UNKNOWN",
    val fetchedAtMs: Long? = null,
    val lastError: String? = null
)

@Entity(primaryKeys = ["serverKey", "itemId"])
data class AlbumEntity(
    val serverKey: String,
    val itemId: String,
    val name: String,
    val albumArtist: String? = null,
    val metadataJson: String = "{}"
)

@Entity(primaryKeys = ["serverKey", "itemId"])
data class ArtistEntity(
    val serverKey: String,
    val itemId: String,
    val name: String,
    val metadataJson: String = "{}"
)

@Entity(primaryKeys = ["serverKey", "itemId"])
data class PlaylistEntity(
    val serverKey: String,
    val itemId: String,
    val name: String,
    val imageTag: String? = null,
    val lastSyncedMs: Long? = null
)

@Entity(
    indices = [Index(value = ["serverKey", "playlistId", "orderIndex"], unique = true), Index("trackId")]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverKey: String,
    val playlistId: String,
    val trackId: String,
    val orderIndex: Int
)

@Entity(primaryKeys = ["serverKey", "imageKey"])
data class ImageEntity(
    val serverKey: String,
    val imageKey: String,
    val itemId: String,
    val imageType: String,
    val tag: String,
    val localPath: String? = null,
    val downloadState: String = "METADATA"
)

@Entity(primaryKeys = ["serverKey", "indexName"])
data class LegacyImportStateEntity(
    val serverKey: String,
    val indexName: String,
    val importedAtMs: Long
)

@Entity(primaryKeys = ["serverKey", "taskId"], indices = [Index("status")])
data class DownloadTaskEntity(
    val serverKey: String,
    val taskId: String,
    val itemId: String,
    val itemJson: String,
    val status: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val qualityLabel: String? = null,
    val updatedAtMs: Long = 0
)

data class TrackWithRelations(
    val track: TrackEntity,
    val album: AlbumEntity? = null,
    val artists: List<ArtistEntity> = emptyList()
)
