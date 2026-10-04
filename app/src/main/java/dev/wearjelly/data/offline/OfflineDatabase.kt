package dev.wearjelly.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ServerProfileEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ImageEntity::class,
        DownloadTaskEntity::class,
        LegacyImportStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(OfflineConverters::class)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun tracks(): TrackDao
    abstract fun albums(): AlbumDao
    abstract fun artists(): ArtistDao
    abstract fun playlists(): PlaylistDao
    abstract fun playlistTracks(): PlaylistTrackDao
    abstract fun images(): ImageDao
    abstract fun legacyImports(): LegacyImportDao
    abstract fun downloadTasks(): DownloadTaskDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, "offline-library.db")
                .build()
                .also { instance = it }
        }
    }
}
