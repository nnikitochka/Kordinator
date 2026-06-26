package su.nnedition.kord.data.local.artist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtistsDao {
    @Query("SELECT COUNT(*) FROM ${ArtistEntity.TABLE_NAME}")
    suspend fun getArtistsCount(): Long

    @Query("SELECT * FROM ${ArtistEntity.TABLE_NAME} WHERE artist_id = :id")
    suspend fun findArtistById(id: Long): ArtistEntity?
    @Query("SELECT * FROM ${ArtistEntity.TABLE_NAME} WHERE name = :name")
    suspend fun findArtistByName(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)
}