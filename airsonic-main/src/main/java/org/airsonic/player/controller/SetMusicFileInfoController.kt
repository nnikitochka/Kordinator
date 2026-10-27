package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.MediaFileService
import org.apache.commons.lang.StringEscapeUtils
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView

/**
 * Controller for updating music file metadata.
 */
@Controller
@RequestMapping("/setMusicFileInfo")
class SetMusicFileInfoController(private val mediaFileService: MediaFileService) {
    @PostMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest): ModelAndView {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        val action = request.getParameter("action")

        val mediaFile = mediaFileService.getMediaFile(id)

        if ("comment" == action) {
            mediaFile.comment = StringEscapeUtils.escapeHtml(request.getParameter("comment"))
            mediaFileService.updateMediaFile(mediaFile)
        }

        val url = "main.view?id=$id"
        return ModelAndView(RedirectView(url))
    }
}
