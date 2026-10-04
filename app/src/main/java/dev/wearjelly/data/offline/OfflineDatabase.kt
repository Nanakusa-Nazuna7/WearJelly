package dev.wearjelly.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServerProfileEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ImageEntity::class,
        LyricsEntity::class,
        DownloadTaskEntity::class,
        LegacyImportStateEntity::class
    ],
    version = 2,
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
    abstract fun lyrics(): LyricsDao
    abstract fun legacyImports(): LegacyImportDao
    abstract fun downloadTasks(): DownloadTaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN composerText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN lyricistText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN genreJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN overview TEXT")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN productionYear INTEGER")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN premiereDate TEXT")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN sortName TEXT")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE TrackEntity ADD COLUMN mediaSourceJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("CREATE TABLE IF NOT EXISTS LyricsEntity (serverKey TEXT NOT NULL, itemId TEXT NOT NULL, language TEXT NOT NULL, lyricsJson TEXT NOT NULL, isSynchronized INTEGER NOT NULL, state TEXT NOT NULL, fetchedAtMs INTEGER, lastError TEXT, PRIMARY KEY(serverKey, itemId, language))")
            }
        }
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDatabase::class.java, "offline-library.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
