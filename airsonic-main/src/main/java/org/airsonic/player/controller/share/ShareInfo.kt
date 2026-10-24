package org.airsonic.player.controller.share;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Share;

public class ShareInfo {
    private final String shareUrl;
    private final Share share;
    private final MediaFile dir;

    public ShareInfo(String shareUrl, Share share, MediaFile dir) {
        this.shareUrl = shareUrl;
        this.share = share;
        this.dir = dir;
    }

    public Share getShare() {
        return share;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public MediaFile getDir() {
        return dir;
    }
}
