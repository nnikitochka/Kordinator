package su.nnedition.kord.data.local.track.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import su.nnedition.kord.data.local.artist.ArtistEntity

// Связующая таблица «Многие-ко-Многим»
@Entity(
    tableName = "track_artists",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE // Удалили трек -> удалились связи с артистами
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE // Удалили артиста -> трек перестал быть с ним связан
        )
    ],
    indices = [Index(value = ["trackId"]), Index(value = ["artistId"])]
)
data class TrackArtistCrossRef(
    val trackId: Long,
    val artistId: Long
)