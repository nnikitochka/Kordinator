package org.airsonic.player.service;

import de.androidpit.colorthief.ColorThief;
import org.airsonic.player.dao.CoverArtDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.airsonic.player.cache.CacheHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CoverArtService {
    private static final Logger LOG = LoggerFactory.getLogger(CoverArtService.class);
    final CoverArtDao coverArtDao;
    final MediaFolderService mediaFolderService;
    final JaudiotaggerParser jaudiotaggerParser;
    private final Cache<String, CoverArt> coverArtCache;

    public CoverArtService(CoverArtDao coverArtDao, MediaFolderService mediaFolderService, JaudiotaggerParser jaudiotaggerParser) {
        this(coverArtDao, mediaFolderService, jaudiotaggerParser, null);
    }

    @Autowired
    public CoverArtService(CoverArtDao coverArtDao, MediaFolderService mediaFolderService, JaudiotaggerParser jaudiotaggerParser,
                           @Autowired(required = false) CacheManager cacheManager) {
        this.coverArtDao = coverArtDao;
        this.mediaFolderService = mediaFolderService;
        this.jaudiotaggerParser = jaudiotaggerParser;
        this.coverArtCache = cacheManager != null ? cacheManager.getCache("coverArtCache", String.class, CoverArt.class) : null;
    }

    public void upsert(EntityType type, int id, String path, Integer folderId, boolean overridden) {
        CoverArt art = new CoverArt(id, type, path, folderId, overridden);
        upsert(art);
    }

    public void upsert(CoverArt art) {
        if (coverArtCache != null && art != null && art.getEntityType() != null) {
            coverArtCache.remove(art.getEntityType().toString() + "-" + art.getEntityId());
        }
        if (art.getAccentColor() == null) {
            CoverArt existing = coverArtDao.get(art.getEntityType(), art.getEntityId());
            if (existing != null && art.getPath().equals(existing.getPath()) && existing.getAccentColor() != null) {
                // Same image already processed, reuse the stored color.
                art.setAccentColor(existing.getAccentColor());
            } else {
                art.setAccentColor(computeAccentColor(art));
            }
        }
        coverArtDao.upsert(art);
    }

    /**
     * Returns the dominant (accent) color of the cover art, or {@code null} if unknown.
     */
    public String getAccentColor(EntityType type, int id) {
        return get(type, id).getAccentColor();
    }

    /**
     * Computes and stores the accent color for every cover art row that still has none.
     * Needed to backfill colors for cover art that was persisted before the accent color
     * feature existed (such rows are not re-upserted by a regular incremental scan).
     *
     * @return the number of rows updated
     */
    public int backfillAccentColors() {
        List<CoverArt> missing = coverArtDao.getCoverArtWithoutAccentColor();
        int count = 0;
        for (CoverArt art : missing) {
            String accent = computeAccentColor(art);
            if (accent != null) {
                art.setAccentColor(accent);
                art.setUpdated(Instant.now());
                upsert(art);
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Backfilled accent colors for {}/{} cover art rows", count, missing.size());
        } else {
            LOG.debug("No cover art rows without accent color to backfill ({} checked)", missing.size());
        }
        return count;
    }

    /**
     * Extracts the dominant color from the cover art image using the color-thief library
     * and returns it as a "#RRGGBB" string, or {@code null} if the image cannot be read.
     */
    private String computeAccentColor(CoverArt art) {
        try {
            Path fullPath = getFullPath(art);
            if (fullPath == null) {
                return null;
            }
            BufferedImage image;
            if (jaudiotaggerParser.isApplicable(fullPath)) {
                Artwork artwork = JaudiotaggerParser.getArtwork(fullPath);
                image = artwork == null ? null : ImageIO.read(new ByteArrayInputStream(artwork.getBinaryData()));
            } else {
                image = ImageIO.read(fullPath.toFile());
            }
            if (image == null) {
                return null;
            }
            int[] rgb = ColorThief.getColor(image);
            return rgb == null ? null : String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
        } catch (Exception x) {
            LOG.warn("Failed to compute accent color for cover art {}", art.getPath(), x);
            return null;
        }
    }

    public void persistIfNeeded(MediaFile mediaFile) {
        if (mediaFile.getArt() != null && !CoverArt.NULL_ART.equals(mediaFile.getArt())) {
            CoverArt art = get(EntityType.MEDIA_FILE, mediaFile.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                mediaFile.getArt().setEntityId(mediaFile.getId());
                upsert(mediaFile.getArt());
            }
            mediaFile.setArt(null);
        }
    }

    public void persistIfNeeded(Album album) {
        if (album.getArt() != null && !CoverArt.NULL_ART.equals(album.getArt())) {
            CoverArt art = get(EntityType.ALBUM, album.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                album.getArt().setEntityId(album.getId());
                upsert(album.getArt());
            }
            album.setArt(null);
        }
    }

    public void persistIfNeeded(Artist artist) {
        if (artist.getArt() != null && !CoverArt.NULL_ART.equals(artist.getArt())) {
            CoverArt art = get(EntityType.ARTIST, artist.getId());
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                artist.getArt().setEntityId(artist.getId());
                upsert(artist.getArt());
            }
            artist.setArt(null);
        }
    }

    public CoverArt get(EntityType type, int id) {
        String key = type.toString() + "-" + id;
        return CacheHelper.getOrCompute(coverArtCache, key, k -> Optional.ofNullable(coverArtDao.get(type, id)).orElse(CoverArt.NULL_ART));
    }

    public Path getFullPath(EntityType type, int id) {
        CoverArt art = get(type, id);
        return getFullPath(art);
    }

    public Path getFullPath(CoverArt art) {
        if (art != null && !CoverArt.NULL_ART.equals(art)) {
            if (art.getFolderId() == null) {
                // null folder ids mean absolute paths
                return art.getRelativePath();
            } else {
                MusicFolder folder = mediaFolderService.getMusicFolderById(art.getFolderId());
                if (folder != null) {
                    return art.getFullPath(folder.getPath());
                }
            }
        }

        return null;
    }

    public void delete(EntityType type, int id) {
        if (coverArtCache != null) {
            coverArtCache.remove(type.toString() + "-" + id);
        }
        coverArtDao.delete(type, id);
    }

    public void expunge() {
        if (coverArtCache != null) {
            coverArtCache.removeAll();
        }
        coverArtDao.expunge();
    }
}
