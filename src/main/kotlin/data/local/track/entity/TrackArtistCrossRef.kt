package su.nnedition.kord.data.local.track.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import su.nnedition.kord.data.local.artist.ArtistEntity

@Entity(
    tableName = "track_artists",
    primaryKeys = ["track_id", "artist_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE // Удалили трек -> удалились связи с артистами
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["artist_id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE // Удалили артиста -> трек перестал быть с ним связан
        )
    ],
    indices = [Index(value = ["track_id"]), Index(value = ["artist_id"])]
)
data class TrackArtistCrossRef(
    @ColumnInfo(name = "track_id")
    val trackId: Long,
    @ColumnInfo(name = "artist_id")
    val artistId: Long
)