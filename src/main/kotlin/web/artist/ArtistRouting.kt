package su.nnedition.kord.web.artist

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import su.nnedition.kord.Kordinator

fun Application.configureArtistsRouting() {
    routing {
        get("/artists/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID format")
                return@get
            }

            val artist = Kordinator.library.getArtist(id) ?: run {
                call.respond(HttpStatusCode.NotFound, "Artist with id $id not found")
                return@get
            }

            call.respond(ArtistResponseModel(
                artist.name,
                artist.genres.map { it.raw }.toSet(),
            ))
        }
    }
}