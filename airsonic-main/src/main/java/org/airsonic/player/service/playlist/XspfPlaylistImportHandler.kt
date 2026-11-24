package org.airsonic.player.service.playlist

import chameleon.playlist.SpecificPlaylist
import chameleon.playlist.xspf.Playlist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.SettingsService
import org.apache.commons.lang3.tuple.Pair
import java.nio.file.Path

open class XspfPlaylistImportHandler(mediaFiles: MediaFileService, settings: SettingsService) :
    PlaylistImportHandler(mediaFiles, settings) {
    override fun canHandle(playlistClass: Class<out SpecificPlaylist>): Boolean = playlistClass == Playlist::class.java

    override fun handle(inputSpecificPlaylist: SpecificPlaylist, location: Path): Pair<List<MediaFile>, List<String?>> {
        val files = ArrayList<MediaFile>()
        val errors = ArrayList<String?>()
        val playlist = inputSpecificPlaylist as Playlist
        playlist.tracks.forEach { track ->
            for (container in track.stringContainers) {
                try {
                    val uri = container.text
                    val matches = getMediaFiles(uri)
                    if (matches.isEmpty()) errors.add("Cannot find media file $uri") else files.addAll(matches)
                } catch (failure: Exception) {
                    errors.add(failure.message)
                }
            }
        }
        return Pair.of(files, errors)
    }

    override fun getOrder(): Int = 40
}
