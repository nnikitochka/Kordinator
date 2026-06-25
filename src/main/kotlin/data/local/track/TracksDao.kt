package su.nnedition.kord.data.local.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import su.nnedition.kord.data.local.track.entity.TrackArtistCrossRef
import su.nnedition.kord.data.local.track.entity.TrackEntity
import su.nnedition.kord.data.local.track.entity.TrackWithArtists

@Dao
interface TracksDao {
    @Insert
    suspend fun insertTrack(track: TrackEntity)

    @Query("SELECT * FROM ${TrackEntity.TABLE_NAME}")
    suspend fun getTracks(): List<TrackEntity>

    // Низкоуровневая вставка самого трека
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackEntity(track: TrackEntity): Long

    // Низкоуровневая вставка связей с артистами
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackArtistRefs(refs: List<TrackArtistCrossRef>)

    // Публичный метод вставки: сохраняет трек и связывает его с ID переданных артистов
    @Transaction
    suspend fun insertTrackWithArtists(track: TrackWithArtists) {
        val trackId = insertTrackEntity(track.track)
        val refs = track.artists.map { TrackArtistCrossRef(trackId = trackId, artistId = it.id) }
        insertTrackArtistRefs(refs)
    }

    // Запрос возвращает полную структуру (трек + артисты)
    @Transaction
    @Query("SELECT * FROM ${TrackEntity.TABLE_NAME}")
    suspend fun getTracksWithArtists(): List<TrackWithArtists>

    @Query("SELECT COUNT(*) FROM ${TrackEntity.TABLE_NAME}")
    suspend fun getTracksCount(): Long
}