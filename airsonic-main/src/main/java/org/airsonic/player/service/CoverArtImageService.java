package org.airsonic.player.service;

import com.google.common.io.MoreFiles;
import org.airsonic.player.controller.cover.request.*;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

/**
 * Produces and caches cover art images.
 */
@Service
public class CoverArtImageService {
    public static final String ALBUM_COVERART_PREFIX = "al-";
    public static final String ARTIST_COVERART_PREFIX = "ar-";
    public static final String PLAYLIST_COVERART_PREFIX = "pl-";

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtImageService.class);

    public final MediaFileService mediaFileService;
    public final CoverArtService coverArtService;
    private final TranscodingService transcodingService;
    private final SettingsService settingsService;
    public final PlaylistService playlistService;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final JaudiotaggerParser jaudiotaggerParser;
    private Semaphore semaphore;

    public CoverArtImageService(MediaFileService mediaFileService, CoverArtService coverArtService, TranscodingService transcodingService, SettingsService settingsService, PlaylistService playlistService, ArtistDao artistDao, AlbumDao albumDao, JaudiotaggerParser jaudiotaggerParser) {
        this.mediaFileService = mediaFileService;
        this.coverArtService = coverArtService;
        this.transcodingService = transcodingService;
        this.settingsService = settingsService;
        this.playlistService = playlistService;
        this.artistDao = artistDao;
        this.albumDao = albumDao;
        this.jaudiotaggerParser = jaudiotaggerParser;
    }

    @PostConstruct
    public void init() {
        semaphore = new Semaphore(settingsService.getCoverArtConcurrency());
    }

    public long getLastModified(String id, String offset) {
        CoverArtRequest request = createCoverArtRequest(id, offset);
        return request == null ? -1L : request.lastModified().toEpochMilli();
    }

    public record ImageResult(InputStream stream, String contentType) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    public ImageResult getImage(String id, Integer size, String offset) throws IOException {
        CoverArtRequest request = createCoverArtRequest(id, offset);
        LOG.trace("getImage - {}", request);
        if (request == null) {
            return fallback(size);
        }
        try {
            if (size == null && request.coverArt != null) {
                Pair<InputStream, String> image = getImageInputStreamWithType(coverArtService.getFullPath(request.coverArt));
                return new ImageResult(image.getLeft(), image.getRight());
            }
            int imageSize = size == null ? CoverArtScheme.LARGE.getSize() * 2 : size;
            Path cachedImage = getCachedImage(request, imageSize);
            return new ImageResult(Files.newInputStream(cachedImage), StringUtil.getMimeType(MoreFiles.getFileExtension(cachedImage)));
        } catch (Exception e) {
            LOG.debug("Sending fallback as an exception was encountered during normal cover art processing", e);
            return fallback(size);
        }
    }

    private ImageResult fallback(Integer size) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/org/airsonic/player/controller/default_cover.jpg");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (size != null) {
                image = scale(image, size, size);
            }
            ImageIO.write(image, "jpeg", out);
            return new ImageResult(new ByteArrayInputStream(out.toByteArray()), StringUtil.getMimeType("jpeg"));
        }
    }

    private CoverArtRequest createCoverArtRequest(String id, String offset) {
        if (id == null) {
            return null;
        }

        if (id.startsWith(ALBUM_COVERART_PREFIX)) {
            return createAlbumCoverArtRequest(Integer.parseInt(id.replace(ALBUM_COVERART_PREFIX, "")));
        }
        if (id.startsWith(ARTIST_COVERART_PREFIX)) {
            return createArtistCoverArtRequest(Integer.parseInt(id.replace(ARTIST_COVERART_PREFIX, "")));
        }
        if (id.startsWith(PLAYLIST_COVERART_PREFIX)) {
            return createPlaylistCoverArtRequest(Integer.parseInt(id.replace(PLAYLIST_COVERART_PREFIX, "")));
        }
        return createMediaFileCoverArtRequest(Integer.parseInt(id), offset);
    }

    private CoverArtRequest createAlbumCoverArtRequest(int id) {
        Album album = albumDao.getAlbum(id);
        return album == null ? null : new AlbumCoverArtRequest(this, album);
    }

    private CoverArtRequest createArtistCoverArtRequest(int id) {
        Artist artist = artistDao.getArtist(id);
        return artist == null ? null : new ArtistCoverArtRequest(this, artist);
    }

    private PlaylistCoverArtRequest createPlaylistCoverArtRequest(int id) {
        Playlist playlist = playlistService.getPlaylist(id);
        return playlist == null ? null : new PlaylistCoverArtRequest(this, playlist);
    }

    private CoverArtRequest createMediaFileCoverArtRequest(int id, String offset) {
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            return null;
        }
        if (mediaFile.isVideo()) {
            int parsedOffset = offset == null || offset.isBlank() ? 60 : Integer.parseInt(offset.trim());
            return new VideoCoverArtRequest(this, mediaFile, parsedOffset);
        }

        var dir = mediaFile.isDirectory() ? mediaFile : mediaFileService.getParentOf(mediaFile);

        return new MediaFileCoverArtRequest(this, dir, mediaFile.isDirectory() ? null : mediaFile.getId());
    }

    private Path getCachedImage(CoverArtRequest request, int size) throws IOException {
        String hash = DigestUtils.md5Hex(request.getKey());
        String encoding = request.coverArt != null ? "jpeg" : "png";
        Path cachedImage = getImageCacheDirectory(size).resolve(hash + "." + encoding);

        // Synchronize to avoid concurrent writing to the same file.
        synchronized (hash.intern()) {

            // Is cache missing or obsolete?
            if (!Files.exists(cachedImage) || request.lastModified().isAfter(FileUtil.lastModified(cachedImage))) {
//                LOG.info("Cache MISS - " + request + " (" + size + ")");
                ImageWriter writer = null;

                try (OutputStream os = Files.newOutputStream(cachedImage);
                        BufferedOutputStream bos = new BufferedOutputStream(os);
                        ImageOutputStream out = ImageIO.createImageOutputStream(bos)) {
                    semaphore.acquire();
                    BufferedImage image = request.createImage(size);
                    if (image == null) {
                        throw new Exception("Unable to decode image.");
                    }
                    writer = ImageIO.getImageWritersByFormatName(encoding).next();

                    float quality = (float) (settingsService.getCoverArtQuality() / 100.0);
                    ImageWriteParam params = writer.getDefaultWriteParam();
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality); // default is 0.75

                    writer.setOutput(out);
                    writer.write(null, new IIOImage(image, null, null), params);

                } catch (Throwable x) {
                    // Delete corrupt (probably empty) thumbnail cache.
                    LOG.warn("Failed to create thumbnail for {}", request, x);
                    FileUtil.delete(cachedImage);
                    throw new IOException("Failed to create thumbnail for " + request + ". " + x.getMessage());
                } finally {
                    if (writer != null) {
                        writer.dispose();
                        writer = null;
                    }
                    semaphore.release();
                }
            }
//            else {
//                LOG.info("Cache HIT - " + request + " (" + size + ")");
//            }
            return cachedImage;
        }
    }

    /**
     * Returns an input stream to the image in the given file.  If the file is an audio file,
     * the embedded album art is returned.
     */
    public InputStream getImageInputStream(CoverArt art) throws IOException {
        return getImageInputStreamWithType(coverArtService.getFullPath(art)).getLeft();
    }

    /**
     * Returns an input stream to the image in the given file.  If the file is an audio file,
     * the embedded album art is returned. In addition returns the mime type
     */
    private Pair<InputStream, String> getImageInputStreamWithType(Path file) throws IOException {
        InputStream is;
        String mimeType;
        if (jaudiotaggerParser.isApplicable(file)) {
            LOG.trace("Using Jaudio Tagger for reading artwork from {}", file);
            try {
                LOG.trace("Reading artwork from file {}", file);
                Artwork artwork = JaudiotaggerParser.getArtwork(file);
                is = new ByteArrayInputStream(artwork.getBinaryData());
                mimeType = artwork.getMimeType();
            } catch (Exception e) {
                LOG.debug("Could not read artwork from file {}", file);
                throw new RuntimeException(e);
            }
        } else {
            is = new BufferedInputStream(Files.newInputStream(file));
            mimeType = StringUtil.getMimeType(MoreFiles.getFileExtension(file));
        }
        return Pair.of(is, mimeType);
    }

    public InputStream getImageInputStreamForVideo(MediaFile mediaFile, int width, int height, int offset) throws Exception {
        VideoTranscodingSettings videoSettings = new VideoTranscodingSettings(width, height, offset, 0);
        TranscodingService.Parameters parameters = new TranscodingService.Parameters(mediaFile, videoSettings);
        String command = settingsService.getVideoImageCommand();
        parameters.setTranscoding(new Transcoding(null, null, null, null, command, null, null, false));
        return transcodingService.getTranscodedInputStream(parameters);
    }

    private synchronized Path getImageCacheDirectory(int size) {
        Path dir = SettingsService.getAirsonicHome().resolve("thumbs").resolve(String.valueOf(size));
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                LOG.info("Created thumbnail cache {}", dir);
            } catch (Exception e) {
                LOG.error("Failed to create thumbnail cache {}", dir, e);
            }
        }

        return dir;
    }

    public static BufferedImage scale(BufferedImage thumb, int width, int height) {
        int imageWidth = thumb.getWidth();
        int imageHeight = thumb.getHeight();

        // For optimal results, use step by step bilinear resampling - halfing the size at each step.
        do {
            imageWidth /= 2;
            imageHeight /= 2;
            if (imageWidth < width) {
                imageWidth = width;
            }
            if (imageHeight < height) {
                imageHeight = height;
            }

            BufferedImage temp = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = temp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(thumb, 0, 0, temp.getWidth(), temp.getHeight(), null);
            g2.dispose();

            thumb = temp;
        } while (imageWidth != width);

        return thumb;
    }
}
