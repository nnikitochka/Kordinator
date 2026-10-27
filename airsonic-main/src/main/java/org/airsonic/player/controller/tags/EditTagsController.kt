package org.airsonic.player.controller.tags

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.service.metadata.MetaDataParser
import org.apache.commons.io.FilenameUtils
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for the page used to edit MP3 tags.
 */
@Controller
@RequestMapping("/editTags")
class EditTagsController(private val mediaFileService: MediaFileService) {
    @GetMapping
    @Throws(Exception::class)
    protected fun handleRequestInternal(request: HttpServletRequest, response: HttpServletResponse?): ModelAndView {
        val id = ServletRequestUtils.getRequiredIntParameter(request, "id")
        val dir = mediaFileService.getMediaFile(id)
        val files = mediaFileService.getChildrenOf(dir, true, false, true, false)

        val map = buildMap {
            if (files.isNotEmpty()) {
                put("defaultArtist", files[0].artist)
                put("defaultAlbum", files[0].albumName)
                put("defaultYear", files[0].year)
                put("defaultGenre", files[0].genre)
            }
            put("allGenres", JaudiotaggerParser.getID3V1Genres())

            val songs: MutableList<Song> = ArrayList()
            files.forEachIndexed { index, file ->
                songs.add(Song(
                    id = file.id,
                    fileName = FilenameUtils.getBaseName(file.path),
                    track = file.trackNumber,
                    suggestedTrack = index + 1,
                    title = file.title,
                    suggestedTitle = MetaDataParser.guessTitle(file.relativePath),
                    artist = file.artist,
                    album = file.albumName,
                    year = file.year,
                    genre = file.genre
                ))
            }

            put("id", id)
            put("songs", songs)
        }

        return ModelAndView("editTags", "model", map)
    }
}
