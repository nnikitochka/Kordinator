package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.MediaFileService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for changing cover art.
 */
@Controller
@RequestMapping("/changeCoverArt")
class ChangeCoverArtController(private val mediaFileService: MediaFileService) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        var artist = request.getParameter("artist")
        var album = request.getParameter("album")
        val dir = mediaFileService.getMediaFile(id)

        if (artist.isBlank()) {
            artist = dir.artist
        }
        if (album.isBlank()) {
            album = dir.albumName
        }

        val map = buildMap {
            put("id", id)
            put("artist", artist)
            put("album", album)
        }

        return ModelAndView("changeCoverArt", "model", map)
    }
}
