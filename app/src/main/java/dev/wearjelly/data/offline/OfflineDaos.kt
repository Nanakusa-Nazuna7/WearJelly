package dev.wearjelly.data.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.withTransaction
import dev.wearjelly.data.DownloadedSong
import dev.wearjelly.data.JellyfinItem
import dev.wearjelly.data.SongLyrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(profile: ServerProfileEntity)
    @Query("SELECT * FROM ServerProfileEntity WHERE serverKey = :serverKey AND userId = :userId") suspend fun get(serverKey: String, userId: String): ServerProfileEntity?
    @Query("SELECT * FROM ServerProfileEntity WHERE isOfflineAvailable = 1 ORDER BY lastSuccessfulSyncMs DESC LIMIT 1") suspend fun lastUsable(): ServerProfileEntity?
}
@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfMissing(track: TrackEntity)
    @Query("UPDATE TrackEntity SET name = :name, type = :type, albumId = :albumId, album = :album, artistText = :artistText, durationTicks = :durationTicks, container = :container, metadataJson = :metadataJson, composerText = :composerText, lyricistText = :lyricistText, genreJson = :genreJson, overview = :overview, productionYear = :productionYear, premiereDate = :premiereDate, sortName = :sortName, bitrate = :bitrate, mediaSourceJson = :mediaSourceJson WHERE serverKey = :serverKey AND itemId = :itemId")
    suspend fun updateMetadata(serverKey: String, itemId: String, name: String, type: String?, albumId: String?, album: String?, artistText: String, durationTicks: Long?, container: String?, metadataJson: String, composerText: String, lyricistText: String, genreJson: String, overview: String?, productionYear: Int?, premiereDate: String?, sortName: String?, bitrate: Int?, mediaSourceJson: String)
    @Transaction suspend fun upsert(track: TrackEntity) { insertIfMissing(track); updateMetadata(track.serverKey, track.itemId, track.name, track.type, track.albumId, track.album, track.artistText, track.durationTicks, track.container, track.metadataJson, track.composerText, track.lyricistText, track.genreJson, track.overview, track.productionYear, track.premiereDate, track.sortName, track.bitrate, track.mediaSourceJson) }
    @Transaction suspend fun upsert(tracks: List<TrackEntity>) { tracks.forEach { upsert(it) } }
    @Query("SELECT * FROM TrackEntity WHERE serverKey = :serverKey AND itemId = :itemId") suspend fun get(serverKey: String, itemId: String): TrackEntity?
    @Query("SELECT COUNT(*) FROM TrackEntity WHERE serverKey = :serverKey") suspend fun count(serverKey: String): Int
    @Query("SELECT * FROM TrackEntity WHERE serverKey = :serverKey ORDER BY name COLLATE NOCASE") suspend fun all(serverKey: String): List<TrackEntity>
    @Query("UPDATE TrackEntity SET localAudioPath = :audioPath, localCoverPath = :coverPath, downloadedTimeMs = :downloadedTimeMs, qualityLabel = :qualityLabel WHERE serverKey = :serverKey AND itemId = :itemId") suspend fun updateMediaState(serverKey: String, itemId: String, audioPath: String?, coverPath: String?, downloadedTimeMs: Long?, qualityLabel: String?): Int
}
@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: LyricsEntity)
    @Query("SELECT * FROM LyricsEntity WHERE serverKey = :serverKey AND itemId = :itemId AND language = 'default'") suspend fun get(serverKey: String, itemId: String): LyricsEntity?
}

