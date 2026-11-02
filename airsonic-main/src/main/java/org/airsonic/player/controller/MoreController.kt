package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.service.*
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import java.time.LocalDate
import java.util.function.Supplier

/**
 * Controller for the "more" page.
 */
@Controller
@RequestMapping("/more")
class MoreController(
    private val settingsService: SettingsService,
    private val securityService: SecurityService,
    private val playerService: PlayerService,
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService
) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest?, response: HttpServletResponse?): ModelAndView {
        val user = securityService.getCurrentUser(request)

        val musicFolders = mediaFolderService.getMusicFoldersForUser(user.username)
        val contextSupplier: Supplier<MutableMap<String, Any>> = Supplier {
            val context = settingsService.buildSpelContext()
            if (StringUtils.isNotEmpty(user.username)) {
                context["USER_NAME"] = user.username
                context["USER_MUSIC_FOLDERS"] = musicFolders.map {
                    obj -> obj.path.toString()
                }
            }
            context
        }
        val uploadsFolder = settingsService.uploadsFolder
        // The default upload pattern uses the first accessible music folder. Spring 6's
        // SpEL rejects indexing an empty list, whereas a freshly installed server has no
        // music folders yet. Keep the page usable until the administrator creates one.
        val uploadDirectory = if (musicFolders.isEmpty() && uploadsFolder?.contains("USER_MUSIC_FOLDERS") == true) ""
        else settingsService.resolveContextualString(uploadsFolder, contextSupplier)

        val player = playerService.getPlayer(request, response)
        val result = ModelAndView()

        val map: MutableMap<String, Any> = HashMap()
        result.addObject("model", map)
        map["user"] = user
        map["uploadDirectory"] = uploadDirectory
        map["genres"] = mediaFileService.getGenres(false)
        map["currentYear"] = LocalDate.now().year
        map["musicFolders"] = musicFolders
        map["clientSidePlaylist"] = player.isExternalWithPlaylist || player.isWeb
        map["brand"] = settingsService.brand
        return result
    }
}
