package su.nnedition.kord.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import su.nnedition.kord.data.local.artist.ArtistEntity
import su.nnedition.kord.data.local.track.entity.TrackEntity
import su.nnedition.kord.data.local.artist.ArtistsDao
import su.nnedition.kord.data.local.track.TracksDao
import su.nnedition.kord.data.local.track.entity.TrackArtistCrossRef

@Database(
    entities = [
        ArtistEntity::class,
        TrackEntity::class,
        TrackArtistCrossRef::class,
    ],
    version = 5
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract val artistsDao: ArtistsDao
    abstract val tracksDao: TracksDao

    companion object {
        @Volatile
        private var INSTANCE: LibraryDatabase? = null

        fun getOrCreateDatabase(): LibraryDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder<LibraryDatabase>(
                    name = "data/library_data.db",
                )
                    .setDriver(BundledSQLiteDriver())
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}