@Dao
interface AlbumDao { @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(albums: List<AlbumEntity>); @Query("SELECT * FROM AlbumEntity WHERE serverKey = :serverKey ORDER BY name COLLATE NOCASE") suspend fun all(serverKey: String): List<AlbumEntity> }
@Dao
interface ArtistDao { @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(artists: List<ArtistEntity>); @Query("SELECT * FROM ArtistEntity WHERE serverKey = :serverKey ORDER BY name COLLATE NOCASE") suspend fun all(serverKey: String): List<ArtistEntity> }
@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(playlist: PlaylistEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(playlists: List<PlaylistEntity>)
    @Query("UPDATE PlaylistEntity SET lastSyncedMs = :syncedAt WHERE serverKey = :serverKey AND itemId = :playlistId") suspend fun markSynced(serverKey: String, playlistId: String, syncedAt: Long)
    @Query("SELECT * FROM PlaylistEntity WHERE serverKey = :serverKey ORDER BY name COLLATE NOCASE") suspend fun all(serverKey: String): List<PlaylistEntity>
    @Query("DELETE FROM PlaylistEntity WHERE serverKey = :serverKey") suspend fun deleteAll(serverKey: String)
}
@Dao
interface PlaylistTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(relations: List<PlaylistTrackEntity>)
    @Query("DELETE FROM PlaylistTrackEntity WHERE serverKey = :serverKey AND playlistId = :playlistId") suspend fun deleteForPlaylist(serverKey: String, playlistId: String)
    @Query("SELECT * FROM PlaylistTrackEntity WHERE serverKey = :serverKey AND playlistId = :playlistId ORDER BY orderIndex") suspend fun tracksInOrder(serverKey: String, playlistId: String): List<PlaylistTrackEntity>
    @Query("DELETE FROM PlaylistTrackEntity WHERE serverKey = :serverKey") suspend fun deleteAll(serverKey: String)
    @Transaction suspend fun replaceSnapshot(serverKey: String, playlistId: String, trackIdsInOrder: List<String>) { deleteForPlaylist(serverKey, playlistId); insertAll(trackIdsInOrder.mapIndexed { index, trackId -> PlaylistTrackEntity(serverKey = serverKey, playlistId = playlistId, trackId = trackId, orderIndex = index) }) }
}
@Dao
interface ImageDao { @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(images: List<ImageEntity>); @Query("SELECT * FROM ImageEntity WHERE serverKey = :serverKey AND imageKey = :imageKey") suspend fun get(serverKey: String, imageKey: String): ImageEntity?; @Query("UPDATE ImageEntity SET localPath = :localPath, downloadState = :state WHERE serverKey = :serverKey AND imageKey = :imageKey") suspend fun updateLocalState(serverKey: String, imageKey: String, localPath: String?, state: String) }
@Dao
interface LegacyImportDao { @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(state: LegacyImportStateEntity): Long; @Query("SELECT EXISTS(SELECT 1 FROM LegacyImportStateEntity WHERE serverKey = :serverKey AND indexName = :indexName)") suspend fun hasImported(serverKey: String, indexName: String): Boolean }
@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(task: DownloadTaskEntity)
    @Query("SELECT * FROM DownloadTaskEntity WHERE serverKey = :serverKey ORDER BY updatedAtMs") suspend fun all(serverKey: String): List<DownloadTaskEntity>
    @Query("UPDATE DownloadTaskEntity SET status = 'WAITING' WHERE serverKey = :serverKey AND status = 'RUNNING'") suspend fun recoverRunning(serverKey: String): Int
    @Query("UPDATE DownloadTaskEntity SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updatedAtMs = :updatedAtMs WHERE serverKey = :serverKey AND taskId = :taskId") suspend fun updateProgress(serverKey: String, taskId: String, downloadedBytes: Long, totalBytes: Long, updatedAtMs: Long)
    @Query("UPDATE DownloadTaskEntity SET status = :status, failureReason = :failureReason, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updatedAtMs = :updatedAtMs WHERE serverKey = :serverKey AND taskId = :taskId") suspend fun updateStatus(serverKey: String, taskId: String, status: String, failureReason: String?, downloadedBytes: Long, totalBytes: Long, updatedAtMs: Long)
}
class LegacyDownloadIndexImporter(private val json: kotlinx.serialization.json.Json) {
    fun read(file: java.io.File): List<DownloadedSong> { if (!file.exists()) return emptyList(); return json.decodeFromString(file.readText()) }
    suspend fun importOnce(file: java.io.File, serverKey: String, database: OfflineDatabase): Int {
        if (!file.exists()) return 0
        return database.withTransaction {
            val indexName = file.name
            if (database.legacyImports().hasImported(serverKey, indexName)) return@withTransaction 0
            val records = read(file).filter { java.io.File(it.localFilePath).exists() }
            database.tracks().upsert(records.map { record -> record.item.toTrackEntity(serverKey).copy(localAudioPath = record.localFilePath, localCoverPath = record.localCoverPath, downloadedTimeMs = record.downloadedTimeMs, qualityLabel = record.qualityLabel) })
            database.legacyImports().insert(LegacyImportStateEntity(serverKey, indexName, System.currentTimeMillis())); records.size
        }
    }
}
