package org.airsonic.player.service

import chameleon.playlist.SpecificPlaylist
import chameleon.playlist.SpecificPlaylistFactory
import chameleon.playlist.SpecificPlaylistProvider
import org.airsonic.player.cache.CacheHelper
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.dao.PlaylistDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.PlayQueue
import org.airsonic.player.domain.Playlist
import org.airsonic.player.domain.User
import org.airsonic.player.service.playlist.PlaylistExportHandler
import org.airsonic.player.service.playlist.PlaylistImportHandler
import org.airsonic.player.util.StringUtil
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.WatchEvent
import java.time.Instant
import java.util.function.BiConsumer
import javax.cache.Cache
import javax.cache.CacheManager

/** Loads and persists playlists and watches their configured import directory. */
class PlaylistService(
    mediaFileDao: MediaFileDao,
    playlistDao: PlaylistDao,
    private val securityService: SecurityService,
    private val settingsService: SettingsService,
    exportHandlers: List<PlaylistExportHandler>,
    importHandlers: List<PlaylistImportHandler>,
    private val pathWatcherService: PathWatcherService,
    cacheManager: CacheManager?
) {
    private val mediaFileDao = mediaFileDao
    private val playlistDao = playlistDao
    private val exportHandlers = exportHandlers.sortedBy { it.order }
    private val importHandlers = importHandlers.sortedBy { it.getOrder() }
    private val playlistCache: Cache<Int, Playlist>? = cacheManager?.getCache("playlistCache", Int::class.javaObjectType, Playlist::class.java)
    private val playlistUsersCache: Cache<Int, Any>? = cacheManager?.getCache("playlistUsersCache", Int::class.javaObjectType, Any::class.java)

    constructor(
        mediaFileDao: MediaFileDao,
        playlistDao: PlaylistDao,
        securityService: SecurityService,
        settingsService: SettingsService,
        exportHandlers: List<PlaylistExportHandler>,
        importHandlers: List<PlaylistImportHandler>,
        pathWatcherService: PathWatcherService
    ) : this(
        mediaFileDao, playlistDao, securityService, settingsService,
        exportHandlers, importHandlers, pathWatcherService, null
    )

    private val playlistModified = BiConsumer<Path, WatchEvent<Path>> { directory, event ->
        importPlaylist(directory.resolve(event.context()), playlistDao.getAllPlaylists())
    }

    @Throws(IOException::class)
    fun init() {
        addPlaylistFolderWatcher()
    }

    fun addPlaylistFolderWatcher() {
        val playlistFolder = Paths.get(settingsService.getPlaylistFolder())
        if (Files.exists(playlistFolder) && Files.isDirectory(playlistFolder)) {
            try {
                val ignoreEvent = BiConsumer<Path, WatchEvent<Path>> { _, _ -> }
                pathWatcherService.setWatcher("Playlist folder watcher", playlistFolder, playlistModified, ignoreEvent, playlistModified, ignoreEvent)
            } catch (_: Exception) {
                log.warn("Issues setting watcher for folder: {}", playlistFolder)
            }
        }
    }

    val allPlaylists: List<Playlist> get() = playlistDao.getAllPlaylists()

    fun getReadablePlaylistsForUser(username: String): List<Playlist> =
        playlistDao.getReadablePlaylistsForUser(username)

    fun getWritablePlaylistsForUser(username: String): List<Playlist> {
        if (securityService.isAdmin(username)) return getReadablePlaylistsForUser(username)
        return playlistDao.getWritablePlaylistsForUser(username)
    }

    fun getPlaylist(id: Int): Playlist? = CacheHelper.getOrCompute(playlistCache, id) { key -> playlistDao.getPlaylist(key!!) }

    @Suppress("UNCHECKED_CAST")
    fun getPlaylistUsers(playlistId: Int): List<String> = CacheHelper.getOrCompute(
        playlistUsersCache, playlistId
    ) { key -> playlistDao.getPlaylistUsers(key!!) } as List<String>

    fun getFilesInPlaylist(id: Int): List<MediaFile> = getFilesInPlaylist(id, false)

    fun getFilesInPlaylist(id: Int, includeNotPresent: Boolean): List<MediaFile> =
        mediaFileDao.getFilesInPlaylist(id).filter { includeNotPresent || it.isPresent }

    fun setFilesInPlaylist(id: Int, files: List<MediaFile>) {
        playlistDao.setFilesInPlaylist(id, files)
        refreshPlaylistStats(id)
    }

    fun refreshPlaylistsStats() {
        allPlaylists.forEach { refreshPlaylistStats(it.id) }
    }

    fun refreshPlaylistStats(id: Int) {
        val playlist = Playlist(getPlaylist(id)!!)
        val stats: Pair<Int, Double> = playlistDao.getPlaylistFileStats(id)!!
        playlist.fileCount = stats.left
        playlist.duration = stats.right
        updatePlaylist(playlist)
    }

    fun createPlaylist(playlist: Playlist) {
        playlistDao.createPlaylist(playlist)
    }

    fun addPlaylistUser(playlist: Playlist, username: String) {
        playlistUsersCache?.remove(playlist.id)
        playlistDao.addPlaylistUser(playlist.id, username)
    }

    fun deletePlaylistUser(playlist: Playlist, username: String) {
        playlistUsersCache?.remove(playlist.id)
        playlistDao.deletePlaylistUser(playlist.id, username)
    }

    fun isReadAllowed(playlist: Playlist, username: String?): Boolean {
        if (username == null) return false
        if (username == playlist.username || playlist.shared) return true
        return getPlaylistUsers(playlist.id).contains(username)
    }

    fun isWriteAllowed(playlist: Playlist, username: String?): Boolean = username != null && username == playlist.username

    fun deletePlaylist(id: Int) {
        playlistCache?.remove(id)
        playlistDao.deletePlaylist(id)
    }

    fun updatePlaylist(playlist: Playlist) {
        playlistCache?.remove(playlist.id)
        playlistDao.updatePlaylist(playlist)
    }

    fun getExportPlaylistExtension(): String {
        val format = settingsService.getPlaylistExportFormat()
        val provider = SpecificPlaylistFactory.getInstance().findProviderById(format)
        return provider.contentTypes[0].extensions[0]
    }

    @Throws(Exception::class)
    fun exportPlaylist(id: Int, out: OutputStream) {
        val format = settingsService.getPlaylistExportFormat()
        val provider = SpecificPlaylistFactory.getInstance().findProviderById(format)
        val specificPlaylist = exportHandler(provider).handle(id, provider)
        specificPlaylist.writeTo(out, StringUtil.ENCODING_UTF8)
    }

    private fun importHandler(playlist: SpecificPlaylist): PlaylistImportHandler = importHandlers
        .firstOrNull { it.canHandle(playlist.javaClass) }
        ?: throw RuntimeException("No import handler for ${playlist.javaClass.name}")

    private fun exportHandler(provider: SpecificPlaylistProvider): PlaylistExportHandler = exportHandlers
        .firstOrNull { it.canHandle(provider.javaClass) }
        ?: throw RuntimeException("No export handler for ${provider.javaClass.name}")

    fun importPlaylists() {
        try {
            log.info("Starting playlist import.")
            doImportPlaylists()
            log.info("Completed playlist import.")
        } catch (failure: Throwable) {
            log.warn("Failed to import playlists: {}", failure.toString(), failure)
        }
    }

    private fun doImportPlaylists() {
        val folderPath = settingsService.getPlaylistFolder() ?: return
        val playlistFolder = Paths.get(folderPath)
        if (!Files.exists(playlistFolder)) return
        val all = playlistDao.getAllPlaylists()
        try {
            Files.walk(playlistFolder).use { it.forEach { path -> importPlaylist(path, all) } }
        } catch (failure: IOException) {
            log.warn("Error while reading directory {} when importing playlists", playlistFolder, failure)
        }
    }

    private fun importPlaylist(file: Path, allPlaylists: List<Playlist>) {
        if (Files.isRegularFile(file) && Files.isReadable(file)) {
            try {
                val fileName = file.fileName.toString()
                val existing = allPlaylists.firstOrNull { fileName == it.importedFrom }
                importPlaylistIfUpdated(file, existing)
            } catch (failure: Exception) {
                log.warn("Failed to auto-import playlist {}", file, failure)
            }
        }
    }

    @Throws(Exception::class)
    fun importPlaylist(
        username: String,
        playlistName: String,
        fileName: String,
        file: Path,
        inputStream: InputStream,
        existingPlaylist: Playlist?
    ): Playlist {
        val inputPlaylist = SpecificPlaylistFactory.getInstance().readFrom(inputStream, "UTF-8")
            ?: throw Exception("Unsupported playlist $fileName")
        val handler = importHandler(inputPlaylist)
        log.debug("Using {} playlist import handler", handler.javaClass.simpleName)
        val (files, errors) = handler.handle(inputPlaylist, file)
        if (files.isEmpty() && errors.isNotEmpty()) throw Exception("No songs in the playlist were found.")
        errors.forEach { log.warn("File in playlist '{}' not found: {}", fileName, it) }

        val now = Instant.now()
        val playlist = if (existingPlaylist == null) {
            Playlist().apply {
                this.username = username
                this.created = now
                this.changed = now
                this.shared = true
                this.name = playlistName
                this.comment = "Auto-imported from $fileName"
                this.importedFrom = fileName
            }.also(::createPlaylist)
        } else {
            existingPlaylist
        }
        setFilesInPlaylist(playlist.id, files)
        return playlist
    }

    private fun importPlaylistIfUpdated(file: Path, existingPlaylist: Playlist?) {
        val fileName = file.fileName.toString()
        if (existingPlaylist != null && Files.getLastModifiedTime(file).toMillis() <= existingPlaylist.changed!!.toEpochMilli()) return
        Files.newInputStream(file).use { input ->
            importPlaylist(User.USERNAME_ADMIN, FilenameUtils.getBaseName(fileName), fileName, file, input, existingPlaylist)
            log.info("Auto-imported playlist {}", file)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PlaylistService::class.java)

        @JvmStatic
        fun notNull(value: Any?, message: String) {
            if (value == null) throw IllegalArgumentException(message)
        }
    }
}
