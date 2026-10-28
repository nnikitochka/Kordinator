package org.airsonic.player.controller.rest.shares

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.SubsonicResponseMapper
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.Share as DomainShare
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.MediaFolderService
import org.airsonic.player.service.ShareService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import org.subsonic.restapi.Share
import org.subsonic.restapi.Shares
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class SharesController(
    private val shares: ShareService,
    private val mediaFiles: MediaFileService,
    private val folders: MediaFolderService,
    private val mapper: SubsonicResponseMapper,
    private val writer: JAXBWriter,
) {
    fun routes(root: Route, request: (ApplicationCall) -> SharesRequest) {
        with(root) {
            PATHS.forEach { path ->
                val operation = path.substringAfterLast('/').removeSuffix(".view")
                get(path) { respond(call, request(call), operation) }
                post(path) { respond(call, request(call), operation) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: SharesRequest, operation: String) {
        val result = try {
            when (operation) {
                "getShares" -> getShares(request)
                "createShare" -> createShare(request)
                "updateShare" -> changeShare(request, false)
                "deleteShare" -> changeShare(request, true)
                else -> error("Unknown share operation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error in shares API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: e.javaClass.simpleName)
        }
        if (result == null) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
        val serialized = writer.serializeForType(
            request.parameters["f"] ?: "xml", request.parameters["callback"], result
        )
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun getShares(request: SharesRequest): Response {
        val result = Shares()
        val musicFolders = folders.getMusicFoldersForUser(request.user.username)
        shares.getSharesForUser(request.user).forEach { share ->
            result.share.add(toRest(request, share, musicFolders))
        }
        return writer.createResponse(true).apply { this.shares = result }
    }

    private fun createShare(request: SharesRequest): Response? {
        if (!request.user.isShareRole) {
            return errorResponse(ErrorCode.NOT_AUTHORIZED, "${request.user.username} is not authorized to share media.")
        }
        val values = request.parameters.getAll("id") ?: return missingId()
        val ids = values.map { it.toIntOrNull() ?: return null }
        val files = ids.map { mediaFiles.getMediaFile(it) }
        val share = request.createShare(files)
        share.description = request.parameters["description"]
        val expiry = request.parameters["expires"]?.toLongOrNull() ?: 0L
        if (expiry != 0L) share.expires = Instant.ofEpochMilli(expiry)
        shares.updateShare(share)
        val result = Shares()
        val musicFolders = folders.getMusicFoldersForUser(request.user.username)
        result.share.add(toRest(request, share, musicFolders))
        return writer.createResponse(true).apply { this.shares = result }
    }

    private fun changeShare(request: SharesRequest, delete: Boolean): Response? {
        val id = request.id?.let { it.toIntOrNull() ?: return null } ?: return missingId()
        val share = shares.getShareById(id) ?: return errorResponse(ErrorCode.NOT_FOUND, "Shared media not found.")
        if (!request.user.isAdminRole && share.username != request.user.username) {
            val action = if (delete) "delete" else "modify"
            return errorResponse(ErrorCode.NOT_AUTHORIZED, "Not authorized to $action shared media.")
        }
        if (delete) {
            shares.deleteShare(id)
        } else {
            share.description = request.parameters["description"]
            request.parameters["expires"]?.let {
                val expiry = it.toLong()
                share.expires = if (expiry == 0L) null else Instant.ofEpochMilli(expiry)
            }
            shares.updateShare(share)
        }
        return writer.createResponse(true)
    }

    private fun toRest(request: SharesRequest, share: DomainShare, musicFolders: List<org.airsonic.player.domain.MusicFolder>): Share {
        val result = Share().apply {
            id = share.id.toString()
            url = request.shareUrl(share)
            username = share.username
            created = writer.convertDate(share.created)
            visitCount = share.visitCount
            description = share.description
            expires = writer.convertDate(share.expires)
            lastVisited = writer.convertDate(share.lastVisited)
        }
        shares.getSharedFiles(share.id, musicFolders).forEach {
            result.entry.add(mapper.child(requireNotNull(request.player), it, request.user.username))
        }
        return result
    }

    private fun missingId() = errorResponse(ErrorCode.MISSING_PARAMETER, "Required param (id) is missing")
    private fun errorResponse(code: ErrorCode, message: String) = writer.createErrorResponse(code, message)

    companion object {
        private val LOG = LoggerFactory.getLogger(SharesController::class.java)
        @JvmField val PATHS = arrayOf(
            "/rest/getShares", "/rest/getShares.view",
            "/rest/createShare", "/rest/createShare.view",
            "/rest/deleteShare", "/rest/deleteShare.view",
            "/rest/updateShare", "/rest/updateShare.view",
        )
    }
}
