package org.airsonic.player.controller.rest.indexes

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.MusicIndexService
import org.airsonic.player.service.RatingService
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Artist
import org.subsonic.restapi.Index
import org.subsonic.restapi.Indexes
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class IndexesController(
    private val settings: SettingsService,
    private val folders: MediaFolderService,
    private val musicIndex: MusicIndexService,
    private val mediaFiles: MediaFileDao,
    private val ratings: RatingService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> IndexesRequest) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call)) }
                post(path) { respond(call, request(call)) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: IndexesRequest) {
        val result = try {
            getIndexes(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in indexes API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getIndexes(request: IndexesRequest): Response {
        val result = writer.createResponse(true)
        val ifModifiedSince = request.parameters["ifModifiedSince"]?.toLongOrNull() ?: 0L
        val lastModified = request.lastModified()
        if (lastModified <= ifModifiedSince) return result

        val indexes = Indexes().apply {
            this.lastModified = lastModified
            ignoredArticles = settings.ignoredArticles
        }
        val allowedFolders = folders.getMusicFoldersForUser(request.username)
        val selectedFolder = allowedFolders.find { it.id == request.folderId }
        val selectedFolders = if (selectedFolder == null) allowedFolders else listOf(selectedFolder)
        musicIndex.getShortcuts(selectedFolders).forEach { indexes.shortcut.add(mapper.artist(it, request.username)) }
        val content = musicIndex.getMusicFolderContent(selectedFolders, false)
        content.indexedArtists.forEach { (key, artists) ->
            val index = Index().apply { name = key.index }
            indexes.index.add(index)
            artists.forEach { artist ->
                artist.mediaFiles.forEach { file ->
                    if (file.isDirectory) {
                        val entry = Artist().apply {
                            id = file.id.toString()
                            name = artist.name
                            starred = writer.convertDate(mediaFiles.getMediaFileStarredDate(file.id, request.username))
                            if (file.isAlbum) {
                                averageRating = ratings.getAverageRating(file)
                                userRating = ratings.getRatingForUser(request.username, file)
                            }
                        }
                        index.artist.add(entry)
                    }
                }
            }
        }
        val player = request.player()
        content.singleSongs.forEach { indexes.child.add(mapper.child(player, it, request.username)) }
        result.indexes = indexes
        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(IndexesController::class.java)
        @JvmField val PATHS = arrayOf("/rest/getIndexes", "/rest/getIndexes.view")
    }
}
