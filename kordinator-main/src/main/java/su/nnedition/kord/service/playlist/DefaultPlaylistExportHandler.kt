package su.nnedition.kord.service.playlist

import chameleon.content.Content
import chameleon.playlist.Media
import chameleon.playlist.Playlist
import chameleon.playlist.SpecificPlaylist
import chameleon.playlist.SpecificPlaylistProvider
import su.nnedition.kord.dao.MediaFileDao
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.util.StringUtil

open class DefaultPlaylistExportHandler(
    private val mediaFileDao: MediaFileDao,
    private val mediaFolderService: MediaFolderService,
) : PlaylistExportHandler {
    override fun canHandle(providerClass: Class<out SpecificPlaylistProvider>): Boolean = true

    override fun handle(id: Int, provider: SpecificPlaylistProvider): SpecificPlaylist =
        provider.toSpecificPlaylist(createChameleonGenericPlaylistFromDBId(id))

    internal fun createChameleonGenericPlaylistFromDBId(id: Int): Playlist {
        val playlist = Playlist()
        mediaFileDao.getFilesInPlaylist(id).forEach { file ->
            val folder = mediaFolderService.getMusicFolderById(file.folderId)!!
            val content = Content(file.getFullPath(folder.path).toString())
            file.duration?.let { content.duration = (it * 1000).toLong() }
            file.fileSize?.let { content.length = it }
            file.changed?.let { content.lastModified = it.toEpochMilli() }
            file.height?.let { content.height = it }
            file.width?.let { content.width = it }
            file.format?.let { content.type = StringUtil.getMimeType(it) }
            val component = Media()
            component.source = content
            playlist.rootSequence.addComponent(component)
        }
        return playlist
    }

    override val order: Int get() = Int.MAX_VALUE
}
