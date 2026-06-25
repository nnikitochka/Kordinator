package su.nnedition.kord.data.domain.artist

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import su.nnedition.kord.data.domain.Genre

@Serializable
data class Artist(
    val id: Long,
    val name: String,
    val genres: Set<Genre>,
    @Transient
    val meta: ArtistMeta? = null,
) {
}