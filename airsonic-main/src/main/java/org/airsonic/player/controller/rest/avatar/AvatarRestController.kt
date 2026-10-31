package org.airsonic.player.controller.rest.avatar

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.controller.AvatarController

/** Temporary REST adapter for the existing avatar controller. */
class AvatarRestController(private val avatarController: AvatarController) {
    fun handleRequest(request: HttpServletRequest, response: HttpServletResponse) {
        avatarController.handleRequest(request, response)
    }

    companion object {
        @JvmField
        val PATHS = arrayOf("/rest/getAvatar", "/rest/getAvatar.view")
    }
}
