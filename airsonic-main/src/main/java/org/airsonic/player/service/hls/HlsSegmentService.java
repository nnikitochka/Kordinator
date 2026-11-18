package org.airsonic.player.service.hls;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.io.PipeStreams.MonitoredResource;
import org.airsonic.player.security.CallerContext;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.acls.model.NotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HlsSegmentService {
    private static final Logger LOG = LoggerFactory.getLogger(HlsSegmentService.class);
    private final MediaFileService mediaFileService;
    private final SecurityService securityService;
    private final StatusService statusService;
    private final SettingsService settingsService;
    private final TranscodingService transcodingService;
    private final Map<HlsSession.Key, HlsSession> sessions = new ConcurrentHashMap<>();

    public HlsSegmentService(MediaFileService mediaFileService, SecurityService securityService,
            StatusService statusService, SettingsService settingsService, TranscodingService transcodingService) {
        this.mediaFileService = mediaFileService;
        this.securityService = securityService;
        this.statusService = statusService;
        this.settingsService = settingsService;
        this.transcodingService = transcodingService;
    }

    public void init() {
        FileUtil.delete(HlsSession.getHlsRootDirectory());
    }

    public Resource getSegment(boolean jwtAuthentication, User user, CallerContext callerContext, Player player, int id, String playerId,
            int segmentIndex, int maxBitRate, String size, Integer duration, Integer audioTrack, boolean enforceFolderAccess, boolean countPlay) throws Exception {
        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            throw new NotFoundException("Media file not found: " + id);
        }
        if ((enforceFolderAccess || !jwtAuthentication)
                && !securityService.isFolderAccessAllowed(mediaFile, callerContext)) {
            throw new AccessDeniedException("Access to file " + id + " is forbidden for user " + user.getUsername());
        }
        TransferStatus status = this.statusService.createStreamStatus(player);
        status.setMediaFile(mediaFile);
        HlsSession.Key sessionKey = new HlsSession.Key(id, playerId, maxBitRate, size, duration, audioTrack);
        HlsSession session = getOrCreateSession(sessionKey, mediaFile);
        Path segmentFile;
        try {
            segmentFile = session.waitForSegment(segmentIndex, 30000L);
        } catch (Exception e) {
            statusService.removeStreamStatus(status);
            throw e;
        }
        if (segmentFile == null) {
            statusService.removeStreamStatus(status);
            throw new TimeoutException("Timed out producing segment " + segmentIndex + " for media file " + id);
        }
        Supplier<TransferStatus> statusSupplier = () -> status;
        Consumer<TransferStatus> statusCloser = s -> {
            securityService.updateUserByteCounts(user, s.getBytesTransferred(), 0L, 0L);
            statusService.removeStreamStatus(s);
        };
        BiConsumer<InputStream, TransferStatus> inputStreamInit = (i, s) -> {
            LOG.info("{}: {} listening to {}", player.getIpAddress(), player.getUsername(), FileUtil.getShortPath(mediaFile.getRelativePath()));
            if (countPlay && segmentIndex == 0)
                this.mediaFileService.incrementPlayCount(mediaFile);
        };

        Resource resource = new MonitoredResource(new PathResource(segmentFile),
                settingsService.getDownloadBitrateLimiter(), statusSupplier, statusCloser, inputStreamInit);

        return resource;
    }

    private HlsSession getOrCreateSession(HlsSession.Key sessionKey, MediaFile mediaFile) {
        return this.sessions.computeIfAbsent(sessionKey, k -> {
            for (HlsSession.Key k1 : sessions.keySet()) {
                if (k1.getMediaFileId() == k.getMediaFileId()
                        && StringUtils.equals(k1.getPlayerId(), k.getPlayerId())) {
                    sessions.remove(k1).destroySession();
                }
            }

            return new HlsSession(k, mediaFile, transcodingService);
        });
    }

}
