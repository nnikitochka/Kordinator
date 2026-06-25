package su.nnedition.kord.web.artist

import kotlinx.serialization.Serializable

@Serializable
class ArtistReceiveRemote(
    val id: Long,
)

@Serializable
class ArtistResponseModel(
    val name: String,
    val genres: Set<String>,
)