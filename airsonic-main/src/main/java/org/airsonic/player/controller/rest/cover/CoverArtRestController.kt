package org.airsonic.player.controller.rest.cover

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.controller.cover.CoverArtController

/** Temporary REST adapter for the existing cover art controller. */
class CoverArtRestController(private val coverArtController: CoverArtController) {
    fun handleRequest(request: HttpServletRequest, response: HttpServletResponse) {
        coverArtController.handleRequest(request, response)
    }

    companion object {
        @JvmField
        val PATHS = arrayOf("/rest/getCoverArt", "/rest/getCoverArt.view")
    }
}
