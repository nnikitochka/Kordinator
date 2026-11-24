package su.nnedition.kord.api.module.favorite

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.airsonic.player.ktor.respondJson
import org.airsonic.player.security.LocalUserPrincipal
import org.airsonic.player.service.StandaloneFavoriteService
import su.nnedition.kord.api.ApiError
import java.util.Base64

internal fun parsePage(limitValue: String?, offsetValue: String?): Pair<Int, Int>? {
    val limit = limitValue?.toIntOrNull() ?: if (limitValue == null) 50 else null
    val offset = offsetValue?.toIntOrNull() ?: if (offsetValue == null) 0 else null
    if (limit == null || limit !in 1..100 || offset == null || offset < 0) return null
    return limit to offset
}

internal fun LocalUserPrincipal.libraryScope() = if (role == "ADMIN") null else allowedLibraryIds

internal suspend fun ApplicationCall.updateCatalogFavorite(
    favoriteService: StandaloneFavoriteService?,
    dispatcher: CoroutineDispatcher,
    favorite: Boolean,
    artist: Boolean
) {
    val user = principal<LocalUserPrincipal>() ?: return respond(HttpStatusCode.Unauthorized)
    val favoriteService = favoriteService ?: return respondJson(
        ApiError("unavailable", "Favorite storage is not configured"), HttpStatusCode.ServiceUnavailable,
    )
    val selector = parameters["id"]?.decodeCatalogId() ?: return respondJson(ApiError(
        "invalid_request", "Catalog ID is invalid"
    ), HttpStatusCode.BadRequest)

    val saved = try {
        withContext(dispatcher) {
            if (artist) favoriteService.setArtist(user.id, selector.first, selector.second, favorite, user.libraryScope())
            else favoriteService.setAlbum(user.id, selector.first, selector.second, favorite, user.libraryScope())
        }
    } catch (failure: IllegalArgumentException) {
        return respondJson(ApiError("invalid_request", failure.message ?: "Catalog favorite is invalid"), HttpStatusCode.BadRequest)
    }
    if (saved) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound)
}

private fun String.decodeCatalogId(): Pair<Long, String>? {
    return try {
        val separator = indexOf('.')
        if (separator < 1 || separator == lastIndex) return null
        val libraryId = substring(0, separator).toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val name = Base64.getUrlDecoder().decode(substring(separator + 1)).toString(Charsets.UTF_8)
        if (name.isBlank()) null else libraryId to name
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun catalogId(libraryId: Long, name: String) =
    "$libraryId.${Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))}"