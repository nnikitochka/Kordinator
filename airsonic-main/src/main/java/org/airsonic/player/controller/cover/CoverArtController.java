package org.airsonic.player.controller.cover;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.service.CoverArtImageService;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.LastModified;

import java.awt.image.BufferedImage;

/** Controller for cover art on the existing website. */
@Controller
@RequestMapping({"/coverArt", "/ext/coverArt"})
public class CoverArtController implements LastModified {
    public static final String ALBUM_COVERART_PREFIX = CoverArtImageService.ALBUM_COVERART_PREFIX;
    public static final String ARTIST_COVERART_PREFIX = CoverArtImageService.ARTIST_COVERART_PREFIX;
    public static final String PLAYLIST_COVERART_PREFIX = CoverArtImageService.PLAYLIST_COVERART_PREFIX;

    private final CoverArtImageService images;

    public CoverArtController(CoverArtImageService images) {
        this.images = images;
    }

    @Override
    public long getLastModified(@NotNull HttpServletRequest request) {
        return images.getLastModified(request.getParameter("id"), request.getParameter("offset"));
    }

    @GetMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer size = ServletRequestUtils.getIntParameter(request, "size");
        String offset = request.getParameter("offset");
        try (CoverArtImageService.ImageResult image = images.getImage(request.getParameter("id"), size, offset)) {
            response.setContentType(image.contentType());
            IOUtils.copy(image.stream(), response.getOutputStream());
        }
    }

    public static BufferedImage scale(BufferedImage image, int width, int height) {
        return CoverArtImageService.scale(image, width, height);
    }
}
