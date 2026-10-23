package org.airsonic.player.controller.statistics;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;

import java.util.Locale;
import java.util.Optional;

public class TransferStatusHolder {
    private final TransferType transferType;
    private final String playerDescription;
    private final String playerType;
    private final String username;
    private final String path;
    private final String bytesTransferred;
    private final TransferStatus.SampleHistory history;

    TransferStatusHolder(TransferStatus transferStatus, TransferType transferType, Locale locale) {
        this.transferType = transferType;
        this.playerDescription = Optional.ofNullable(transferStatus.getPlayer()).map(Player::toString).orElse(null);
        this.playerType = Optional.ofNullable(transferStatus.getPlayer()).map(Player::getType).orElse(null);
        this.username = Optional.ofNullable(transferStatus.getPlayer()).map(Player::getUsername).orElse(null);
        this.path = Optional.ofNullable(transferStatus.getMediaFile())
                .map(MediaFile::getRelativePath)
                .or(() -> Optional.ofNullable(transferStatus.getExternalFile()))
                .map(FileUtil::getShortPath)
                .orElse(null);
        this.bytesTransferred = StringUtil.formatBytes(transferStatus.getBytesTransferred(), locale);
        this.history = transferStatus.getHistory();
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public String getPlayerDescription() {
        return playerDescription;
    }

    public String getPlayerType() {
        return playerType;
    }

    public String getUsername() {
        return username;
    }

    public String getPath() {
        return path;
    }

    public String getBytesTransferred() {
        return bytesTransferred;
    }

    public TransferStatus.SampleHistory getHistory() {
        return history;
    }
}
