package org.airsonic.player.service;

import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.LastFmCoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class CoverArtSearchService {
    private static final Logger LOG = LoggerFactory.getLogger(CoverArtSearchService.class);

    private SecurityService securityService;
    private MediaFileService mediaFileService;
    private LastFmService lastFmService;
    private final MediaFolderService mediaFolderService;
    private final CoverArtService coverArtService;

    public CoverArtSearchService(SecurityService securityService, MediaFileService mediaFileService, LastFmService lastFmService, MediaFolderService mediaFolderService, CoverArtService coverArtService) {
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.mediaFolderService = mediaFolderService;
        this.coverArtService = coverArtService;
    }

    public List<LastFmCoverArt> searchCoverArt(String artist, String album) {
        return lastFmService.searchCoverArt(artist, album);
    }

    public void setCoverArtImage(int id, String url, String username) throws Exception {
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null || !mediaFile.isDirectory() || mediaFolderService.getMusicFoldersForUser(username).stream()
                .noneMatch(folder -> folder.getId().equals(mediaFile.getFolderId()))) {
            throw new SecurityException("Music folder is not accessible");
        }
        saveCoverArt(mediaFile, url);
    }

    private void saveCoverArt(MediaFile dir, String url) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(20 * 1000) // 20 seconds
                .setSocketTimeout(20 * 1000) // 20 seconds
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);

        // Attempt to resolve proper suffix.
        String suffix = "jpg";
        if (url.toLowerCase().endsWith(".gif")) {
            suffix = "gif";
        } else if (url.toLowerCase().endsWith(".png")) {
            suffix = "png";
        }

        // Check permissions.
        MusicFolder folder = mediaFolderService.getMusicFolderById(dir.getFolderId());
        Path fullPath = dir.getFullPath(folder.getPath());
        Path newCoverFile = fullPath.resolve("cover." + suffix);
        if (!securityService.isWriteAllowed(folder.getPath().relativize(newCoverFile), folder)) {
            throw new SecurityException("Permission denied: " + StringEscapeUtils.escapeHtml(newCoverFile.toString()));
        }

        try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(method);
                InputStream input = response.getEntity().getContent()) {

            if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 300) {
                throw new IOException("Cover image download failed: HTTP " + response.getStatusLine().getStatusCode());
            }

            // If file exists, create a backup.
            backup(newCoverFile, fullPath.resolve("cover." + suffix + ".backup"));

            // Write file.
            Files.copy(input, newCoverFile, StandardCopyOption.REPLACE_EXISTING);
        }

        coverArtService.upsert(EntityType.MEDIA_FILE, dir.getId(), folder.getPath().relativize(newCoverFile).toString(), dir.getFolderId(), true);
    }

    private void backup(Path newCoverFile, Path backup) {
        if (Files.exists(newCoverFile)) {
            try {
                Files.move(newCoverFile, backup, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Backed up old image file to {}", backup);
            } catch (IOException e) {
                LOG.warn("Failed to create image file backup {}", backup, e);
            }
        }
    }

}
