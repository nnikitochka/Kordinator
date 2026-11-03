package org.airsonic.player.controller.cover.request;

import org.airsonic.player.service.CoverArtImageService;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;

public class MediaFileCoverArtRequest extends CoverArtRequest {
    private final MediaFile dir;
    private final Integer proxyId;

    public MediaFileCoverArtRequest(CoverArtImageService coverArtImageService, MediaFile mediaFile, Integer proxyId) {
        super(coverArtImageService, coverArtImageService.coverArtService.get(CoverArt.EntityType.MEDIA_FILE, mediaFile.getId()),
                () -> mediaFile.getFolderId() + "/" + mediaFile.getPath(),
                mediaFile::getChanged);
        this.dir = mediaFile;
        this.proxyId = proxyId;
    }

    public MediaFileCoverArtRequest(CoverArtImageService coverArtImageService, MediaFile mediaFile) {
        this(coverArtImageService, mediaFile, null);
    }

    @Override
    public String getAlbum() {
        return dir.getName();
    }

    @Override
    public String getArtist() {
        return dir.getAlbumArtist() != null ? dir.getAlbumArtist() : dir.getArtist();
    }

    @Override
    public String toString() {
        return "Media file " + dir.getId() + " - " + dir + (proxyId == null ? "" : " (Proxy for " + proxyId + ")");
    }
}
