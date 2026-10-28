package org.airsonic.player.controller.rest.artists

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.ArtistsID3
import org.subsonic.restapi.IndexID3
import kotlin.coroutines.cancellation.CancellationException

/** Ktor routes for the Subsonic ID3 artist index. */
class ArtistsController(
    private val artistDao: ArtistDao,
    private val musicIndexService: MusicIndexService,
    private val settingsService: SettingsService,
    private val mediaFolderService: MediaFolderService,
    private val responseMapper: SubsonicResponseMapper,
    private val jaxbWriter: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> ArtistsRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: ArtistsRequest) {
        val response = try {
            jaxbWriter.createResponse(true).apply {
                artists = getArtists(request.username)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in Subsonic artists API", e)
            jaxbWriter.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = jaxbWriter.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], response
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getArtists(username: String): ArtistsID3 = ArtistsID3().also { result ->
        result.ignoredArticles = settingsService.ignoredArticles
        val folders = mediaFolderService.getMusicFoldersForUser(username)
        val artists = artistDao.getAlphabetialArtists(0, Int.MAX_VALUE, folders)
        for ((musicIndex, sortableArtists) in musicIndexService.getIndexedArtists(artists)) {
            val index = IndexID3().apply { name = musicIndex.index }
            result.index += index
            for (artist in sortableArtists) {
                index.artist += responseMapper.artist(ArtistID3(), artist.artist, username)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ArtistsController::class.java)

        @JvmField
        val PATHS = arrayOf("/rest/getArtists", "/rest/getArtists.view")
    }
}
