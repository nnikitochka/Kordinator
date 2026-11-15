package org.airsonic.player.controller.rest.avatar

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.service.SettingsService
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Serves the Subsonic avatar endpoint without dispatching to Spring MVC. */
class AvatarRestController(
    private val settings: SettingsService
) {
    data class Request(
        val params: Parameters,
        val webResource: (String) -> InputStream?,
        val lenientForceCustom: Boolean = false
    )

    fun routes(root: Route, request: (ApplicationCall) -> Request) {
        with(root) {
            PATHS.forEach { path ->
                get(path) { respond(call, request(call), false) }
                post(path) { respond(call, request(call), false) }
                head(path) { respond(call, request(call), true) }
            }
            LEGACY_PATHS.forEach { path ->
                get(path) { respond(call, request(call), false) }
                head(path) { respond(call, request(call), true) }
            }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: Request, head: Boolean) {
        val avatar = findAvatar(request.params, request.lenientForceCustom)
        if (avatar == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val type = ContentType.parse(avatar.mimeType)
        val input = openAvatar(request, avatar)
        if (head) {
            input.close()
            call.respond(object : OutgoingContent.NoContent() { override val contentType = type })
        } else {
            call.respondOutputStream(type) { input.use { it.copyTo(this) } }
        }
    }

    private fun findAvatar(params: Parameters, lenientForceCustom: Boolean): Avatar? {
        params["id"]?.let { return settings.getSystemAvatar(it.toInt()) }
        val username = params["username"] ?: return null
        val forceCustom = when (params["forceCustom"]?.lowercase()) {
            null, "false", "off", "no", "0" -> false
            "true", "on", "yes", "1" -> true
            else -> if (lenientForceCustom) false else throw IllegalArgumentException("Invalid forceCustom")
        }
        val userSettings = settings.getUserSettings(username)
        return when {
            userSettings.avatarScheme == AvatarScheme.CUSTOM || forceCustom -> settings.getCustomAvatar(username)
            userSettings.avatarScheme == AvatarScheme.NONE -> null
            else -> settings.getSystemAvatar(userSettings.systemAvatarId)
        }
    }

    private fun openAvatar(request: Request, avatar: Avatar): InputStream {
        val path = avatar.path
        val text = path.toString()
        if (text.startsWith("classpath:")) {
            javaClass.classLoader.getResourceAsStream(text.removePrefix("classpath:").removePrefix("/"))?.let { return it }
        } else if (text.startsWith("file:")) {
            return Files.newInputStream(Path.of(URI.create(text)))
        } else {
            request.webResource("/" + text.replace('\\', '/').trimStart('/'))?.let { return it }
        }
        return Files.newInputStream(path)
    }

    companion object {
        @JvmField val PATHS = arrayOf("/rest/getAvatar", "/rest/getAvatar.view")
        @JvmField val LEGACY_PATHS = arrayOf("/avatar", "/avatar.view")
    }
}
