package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.service.SettingsService
import org.apache.commons.io.IOUtils
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.LastModified
import kotlin.math.max

/**
 * Controller which produces avatar images.
 */
@Controller
@RequestMapping("/avatar")
class AvatarController(private val settingsService: SettingsService, private val loader: ResourceLoader) :
    LastModified {
    override fun getLastModified(request: HttpServletRequest): Long {
        val avatar = getAvatar(request)
        var result = if (avatar == null) -1L else avatar.createdDate.toEpochMilli()

        val username = request.getParameter("username")
        if (username != null) {
            val userSettings = settingsService.getUserSettings(username)
            result = max(result, userSettings.changed.toEpochMilli())
        }

        return result
    }

    @GetMapping
    @Throws(Exception::class)
    fun handleRequest(request: HttpServletRequest, response: HttpServletResponse) {
        val avatar = getAvatar(request) ?: return response.sendError(HttpServletResponse.SC_NOT_FOUND)

        response.contentType = avatar.mimeType
        var res = loader.getResource(avatar.path.toString())
        if (!res.exists()) {
            res = loader.getResource("file:" + avatar.path.toString())
        }
        IOUtils.copy(res.inputStream, response.outputStream)
    }

    private fun getAvatar(request: HttpServletRequest): Avatar? {
        val id = request.getParameter("id")
        val forceCustom = ServletRequestUtils.getBooleanParameter(request, "forceCustom", false)

        if (id != null) {
            return settingsService.getSystemAvatar(id.toInt())
        }

        val username = request.getParameter("username") ?: return null

        val userSettings = settingsService.getUserSettings(username)
        if (userSettings.avatarScheme == AvatarScheme.CUSTOM || forceCustom) {
            return settingsService.getCustomAvatar(username)
        }
        if (userSettings.avatarScheme == AvatarScheme.NONE) {
            return null
        }
        return settingsService.getSystemAvatar(userSettings.systemAvatarId)
    }
}
