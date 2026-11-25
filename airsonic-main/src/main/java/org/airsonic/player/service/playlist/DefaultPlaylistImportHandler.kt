package org.airsonic.player.service.playlist

import chameleon.playlist.BasePlaylistVisitor
import chameleon.playlist.Media
import chameleon.playlist.SpecificPlaylist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.SettingsService
import org.apache.commons.lang3.tuple.Pair
import java.nio.file.Path

open class DefaultPlaylistImportHandler(mediaFiles: MediaFileService, settings: SettingsService) :
    PlaylistImportHandler(mediaFiles, settings) {
    override fun canHandle(playlistClass: Class<out SpecificPlaylist>): Boolean = true

    override fun handle(inputSpecificPlaylist: SpecificPlaylist, location: Path): Pair<List<MediaFile>, List<String?>> {
        val files = ArrayList<MediaFile>()
        val errors = ArrayList<String?>()
        try {
            inputSpecificPlaylist.toPlaylist().acceptDown(object : BasePlaylistVisitor() {
                override fun beginVisitMedia(media: Media) {
                    try {
                        val uri = media.source.toString()
                        val matches = getMediaFiles(uri)
                        if (matches.isEmpty()) errors.add("Cannot find media file $uri") else files.addAll(matches)
                    } catch (failure: Exception) {
                        errors.add(failure.message)
                    }
                }
            })
        } catch (failure: Exception) {
            errors.add(failure.message)
        }
        return Pair.of(files, errors)
    }

    override fun getOrder(): Int = Int.MAX_VALUE
}
