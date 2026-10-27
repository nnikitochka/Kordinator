package org.airsonic.player.controller.cover;

import com.google.common.io.MoreFiles;
import org.airsonic.player.controller.cover.request.*;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.*;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jaudiotagger.tag.images.Artwork;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.LastModified;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Controller which produces cover art images.
 */
@Controller
@RequestMapping({"/coverArt", "/ext/coverArt"})
public class CoverArtController implements LastModified {
    public static final String ALBUM_COVERART_PREFIX = "al-";
    public static final String ARTIST_COVERART_PREFIX = "ar-";
    public static final String PLAYLIST_COVERART_PREFIX = "pl-";

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtController.class);

    public final MediaFileService mediaFileService;
    public final CoverArtService coverArtService;
    private final TranscodingService transcodingService;
    private final SettingsService settingsService;
    public final PlaylistService playlistService;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final JaudiotaggerParser jaudiotaggerParser;
    private Semaphore semaphore;

    public CoverArtController(MediaFileService mediaFileService, CoverArtService coverArtService, TranscodingService transcodingService, SettingsService settingsService, PlaylistService playlistService, ArtistDao artistDao, AlbumDao albumDao, JaudiotaggerParser jaudiotaggerParser) {
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

    @Override
    public long getLastModified(@NotNull HttpServletRequest request) {
        CoverArtRequest coverArtRequest = createCoverArtRequest(request);
        if (coverArtRequest == null) {
            return -1L;
        }
        return coverArtRequest.lastModified().toEpochMilli();
    }

    @GetMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        CoverArtRequest coverArtRequest = createCoverArtRequest(request);
        LOG.trace("handleRequest - {}", coverArtRequest);
        Integer size = ServletRequestUtils.getIntParameter(request, "size");

        // Send fallback image if no ID is given. (No need to cache it, since it will be cached in browser.)
        if (coverArtRequest == null) {
            sendFallback(size, response);
            return;
        }

        try {
            // Optimize if no scaling is required.
            if (size == null && coverArtRequest.coverArt != null) {
                LOG.trace("sendUnscaled - {}", coverArtRequest);
                sendUnscaled(coverArtRequest, response);
                return;
            }

            // Send cached image, creating it if necessary.
            if (size == null) {
                size = CoverArtScheme.LARGE.getSize() * 2;
            }
            Path cachedImage = getCachedImage(coverArtRequest, size);
            sendImage(cachedImage, response);
        } catch (Exception e) {
            LOG.debug("Sending fallback as an exception was encountered during normal cover art processing", e);
            sendFallback(size, response);
        }

    }

    private CoverArtRequest createCoverArtRequest(HttpServletRequest request) {
        String id = request.getParameter("id");
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
        return createMediaFileCoverArtRequest(Integer.parseInt(id), request);
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

    private CoverArtRequest createMediaFileCoverArtRequest(int id, HttpServletRequest request) {
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            return null;
        }
        if (mediaFile.isVideo()) {
            int offset = ServletRequestUtils.getIntParameter(request, "offset", 60);
            return new VideoCoverArtRequest(this, mediaFile, offset);
        }

        var dir = mediaFile.isDirectory() ? mediaFile : mediaFileService.getParentOf(mediaFile);

        return new MediaFileCoverArtRequest(this, dir, mediaFile.isDirectory() ? null : mediaFile.getId());
    }

    private void sendImage(Path file, HttpServletResponse response) throws IOException {
        response.setContentType(StringUtil.getMimeType(MoreFiles.getFileExtension(file)));
        Files.copy(file, response.getOutputStream());
    }

    private void sendFallback(Integer size, HttpServletResponse response) throws IOException {
        if (response.getContentType() == null) {
            response.setContentType(StringUtil.getMimeType("jpeg"));
        }
        try (InputStream in = getClass().getResourceAsStream("default_cover.jpg")) {
            BufferedImage image = ImageIO.read(in);
            if (size != null) {
                image = scale(image, size, size);
            }
            ImageIO.write(image, "jpeg", response.getOutputStream());
        }
    }

    private void sendUnscaled(CoverArtRequest coverArtRequest, HttpServletResponse response) throws IOException {
        Pair<InputStream, String> imageInputStreamWithType = getImageInputStreamWithType(
                coverArtService.getFullPath(coverArtRequest.coverArt));

        try (InputStream in = imageInputStreamWithType.getLeft()) {
            response.setContentType(imageInputStreamWithType.getRight());
            IOUtils.copy(in, response.getOutputStream());
        }
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

    public static BufferedImage scale(BufferedImage image, int width, int height) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage thumb = image;

        // For optimal results, use step by step bilinear resampling - halfing the size at each step.
        do {
            w /= 2;
            h /= 2;
            if (w < width) {
                w = width;
            }
            if (h < height) {
                h = height;
            }

            BufferedImage temp = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = temp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(thumb, 0, 0, temp.getWidth(), temp.getHeight(), null);
            g2.dispose();

            thumb = temp;
        } while (w != width);

        return thumb;
    }
}
