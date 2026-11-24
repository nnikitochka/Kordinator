package org.airsonic.player.service.playlist

import chameleon.playlist.SpecificPlaylist
import chameleon.playlist.SpecificPlaylistProvider
import chameleon.playlist.xspf.Location
import chameleon.playlist.xspf.Playlist as XspfPlaylist
import chameleon.playlist.xspf.Track
import chameleon.playlist.xspf.XspfProvider
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.domain.CoverArt.EntityType
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.MediaFolderService
import java.time.Instant
import java.util.Date

open class XspfPlaylistExportHandler(
    private val mediaFileDao: MediaFileDao,
    private val playlistDao: PlaylistDao,
    private val mediaFolderService: MediaFolderService,
    private val coverArtService: CoverArtService,
) : PlaylistExportHandler {
    override fun canHandle(providerClass: Class<out SpecificPlaylistProvider>): Boolean = providerClass == XspfProvider::class.java

    override fun handle(id: Int, provider: SpecificPlaylistProvider): SpecificPlaylist = createXsfpPlaylistFromDBId(id)

    internal fun createXsfpPlaylistFromDBId(id: Int): XspfPlaylist {
        val output = XspfPlaylist()
        val playlist = playlistDao.getPlaylist(id)!!
        output.title = playlist.name
        output.creator = "Airsonic user ${playlist.username}"
        output.date = Date.from(Instant.now())
        mediaFileDao.getFilesInPlaylist(id).forEach { mediaFile ->
            val folder = mediaFolderService.getMusicFolderById(mediaFile.folderId)
            val track = Track()
            track.trackNumber = mediaFile.trackNumber
            track.creator = mediaFile.artist
            track.title = mediaFile.title
            track.album = mediaFile.albumName
            track.duration = Math.round(mediaFile.duration!!).toInt()
            track.image = coverArtService.getFullPath(EntityType.MEDIA_FILE, mediaFile.id!!)?.toString()
            val location = Location()
            location.text = mediaFile.getFullPath(folder.path).toString()
            track.stringContainers.add(location)
            output.addTrack(track)
        }
        return output
    }

    override val order: Int get() = 0
}
