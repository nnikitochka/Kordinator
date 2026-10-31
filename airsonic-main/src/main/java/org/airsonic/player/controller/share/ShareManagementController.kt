package org.airsonic.player.controller.share

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.*
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for sharing music on Twitter, Facebook etc.
 */
@Controller
@RequestMapping("/createShare")
class ShareManagementController(
    private val mediaFileService: MediaFileService,
    private val shareService: ShareService,
    private val playerService: PlayerService,
    private val playlistService: PlaylistService,
    private val securityService: SecurityService
) {
    @GetMapping
    @Throws(Exception::class)
    fun createShare(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val files = getMediaFiles(request)
        var dir: MediaFile? = null
        if (files.isNotEmpty()) {
            dir = files.first()
            if (!dir.isAlbum) {
                dir = mediaFileService.getParentOf(dir)
            }
        }

        val map = buildMap {
            put("dir", dir)
            put("user", securityService.getCurrentUser(request))

            val share = shareService.createShare(request, files)
            val description = getDescription(request)
            if (description != null) {
                share.description = description
                shareService.updateShare(share)
            }

            put("playUrl", shareService.getShareUrl(request, share))
        }

        return ModelAndView("createShare", "model", map)
    }

    @Throws(ServletRequestBindingException::class)
    private fun getDescription(request: HttpServletRequest): String? {
        val playlistId = ServletRequestUtils.getIntParameter(request, "playlist")
        return if (playlistId == null) null else playlistService.getPlaylist(playlistId).name
    }

    @Throws(Exception::class)
    private fun getMediaFiles(request: HttpServletRequest): MutableList<MediaFile> {
        val id = ServletRequestUtils.getIntParameter(request, "id")
        val playerId = ServletRequestUtils.getIntParameter(request, "player")
        val playlistId = ServletRequestUtils.getIntParameter(request, "playlist")

        var result: MutableList<MediaFile> = ArrayList()

        if (id != null) {
            val album = mediaFileService.getMediaFile(id)
            val indexes = ServletRequestUtils.getIntParameters(request, "i")
            if (indexes.size == 0) {
                return mutableListOf(album)
            }
            val children = mediaFileService.getChildrenOf(album, true, false, true)
            for (index in indexes) {
                result.add(children[index])
            }
        } else if (playerId != null) {
            val player = playerService.getPlayerById(playerId)
            val playQueue = player.playQueue
            result = playQueue.files
        } else if (playlistId != null) {
            result = playlistService.getFilesInPlaylist(playlistId)
        }

        return result
    }
}
