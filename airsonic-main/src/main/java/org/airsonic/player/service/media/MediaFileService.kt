package org.airsonic.player.service.media

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Genre
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.service.CoverArtService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.airsonic.player.service.metadata.JaudiotaggerParser
import org.airsonic.player.service.metadata.MetaDataParserFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import javax.cache.Cache
import javax.cache.CacheManager

/** Facade for media-file lookup, hierarchy, metadata and catalog operations. */
class MediaFileService @JvmOverloads constructor(
    internal var securityService: SecurityService,
    internal var settingsService: SettingsService,
    internal var mediaFileDao: MediaFileDao,
    internal val mediaFolderService: MediaFolderService,
    internal var albumDao: AlbumDao,
    internal var parser: JaudiotaggerParser,
    internal var metaDataParserFactory: MetaDataParserFactory,
    internal val coverArtService: CoverArtService,
    cacheManager: CacheManager? = null,
) {
    internal val mediaFilePathCache: Cache<String, MediaFile>? =
        cacheManager?.getCache("mediaFilePathCache", String::class.java, MediaFile::class.java)
    internal val mediaFileIdCache: Cache<Int, MediaFile>? =
        cacheManager?.getCache("mediaFileIdCache", Int::class.javaObjectType, MediaFile::class.java)

    internal var memoryCacheEnabled = true
        private set

    internal val lookup by lazy { MediaFileLookup(this) }
    internal val children by lazy { MediaFileChildren(this) }
    internal val metadata by lazy { MediaFileMetadataFactory(this) }
    internal val catalog by lazy { MediaFileCatalog(this) }

    fun getMediaFile(pathName: String): MediaFile? = getMediaFile(Paths.get(pathName))

    fun getMediaFile(fullPath: Path): MediaFile? = lookup.getMediaFile(fullPath, settingsService.isFastCacheEnabled)

    fun getMediaFile(fullPath: Path, minimizeDiskAccess: Boolean): MediaFile? =
        lookup.getMediaFile(fullPath, minimizeDiskAccess)

    fun getMediaFile(relativePath: String, folderId: Int?): MediaFile? =
        getMediaFile(relativePath, mediaFolderService.getMusicFolderById(folderId)!!)

    fun getMediaFile(relativePath: String, folder: MusicFolder): MediaFile? = getMediaFile(Paths.get(relativePath), folder)

    fun getMediaFile(relativePath: Path, folder: MusicFolder): MediaFile? =
        getMediaFile(relativePath, folder, settingsService.isFastCacheEnabled)

    fun getMediaFile(relativePath: String, folderId: Int?, minimizeDiskAccess: Boolean): MediaFile? =
        getMediaFile(Paths.get(relativePath), mediaFolderService.getMusicFolderById(folderId)!!, minimizeDiskAccess)

    fun getMediaFile(relativePath: Path, folder: MusicFolder, minimizeDiskAccess: Boolean): MediaFile? =
        lookup.getMediaFile(relativePath, folder, minimizeDiskAccess)

    fun getMediaFile(id: Int): MediaFile? = lookup.getMediaFile(id)

    fun getMediaFilesByRelativePath(relativePath: Path): List<MediaFile> =
        mediaFileDao.getMediaFilesByRelativePath(relativePath.toString())

    fun getParentOf(mediaFile: MediaFile): MediaFile? = children.getParentOf(mediaFile, settingsService.isFastCacheEnabled)

    fun getParentOf(mediaFile: MediaFile, minimizeDiskAccess: Boolean): MediaFile? =
        children.getParentOf(mediaFile, minimizeDiskAccess)

    fun getChildrenOf(parent: MediaFile, includeFiles: Boolean, includeDirectories: Boolean, sort: Boolean): List<MediaFile> =
        children.getChildrenOf(parent, includeFiles, includeDirectories, sort, settingsService.isFastCacheEnabled)

    fun getChildrenOf(
        parent: MediaFile,
        includeFiles: Boolean,
        includeDirectories: Boolean,
        sort: Boolean,
        minimizeDiskAccess: Boolean,
    ): List<MediaFile> = children.getChildrenOf(parent, includeFiles, includeDirectories, sort, minimizeDiskAccess)

    fun isRoot(mediaFile: MediaFile): Boolean = children.isRoot(mediaFile)

    fun getGenres(sortByAlbum: Boolean): List<Genre> = catalog.getGenres(sortByAlbum)

    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        catalog.getMostFrequentlyPlayedAlbums(offset, count, musicFolders)

    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        catalog.getMostRecentlyPlayedAlbums(offset, count, musicFolders)

    fun getNewestAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        catalog.getNewestAlbums(offset, count, musicFolders)

    fun getStarredAlbums(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): List<MediaFile> =
        catalog.getStarredAlbums(offset, count, username, musicFolders)

    fun getAlphabeticalAlbums(
        offset: Int,
        count: Int,
        byArtist: Boolean,
        musicFolders: List<MusicFolder>,
    ): List<MediaFile> = catalog.getAlphabeticalAlbums(offset, count, byArtist, musicFolders)

    fun getAlbumsByYear(
        offset: Int,
        count: Int,
        fromYear: Int,
        toYear: Int,
        musicFolders: List<MusicFolder>,
    ): List<MediaFile> = catalog.getAlbumsByYear(offset, count, fromYear, toYear, musicFolders)

    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, musicFolders: List<MusicFolder>): List<MediaFile> =
        catalog.getAlbumsByGenre(offset, count, genre, musicFolders)

    fun getRandomSongsForParent(parent: MediaFile, count: Int): List<MediaFile> = catalog.getRandomSongsForParent(parent, count)

    fun getRandomSongs(criteria: RandomSearchCriteria, username: String): List<MediaFile> =
        catalog.getRandomSongs(criteria, username)

    fun removeVideoFiles(files: List<MediaFile>) = catalog.removeVideoFiles(files)

    fun getMediaFileStarredDate(id: Int, username: String): Instant? = catalog.getMediaFileStarredDate(id, username)

    fun populateStarredDate(mediaFiles: List<MediaFile>, username: String) = catalog.populateStarredDate(mediaFiles, username)

    fun populateStarredDate(mediaFile: MediaFile, username: String) = catalog.populateStarredDate(mediaFile, username)

    fun includeMediaFile(candidate: MediaFile, folder: MusicFolder): Boolean = metadata.includeMediaFile(candidate, folder)

    fun includeMediaFile(candidate: Path): Boolean = metadata.includeMediaFile(candidate)

    fun refreshMediaFile(mediaFile: MediaFile, folder: MusicFolder) = metadata.refreshMediaFile(mediaFile, folder)

    fun setMemoryCacheEnabled(enabled: Boolean) {
        memoryCacheEnabled = enabled
        clearMemoryCache()
    }

    fun clearMemoryCache() {
        mediaFilePathCache?.removeAll()
        mediaFileIdCache?.removeAll()
    }

    fun getMemoryCacheEnabled(): Boolean = memoryCacheEnabled

    fun setSecurityService(securityService: SecurityService) {
        this.securityService = securityService
    }

    fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    fun setMediaFileDao(mediaFileDao: MediaFileDao) {
        this.mediaFileDao = mediaFileDao
    }

    fun getDescendantsOf(ancestor: MediaFile, sort: Boolean): List<MediaFile> = children.getDescendantsOf(ancestor, sort)

    fun setMetaDataParserFactory(metaDataParserFactory: MetaDataParserFactory) {
        this.metaDataParserFactory = metaDataParserFactory
    }

    fun updateMediaFile(mediaFile: MediaFile) = lookup.updateMediaFile(mediaFile)

    fun incrementPlayCount(file: MediaFile) = catalog.incrementPlayCount(file)

    fun getAlbumCount(musicFolders: List<MusicFolder>): Int = catalog.getAlbumCount(musicFolders)

    fun getPlayedAlbumCount(musicFolders: List<MusicFolder>): Int = catalog.getPlayedAlbumCount(musicFolders)

    fun getStarredAlbumCount(username: String, musicFolders: List<MusicFolder>): Int =
        catalog.getStarredAlbumCount(username, musicFolders)

    fun setAlbumDao(albumDao: AlbumDao) {
        this.albumDao = albumDao
    }

    fun setParser(parser: JaudiotaggerParser) {
        this.parser = parser
    }
}
