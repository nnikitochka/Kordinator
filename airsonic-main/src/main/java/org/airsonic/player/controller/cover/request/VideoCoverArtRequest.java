package org.airsonic.player.controller.cover.request;

import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.domain.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class VideoCoverArtRequest extends CoverArtRequest {
    private static final Logger LOG = LoggerFactory.getLogger(VideoCoverArtRequest.class);

    private final MediaFile mediaFile;
    private final int offset;

    public VideoCoverArtRequest(CoverArtController coverArtController, MediaFile mediaFile, int offset) {
        super(coverArtController);
        this.mediaFile = mediaFile;
        this.offset = offset;
        keyGenerator = () -> mediaFile.getFolderId() + "/" + mediaFile.getPath() + "/" + offset;
        lastModifiedGenerator = mediaFile::getChanged;
    }

    @Override
    public BufferedImage createImage(int height) {
        int width = height * 16 / 9;
        try (InputStream in = coverArtController.getImageInputStreamForVideo(mediaFile, width, height, offset)) {
            BufferedImage result = ImageIO.read(in);
            if (result != null) {
                return result;
            }
            LOG.warn("Failed to process cover art for {}: {}", mediaFile, null);
        } catch (Throwable x) {
            LOG.warn("Failed to process cover art for {}", mediaFile, x);
        }
        return createAutoCover(width, height);
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return mediaFile.getName();
    }

    @Override
    public String toString() {
        return "Video file " + mediaFile.getId() + " - " + mediaFile;
    }
}
