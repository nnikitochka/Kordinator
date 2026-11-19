package org.airsonic.player.controller.rest.info.artist

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.domain.ArtistBio
import org.airsonic.player.service.LastFmService
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.ArtistID3
import org.subsonic.restapi.ArtistInfo
import org.subsonic.restapi.ArtistInfo2
import org.subsonic.restapi.ArtistInfoBase
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class ArtistInfoController(
    private val mediaFiles: MediaFileService,
    private val artists: ArtistDao,
    private val lastFm: LastFmService,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> ArtistInfoRequest) {
        with(root) {
            PATHS.forEach { path ->
                val second = path.removeSuffix(".view").endsWith("2")
                get(path) { respond(call, request(call), second) }
                post(path) { respond(call, request(call), second) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: ArtistInfoRequest, second: Boolean) {
        val response = try {
            artistInfo(request, second)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in artist info API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        val serialized = writer.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], response
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun artistInfo(request: ArtistInfoRequest, second: Boolean): Response {
        val id = request.id ?: return writer.createErrorResponse(
            ErrorCode.MISSING_PARAMETER, "Required param (id) is missing"
        )
        val count = request.parameters["count"]?.toIntOrNull() ?: 20
        val includeNotPresent = request.parameters["includeNotPresent"]?.let {
            it.equals("true", true) || it.equals("on", true) || it.equals("yes", true) || it == "1"
        } ?: false
        return if (second) {
            val artist = artists.getArtist(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Artist not found.")
            val similar = lastFm.getSimilarArtists(artist, count, includeNotPresent, folders.getMusicFoldersForUser(request.username))
            val result = ArtistInfo2()
            similar.forEach { result.similarArtist.add(mapper.artist(ArtistID3(), it, request.username)) }
            lastFm.getArtistBio(artist, request.locale)?.let { setBio(result, it) }
            writer.createResponse(true).apply { artistInfo2 = result }
        } else {
            val mediaFile = mediaFiles.getMediaFile(id)
                ?: return writer.createErrorResponse(ErrorCode.NOT_FOUND, "Media file not found.")
            val musicFolders = folders.getMusicFoldersForUser(request.username)
            val similar = lastFm.getSimilarArtists(mediaFile, count, includeNotPresent, musicFolders)
            val result = ArtistInfo()
            similar.forEach { artist ->
                result.similarArtist.add(mapper.artist(artist, request.username))
            }
            lastFm.getArtistBio(mediaFile, request.locale)?.let { setBio(result, it) }
            writer.createResponse(true).apply {
                artistInfo = result
            }
        }
    }

    private fun setBio(result: ArtistInfoBase, bio: ArtistBio) {
        result.biography = bio.biography
        result.musicBrainzId = bio.musicBrainzId
        result.lastFmUrl = bio.lastFmUrl
        result.smallImageUrl = bio.smallImageUrl
        result.mediumImageUrl = bio.mediumImageUrl
        result.largeImageUrl = bio.largeImageUrl
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ArtistInfoController::class.java)
        @JvmField
        val PATHS = arrayOf(
            "/rest/getArtistInfo", "/rest/getArtistInfo.view",
            "/rest/getArtistInfo2", "/rest/getArtistInfo2.view",
        )
    }
}
