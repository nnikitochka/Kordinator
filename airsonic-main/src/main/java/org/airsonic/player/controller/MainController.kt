package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.Util
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the main page.
 * 
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/main")
class MainController(private val securityService: SecurityService, private val settingsService: SettingsService) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(
        request: HttpServletRequest,
        response: HttpServletResponse?
    ): ModelAndView {
        val username = securityService.getCurrentUsername(request)
        val userSettings = settingsService.getUserSettings(username)

        val map = buildMap {
            put("coverArtSizeMedium", CoverArtScheme.MEDIUM.size)
            put("coverArtSizeLarge", CoverArtScheme.LARGE.size)
            put("user", securityService.getCurrentUser(request))
            put("visibility", userSettings.mainVisibility)
            put("showAlbumYear", settingsService.isSortAlbumsByYear)
            put("showArtistInfo", userSettings.showArtistInfoEnabled)
            put("partyMode", userSettings.partyModeEnabled)
            put("brand", settingsService.brand)
            put("viewAsList", userSettings.viewAsList)
            put("initialPaginationSizeFiles", userSettings.paginationSizeFiles)
            put("initialPaginationSizeFolders", userSettings.paginationSizeFolders)
            put("initialPathsJSON", Util.toJson(
                ServletRequestUtils.getStringParameters(request, "path")
                    .map { StringUtils.split(it, ":", 2) }
                    .groupBy { it[0] }
                    .mapValues { (_, values) ->
                        values.map { it.getOrNull(1) ?: "" }.toSet()
                    }
            ))

            put("initialIdsJSON", Util.toJson(ServletRequestUtils.getIntParameters(request, "id")))
        }

        return ModelAndView("mediaMain", "model", map)
    }
}
