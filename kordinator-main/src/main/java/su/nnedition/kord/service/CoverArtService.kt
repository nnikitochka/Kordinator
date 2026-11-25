package su.nnedition.kord.service

import de.androidpit.colorthief.ColorThief
import su.nnedition.kord.cache.CacheHelper
import su.nnedition.kord.dao.CoverArtDao
import su.nnedition.kord.domain.Album
import su.nnedition.kord.domain.Artist
import su.nnedition.kord.domain.CoverArt
import su.nnedition.kord.domain.CoverArt.EntityType
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.service.media.MediaFolderService
import su.nnedition.kord.service.metadata.JaudiotaggerParser
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import javax.cache.Cache
import javax.cache.CacheManager
import javax.imageio.ImageIO

open class CoverArtService {
    private val coverArtDao: CoverArtDao
    private val mediaFolderService: MediaFolderService
    private val jaudiotaggerParser: JaudiotaggerParser
    private val coverArtCache: Cache<String, CoverArt>?

    constructor(coverArtDao: CoverArtDao, mediaFolderService: MediaFolderService, jaudiotaggerParser: JaudiotaggerParser) :
        this(coverArtDao, mediaFolderService, jaudiotaggerParser, null)

    constructor(
        coverArtDao: CoverArtDao,
        mediaFolderService: MediaFolderService,
        jaudiotaggerParser: JaudiotaggerParser,
        cacheManager: CacheManager?
    ) {
        this.coverArtDao = coverArtDao
        this.mediaFolderService = mediaFolderService
        this.jaudiotaggerParser = jaudiotaggerParser
        this.coverArtCache = cacheManager?.getCache("coverArtCache", String::class.java, CoverArt::class.java)
    }

    open fun upsert(type: EntityType, id: Int, path: String, folderId: Int?, overridden: Boolean) {
        upsert(CoverArt(id, type, path, folderId, overridden))
    }

    open fun upsert(art: CoverArt) {
        val entityType = art.entityType
        if (entityType != null) coverArtCache?.remove(cacheKey(entityType, art.entityId))
        if (art.accentColor == null) {
            val existing = coverArtDao.get(art.entityType, art.entityId)
            if (existing != null && art.path == existing.path && existing.accentColor != null) {
                // Same image already processed, reuse the stored color.
                art.accentColor = existing.accentColor
            } else {
                art.accentColor = computeAccentColor(art)
            }
        }
        coverArtDao.upsert(art)
    }

    /** Returns the dominant (accent) color of the cover art, or {@code null} if unknown. */
    open fun getAccentColor(type: EntityType, id: Int): String? = get(type, id).accentColor

    /**
     * Computes and stores the accent color for every cover art row that still has none.
     * Needed to backfill colors for cover art that was persisted before the accent color
     * feature existed (such rows are not re-upserted by a regular incremental scan).
     *
     * @return the number of rows updated
     */
    open fun backfillAccentColors(): Int {
        val missing = coverArtDao.getCoverArtWithoutAccentColor()
        var count = 0
        for (art in missing) {
            val accent = computeAccentColor(art)
            if (accent != null) {
                art.accentColor = accent
                art.updated = Instant.now()
                upsert(art)
                count++
            }
        }
        if (count > 0) {
            LOG.info("Backfilled accent colors for {}/{} cover art rows", count, missing.size)
        } else {
            LOG.debug("No cover art rows without accent color to backfill ({} checked)", missing.size)
        }
        return count
    }

    private fun computeAccentColor(art: CoverArt): String? = try {
        val fullPath = getFullPath(art) ?: return null
        val image = if (jaudiotaggerParser.isApplicable(fullPath)) {
            val artwork: Artwork? = JaudiotaggerParser.getArtwork(fullPath)
            artwork?.binaryData?.let { ImageIO.read(ByteArrayInputStream(it)) }
        } else {
            ImageIO.read(fullPath.toFile())
        } ?: return null
        val rgb = ColorThief.getColor(image) ?: return null
        String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2])
    } catch (x: Exception) {
        LOG.warn("Failed to compute accent color for cover art {}", art.path, x)
        null
    }

    open fun persistIfNeeded(mediaFile: MediaFile) {
        val art = mediaFile.art
        if (art != null && art != CoverArt.NULL_ART) {
            val mediaFileId = mediaFile.id!!
            val existing = get(EntityType.MEDIA_FILE, mediaFileId)
            if (existing == CoverArt.NULL_ART || !existing.overridden) {
                art.entityId = mediaFileId
                upsert(art)
            }
            mediaFile.art = null
        }
    }

    open fun persistIfNeeded(album: Album) {
        val art = album.art
        if (art != null && art != CoverArt.NULL_ART) {
            val existing = get(EntityType.ALBUM, album.id)
            if (existing == CoverArt.NULL_ART || !existing.overridden) {
                art.entityId = album.id
                upsert(art)
            }
            album.art = null
        }
    }

    open fun persistIfNeeded(artist: Artist) {
        val art = artist.art
        if (art != null && art != CoverArt.NULL_ART) {
            val existing = get(EntityType.ARTIST, artist.id)
            if (existing == CoverArt.NULL_ART || !existing.overridden) {
                art.entityId = artist.id
                upsert(art)
            }
            artist.art = null
        }
    }

    open fun get(type: EntityType, id: Int): CoverArt {
        val key = cacheKey(type, id)
        return CacheHelper.getOrCompute(coverArtCache, key) {
            Optional.ofNullable(coverArtDao.get(type, id)).orElse(CoverArt.NULL_ART)
        }!!
    }

    open fun getFullPath(type: EntityType, id: Int): Path? = getFullPath(get(type, id))

    open fun getFullPath(art: CoverArt?): Path? {
        if (art != null && art != CoverArt.NULL_ART) {
            if (art.folderId == null) {
                // null folder ids mean absolute paths
                return art.relativePath
            }
            val folder: MusicFolder? = mediaFolderService.getMusicFolderById(art.folderId)
            if (folder != null) return art.getFullPath(folder.path)
        }
        return null
    }

    open fun delete(type: EntityType, id: Int) {
        coverArtCache?.remove(cacheKey(type, id))
        coverArtDao.delete(type, id)
    }

    open fun expunge() {
        coverArtCache?.removeAll()
        coverArtDao.expunge()
    }

    private fun cacheKey(type: EntityType, id: Int): String = "$type-$id"

    private companion object {
        val LOG = LoggerFactory.getLogger(CoverArtService::class.java)
    }
}
