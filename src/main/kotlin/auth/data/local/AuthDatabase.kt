package su.nnedition.kord.auth.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import su.nnedition.kord.auth.TokenEntity
import su.nnedition.kord.auth.UserEntity

@Database(
    entities = [
        UserEntity::class,
        TokenEntity::class
    ],
    version = 1
)
abstract class AuthDatabase : RoomDatabase() {
    abstract val usersDao: UsersDao
    abstract val tokensDao: TokensDao

    companion object {
        @Volatile
        private var INSTANCE: AuthDatabase? = null

        fun getOrCreate(): AuthDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder<AuthDatabase>(
                    name = "data/auth_data.db"
                )
                    .setDriver(BundledSQLiteDriver())
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}