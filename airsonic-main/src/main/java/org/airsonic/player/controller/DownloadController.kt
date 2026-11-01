package org.airsonic.player.controller

import org.airsonic.player.service.DownloadService
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.util.StringUtil
import org.apache.commons.io.FilenameUtils
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.context.request.ServletWebRequest
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.util.*

@Controller
@RequestMapping("/download")
class DownloadController(
    private val playerService: PlayerService,
    private val securityService: SecurityService,
    private val downloads: DownloadService
) {
    @GetMapping
    @Throws(Exception::class)
    fun handleRequest(
        principal: Principal,
        @RequestParam
        id: Optional<Int?>,
        @RequestParam(required = false)
        playlist: Int?,
        @RequestParam(required = false)
        player: Int?,
        @RequestParam(required = false, name = "i")
        indices: MutableList<Int>?,
        request: ServletWebRequest
    ): ResponseEntity<Resource>? {
        val user = securityService.getUserByName(principal.name)
        val transferPlayer = playerService.getPlayer(request.request, request.response, false, false)
        val download = downloads.prepare(user, transferPlayer, id, playlist, player, indices)
        if (download == null || request.checkNotModified(
                download.size.toString(),
                download.changed
            )
        ) return null

        val headers = HttpHeaders()
        headers.contentDisposition = ContentDisposition.builder("attachment")
            .filename(download.filename, StandardCharsets.UTF_8).build()
        headers.contentType = MediaType.parseMediaType(StringUtil.getMimeType(FilenameUtils.getExtension(download.filename)))

        return ResponseEntity.ok()
            .headers(headers)
            .body(if (download.hasContent()) DownloadResource(download) else null)
    }
}
