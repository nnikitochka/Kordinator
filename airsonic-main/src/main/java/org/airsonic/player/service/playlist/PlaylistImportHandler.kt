package org.airsonic.player.service.playlist

import chameleon.playlist.SpecificPlaylist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.service.media.MediaFileService
import org.airsonic.player.service.SettingsService
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import java.nio.file.Path
import java.nio.file.Paths

/** Resolves playlist paths against the playlist folder, music folders and working directory. */
abstract class PlaylistImportHandler(
    protected val mediaFileService: MediaFileService,
    protected val settingsService: SettingsService,
) {
    open fun getOrder(): Int = Int.MAX_VALUE
    abstract fun canHandle(playlistClass: Class<out SpecificPlaylist>): Boolean
    abstract fun handle(inputSpecificPlaylist: SpecificPlaylist, location: Path): Pair<List<MediaFile>, List<String?>>

    protected fun getMediaFiles(pathInPlaylist: String?): List<MediaFile> {
        if (!StringUtils.isNotBlank(pathInPlaylist)) return emptyList()
        val path = Paths.get(pathInPlaylist)
        if (path.isAbsolute) return mediaFileService.getMediaFile(path)?.let(::listOf) ?: emptyList()

        val matches = ArrayList<MediaFile>()
        settingsService.playlistFolder?.let { folder ->
            mediaFileService.getMediaFile(Paths.get(folder).resolve(path).normalize())?.let(matches::add)
        }
        matches.addAll(mediaFileService.getMediaFilesByRelativePath(path).filter {
            it.mediaType != MediaFile.MediaType.DIRECTORY && it.mediaType != MediaFile.MediaType.ALBUM
        })
        mediaFileService.getMediaFile(Paths.get(".").toAbsolutePath().resolve(path).normalize())?.let(matches::add)
        return matches
    }
}
