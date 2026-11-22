package org.airsonic.player.service.search

import org.airsonic.player.dao.AlbumDao
import org.airsonic.player.dao.ArtistDao
import org.airsonic.player.dao.MediaFileDao
import org.airsonic.player.domain.Album
import org.airsonic.player.domain.Artist
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MediaLibraryStatistics
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.FileUtil
import org.airsonic.player.util.Util
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexCommit
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.regex.Pattern
import java.util.stream.Collectors

/** Owns Lucene index writers, searchers, and on-disk index lifecycle. */
class IndexManager(
    private val analyzerFactory: AnalyzerFactory,
    private val documentFactory: DocumentFactory,
    private val mediaFileDao: MediaFileDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao
) {
    private var rootIndexDirectory: Path = SettingsService.getAirsonicHome().resolve(INDEX_ROOT_DIR_NAME + INDEX_VERSION)
    private val getIndexDirectory = Function<IndexType, Path> { rootIndexDirectory.resolve(it.toString().lowercase()) }
    private val searchers = ConcurrentHashMap<IndexType, SearcherManager>()
    private val writers = ConcurrentHashMap<IndexType, IndexWriter>()

    fun index(album: Album) {
        val primaryKey = documentFactory.createPrimarykey(album)
        val document = documentFactory.createAlbumId3Document(album)
        try {
            writers[IndexType.ALBUM_ID3]!!.updateDocument(primaryKey, document)
        } catch (error: Exception) {
            LOG.error("Failed to create search index for {}", album, error)
        }
    }

    fun index(artist: Artist, musicFolder: MusicFolder) {
        val primaryKey = documentFactory.createPrimarykey(artist)
        val document = documentFactory.createArtistId3Document(artist, musicFolder)
        try {
            writers[IndexType.ARTIST_ID3]!!.updateDocument(primaryKey, document)
        } catch (error: Exception) {
            LOG.error("Failed to create search index for {}", artist, error)
        }
    }

    fun index(mediaFile: MediaFile, musicFolder: MusicFolder) {
        val primaryKey = documentFactory.createPrimarykey(mediaFile)
        try {
            when {
                mediaFile.isFile -> writers[IndexType.SONG]!!.updateDocument(
                    primaryKey,
                    documentFactory.createSongDocument(mediaFile, musicFolder)
                )
                mediaFile.isAlbum -> writers[IndexType.ALBUM]!!.updateDocument(
                    primaryKey,
                    documentFactory.createAlbumDocument(mediaFile, musicFolder)
                )
                else -> writers[IndexType.ARTIST]!!.updateDocument(
                    primaryKey,
                    documentFactory.createArtistDocument(mediaFile, musicFolder)
                )
            }
        } catch (error: Exception) {
            LOG.error("Failed to create search index for {}", mediaFile, error)
        }
    }

    fun startIndexing() {
        EnumSet.allOf(IndexType::class.java).parallelStream().forEach { type ->
            try {
                writers[type] = createIndexWriter(type)
            } catch (error: IOException) {
                LOG.error("Failed to create search index for {}", type, error)
            }
        }
    }

    @Throws(IOException::class)
    private fun createIndexWriter(indexType: IndexType): IndexWriter {
        val indexDirectory = getIndexDirectory.apply(indexType)
        val config = IndexWriterConfig(analyzerFactory.getAnalyzer())
        return IndexWriter(FSDirectory.open(indexDirectory), config)
    }

    fun expunge() {
        deleteDocuments(IndexType.ARTIST, mediaFileDao.getArtistExpungeCandidates().map(documentFactory::createPrimarykey), "artist")
        deleteDocuments(IndexType.ALBUM, mediaFileDao.getAlbumExpungeCandidates().map(documentFactory::createPrimarykey), "album")
        deleteDocuments(IndexType.SONG, mediaFileDao.getSongExpungeCandidates().map(documentFactory::createPrimarykey), "song")
        deleteDocuments(IndexType.ARTIST_ID3, artistDao.getExpungeCandidates().map(documentFactory::createPrimarykey), "artistId3")
        deleteDocuments(IndexType.ALBUM_ID3, albumDao.getExpungeCandidates().map(documentFactory::createPrimarykey), "albumId3")
    }

    private fun deleteDocuments(indexType: IndexType, primaryKeys: List<Term>, entityName: String) {
        try {
            writers[indexType]!!.deleteDocuments(*primaryKeys.toTypedArray())
        } catch (error: IOException) {
            LOG.error("Failed to delete {} doc.", entityName, error)
        }
    }

    /** Closes all writers and refreshes their corresponding searchers after a scan. */
    fun stopIndexing(statistics: MediaLibraryStatistics) {
        EnumSet.allOf(IndexType::class.java).parallelStream().forEach { type -> stopIndexing(type, statistics) }
    }

    private fun stopIndexing(type: IndexType, statistics: MediaLibraryStatistics) {
        writers.computeIfPresent(type) { mapType, writer ->
            try {
                writer.use {
                    val userData = Util.objectToStringMap(statistics)
                    it.setLiveCommitData(userData.entries)
                    val updated = -1L != it.commit()
                    LOG.trace("Success to create or update search index : [{}]", mapType)
                    if (updated) {
                        searchers.computeIfPresent(mapType) { searcherType, manager ->
                            try {
                                manager.maybeRefresh()
                                LOG.trace("SearcherManager has been refreshed : [{}]", searcherType)
                                manager
                            } catch (error: IOException) {
                                LOG.error("Failed to refresh SearcherManager : [{}]", searcherType, error)
                                null
                            }
                        }
                    }
                }
            } catch (error: IOException) {
                LOG.error("Failed to create search index for {}.", mapType, error)
            }
            null
        }
    }

    /** Returns committed library statistics only when all indexes agree. */
    fun getStatistics(): MediaLibraryStatistics? {
        val stats: Set<MediaLibraryStatistics?> = EnumSet.allOf(IndexType::class.java).parallelStream()
            .map { type ->
                val searcher = getSearcher(type)
                if (searcher == null) {
                    LOG.trace("No index for type {}", type)
                    return@map null
                }
                val reader = searcher.indexReader
                if (reader !is DirectoryReader) {
                    LOG.warn("Unexpected index type {} for {}", reader.javaClass, type)
                    return@map null
                }
                try {
                    Util.stringMapToValidObject(MediaLibraryStatistics::class.java, reader.indexCommit.userData)
                } catch (error: IOException) {
                    LOG.debug("Exception encountered while fetching index commit data for {}", type, error)
                    null
                } catch (error: IllegalArgumentException) {
                    LOG.debug("Exception encountered while fetching index commit data for {}", type, error)
                    null
                }
            }
            .distinct()
            .collect(Collectors.toSet())

        if (stats.size > 1) {
            LOG.warn(
                "Differing stats data for different indices: {}",
                stats.stream().map { Util.objectToStringMap(it) }.collect(Collectors.toSet())
            )
            return null
        }
        return stats.stream().map { Optional.ofNullable(it) }.findAny().flatMap { it }.orElse(null)
    }

    /** Returns an acquired searcher, or null when an index has not been built yet. */
    fun getSearcher(indexType: IndexType): IndexSearcher? {
        val manager = searchers[indexType] ?: synchronized(searchers) {
            searchers[indexType] ?: createSearcherManager(indexType)?.also { searchers[indexType] = it }
        } ?: return null
        return try {
            manager.acquire()
        } catch (error: IOException) {
            LOG.warn("Failed to acquire IndexSearcher for {}.", indexType, error)
            null
        }
    }

    private fun createSearcherManager(indexType: IndexType): SearcherManager? {
        val indexDirectory = getIndexDirectory.apply(indexType)
        return try {
            if (Files.exists(indexDirectory)) {
                SearcherManager(FSDirectory.open(indexDirectory), null)
            } else {
                LOG.warn("{} does not exist. Please run a scan.", indexDirectory.toString())
                null
            }
        } catch (error: IOException) {
            LOG.error("Failed to initialize SearcherManager for {}", indexType, error)
            null
        }
    }

    fun release(indexType: IndexType, indexSearcher: IndexSearcher) {
        searchers.compute(indexType) { type, manager ->
            if (manager == null) {
                try {
                    indexSearcher.indexReader.close()
                } catch (error: Exception) {
                    LOG.warn("Failed to release {} because it wasn't found. IndexSearcher has been closed.", type, error)
                }
                return@compute null
            }
            try {
                manager.release(indexSearcher)
                manager
            } catch (error: IOException) {
                LOG.error("Failed to release IndexSearcher for {}.", type, error)
                null
            }
        }
    }

    @Throws(IOException::class)
    fun deleteOldIndexFiles() {
        Files.list(SettingsService.getAirsonicHome()).use { files ->
            files.filter { LEGACY_INDEX_PATTERN.matcher(it.fileName.toString()).matches() }
                .forEach { path ->
                    LOG.info("Found legacy index file. Try to delete : {}", path)
                    FileUtil.delete(path)
                }
        }
        Files.list(SettingsService.getAirsonicHome()).use { files ->
            files.filter { NON_CURRENT_INDEX_PATTERN.matcher(it.fileName.toString()).matches() }
                .filter { it != rootIndexDirectory }
                .forEach { path ->
                    LOG.info("Found old index file. Try to delete : {}", path)
                    FileUtil.delete(path)
                }
        }
    }

    fun initializeIndexDirectory() {
        if (Files.exists(rootIndexDirectory)) {
            LOG.info("Index was found (index version {}). ", INDEX_VERSION)
        } else {
            try {
                Files.createDirectories(rootIndexDirectory)
                LOG.info("Index directory was created (index version {}). ", INDEX_VERSION)
            } catch (error: IOException) {
                LOG.warn("Failed to create index directory :  (index version {}). ", INDEX_VERSION, error)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(IndexManager::class.java)
        private const val INDEX_VERSION = 19
        private const val INDEX_ROOT_DIR_NAME = "index"
        private val LEGACY_INDEX_PATTERN: Pattern = Pattern.compile("^lucene\\d+$")
        private val NON_CURRENT_INDEX_PATTERN: Pattern = Pattern.compile("^index\\d+$")
    }
}
