package su.nnedition.kord.auth.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import su.nnedition.kord.auth.TokenEntity

@Dao
interface TokensDao {
    @Insert
    suspend fun insertToken(token: TokenEntity)

    @Query("SELECT * FROM tokens WHERE token = :token AND expires_at > :now LIMIT 1")
    suspend fun findValidToken(token: String, now: Long): TokenEntity?

    @Query("DELETE FROM tokens WHERE expires_at <= :now")
    suspend fun deleteExpiredTokens(now: Long)

    @Query("DELETE FROM tokens WHERE user_id = :userId")
    suspend fun deleteAllUserTokens(userId: Long)
}