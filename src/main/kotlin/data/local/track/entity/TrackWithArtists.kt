package su.nnedition.kord.data.local.track.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import su.nnedition.kord.data.local.artist.ArtistEntity

data class TrackWithArtists(
    @Embedded
    val track: TrackEntity,

    @Relation(
        parentColumn = "track_id",
        entityColumn = "artist_id",
        associateBy = Junction(
            value = TrackArtistCrossRef::class,
            parentColumn = "track_id",
            entityColumn = "artist_id"
        )
    )
    val artists: List<ArtistEntity>
)