package org.airsonic.player.controller.statistics;

public class UserDataTransferStatistics {
    private final String user;
    private final long streamed;
    private final long downloaded;
    private final long uploaded;

    public UserDataTransferStatistics(String user, long streamed, long downloaded, long uploaded) {
        this.user = user;
        this.streamed = streamed;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
    }

    public String getUser() {
        return user;
    }

    public long getStreamed() {
        return streamed;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public long getUploaded() {
        return uploaded;
    }
}
