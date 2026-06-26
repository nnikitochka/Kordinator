package su.nnedition.kord.auth.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import su.nnedition.kord.auth.UserEntity

@Dao
interface UsersDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users WHERE login = :login LIMIT 1")
    suspend fun findByLogin(login: String): UserEntity?

    @Query("SELECT * FROM users WHERE user_id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?
}