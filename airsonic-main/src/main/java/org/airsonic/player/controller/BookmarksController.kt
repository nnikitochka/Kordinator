package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.service.SettingsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

@Controller
@RequestMapping("/bookmarks")
class BookmarksController(private val settingsService: SettingsService) {
    @GetMapping
    fun doGet(user: Principal, request: HttpServletRequest?): ModelAndView {
        val map = mapOf(
            "initialPaginationSize" to settingsService.getUserSettings(user.name).paginationSizeBookmarks
        )

        return ModelAndView("bookmarks", "model", map)
    }
}
