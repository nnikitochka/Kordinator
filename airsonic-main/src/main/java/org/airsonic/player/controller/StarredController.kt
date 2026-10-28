package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.CoverArtScheme
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.*
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for showing a user's starred items.
 */
@Controller
@RequestMapping("/starred")
class StarredController(
    private val playerService: PlayerService,
    private val mediaFileDao: MediaFileDao,
    private val securityService: SecurityService,
    private val settingsService: SettingsService,
    private val mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService
) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest?, response: HttpServletResponse?): ModelAndView {
        val user = securityService.getCurrentUser(request)
        val username = user.username
        val userSettings = settingsService.getUserSettings(username)
        val musicFolders = mediaFolderService.getMusicFoldersForUser(username)

        val artists = mediaFileDao.getStarredDirectories(0, Int.MAX_VALUE, username, musicFolders)
        val albums = mediaFileDao.getStarredAlbums(0, Int.MAX_VALUE, username, musicFolders)
        val files = mediaFileDao.getStarredFiles(0, Int.MAX_VALUE, username, musicFolders)
        mediaFileService.populateStarredDate(artists, username)
        mediaFileService.populateStarredDate(albums, username)
        mediaFileService.populateStarredDate(files, username)

        val songs: MutableList<MediaFile> = ArrayList()
        val videos: MutableList<MediaFile> = ArrayList()
        for (file in files) {
            (if (file.isVideo) videos else songs).add(file)
        }

        val map = mapOf(
            "user" to user,
            "partyModeEnabled" to userSettings.partyModeEnabled,
            "player" to playerService.getPlayer(request, response),
            "coverArtSize" to CoverArtScheme.MEDIUM.size,
            "artists" to artists,
            "albums" to albums,
            "songs" to songs,
            "videos" to videos
        )
        return ModelAndView("starred", "model", map)
    }
}
