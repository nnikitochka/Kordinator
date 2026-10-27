package org.airsonic.player.controller.cover.request;

import org.airsonic.player.controller.cover.AutoCover;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class CoverArtRequest {
    private static final Logger LOG = LoggerFactory.getLogger(CoverArtRequest.class);

    protected final CoverArtController coverArtController;
    public CoverArt coverArt;
    protected Supplier<String> keyGenerator;
    protected Supplier<Instant> lastModifiedGenerator;

    public CoverArtRequest(CoverArtController coverArtController) {
        this.coverArtController = coverArtController;
    }

    public CoverArtRequest(CoverArtController coverArtController, CoverArt coverArt, Supplier<String> keyGenerator, Supplier<Instant> lastModifiedGenerator) {
        this.coverArtController = coverArtController;
        this.coverArt = CoverArt.NULL_ART.equals(coverArt) ? null : coverArt;
        this.keyGenerator = keyGenerator;
        this.lastModifiedGenerator = lastModifiedGenerator;
    }

    public String getKey() {
        return Optional.ofNullable(coverArt).map(c -> coverArt.getFolderId() + "/" + coverArt.getPath())
                .orElseGet(keyGenerator);
    }

    public Instant lastModified() {
        return Optional.ofNullable(coverArt).map(c -> FileUtil.lastModified(coverArtController.coverArtService.getFullPath(c)))
                .orElseGet(lastModifiedGenerator);
    }

    public BufferedImage createImage(int size) {
        if (coverArt != null) {
            try (InputStream in = coverArtController.getImageInputStream(coverArt)) {
                String reason;
                if (in == null) {
                    reason = "getImageInputStream";
                } else {
                    BufferedImage bimg = ImageIO.read(in);
                    if (bimg == null) {
                        reason = "ImageIO.read";
                    } else {
                        return CoverArtController.scale(bimg, size, size);
                    }
                }
                LOG.warn("Failed to process cover art {}: {} failed", coverArt, reason);
            } catch (Throwable x) {
                LOG.warn("Failed to process cover art {}", coverArt, x);
            }
        }
        return createAutoCover(size, size);
    }

    protected BufferedImage createAutoCover(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        AutoCover autoCover = new AutoCover(graphics, getKey(), getArtist(), getAlbum(), width, height);
        autoCover.paintCover();
        graphics.dispose();
        return image;
    }

    public abstract String getAlbum();

    public abstract String getArtist();
}
