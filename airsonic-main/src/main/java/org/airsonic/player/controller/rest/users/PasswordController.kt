package org.airsonic.player.controller.rest.users

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.domain.UserCredential
import org.airsonic.player.util.StringUtil.decodeSubsonicPassword
import org.airsonic.player.service.SecurityService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.Response
import kotlin.coroutines.cancellation.CancellationException

class PasswordController(private val security: SecurityService, private val writer: JAXBWriter) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) { PATHS.forEach { path ->
            get(path) { respond(call, request(call)) }
            post(path) { respond(call, request(call)) }
        } }
    }
    private suspend fun respond(call: ApplicationCall, request: UsersRequest) {
        val result = try { changePassword(request) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            LOG.warn("Error in password API", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Request processing failed: $e")
        }
        val resultText = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], result)
        call.respondText(resultText.value, ContentType.parse(resultText.key))
    }
    private fun changePassword(request: UsersRequest): Response {
        val username = request.parameters["username"] ?: return missing("username")
        val password = decodeSubsonicPassword(request.parameters["password"] ?: return missing("password"))
        val current = request.user
        if (!current.isAdminRole && !(username == current.username && current.isSettingsRole)) {
            return writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "${current.username} is not authorized to change password for $username")
        }
        val user = security.getUserByName(username)
        val credential = UserCredential(user.username, user.username, password, security.getPreferredPasswordEncoder(true), UserCredential.App.AIRSONIC, "Created via Subsonic REST API")
        security.createCredential(credential)
        return writer.createResponse(true)
    }
    private fun missing(name: String) = writer.createErrorResponse(ErrorCode.MISSING_PARAMETER, "Required param ($name) is missing")
    companion object {
        private val LOG = LoggerFactory.getLogger(PasswordController::class.java)
        @JvmField val PATHS = arrayOf("/rest/changePassword", "/rest/changePassword.view")
    }
}
