package org.airsonic.player.service;

import org.airsonic.player.ajax.playing.NowPlayingInfo;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides services for maintaining the list of stream, download and upload statuses.
 * <p/>
 * Note that for stream statuses, the last inactive status is also stored.
 *
 * @see TransferStatus
 */
@Service
public class StatusService {
    private MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private SettingsService settingsService;
    private SimpMessagingTemplate messagingTemplate;
    private final TaskSchedulingService taskService;

    public StatusService(MediaFileService mediaFileService, MediaFolderService mediaFolderService, SettingsService settingsService, SimpMessagingTemplate messagingTemplate, TaskSchedulingService taskService) {
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.settingsService = settingsService;
        this.messagingTemplate = messagingTemplate;
        this.taskService = taskService;
    }

    @PostConstruct
    public void cleanup() {
        taskService.scheduleFixedDelayTask("remote-playstatus-cleanup", this::cleanupRemotePlays, Instant.now().plus(3, ChronoUnit.HOURS), Duration.ofHours(3), true);
    }

    private final List<TransferStatus> streamStatuses = Collections.synchronizedList(new ArrayList<>());
    private final List<TransferStatus> downloadStatuses = Collections.synchronizedList(new ArrayList<>());
    private final List<TransferStatus> uploadStatuses = Collections.synchronizedList(new ArrayList<>());
    private final Set<PlayStatus> remotePlays = ConcurrentHashMap.newKeySet();
    private final Set<PlayStatus> activeLocalPlays = ConcurrentHashMap.newKeySet();

    // Maps from player ID to latest inactive stream status.
    private final Map<Integer, TransferStatus> inactiveStreamStatuses = new ConcurrentHashMap<>();

    public TransferStatus createStreamStatus(Player player) {
        return createStatus(player, streamStatuses);
    }

    public void removeStreamStatus(TransferStatus status) {
        if (status.getMediaFile() == null && status.getExternalFile() == null) {
            streamStatuses.remove(status);
            status.setActive(false);
            return;
        }
        // Move it to the map of inactive statuses.
        inactiveStreamStatuses.compute(status.getPlayer().getId(), (k, v) -> {
            streamStatuses.remove(status);
            status.setActive(false);
            if (v != null) {
                broadcast(getPlayStatus(v), "recent/remove");
            }
            return status;
        });
        broadcast(getPlayStatus(status), "recent/add");
    }

    public List<TransferStatus> getAllStreamStatuses() {
        List<TransferStatus> snapshot = new ArrayList<>(streamStatuses);
        Set<Integer> playerIds = snapshot.parallelStream()
                .map(x -> x.getPlayer().getId())
                .collect(Collectors.toCollection(() -> ConcurrentHashMap.newKeySet()));
        // Add inactive status for those players that have no active status.
        return Stream.concat(
                snapshot.parallelStream(),
                inactiveStreamStatuses.values().parallelStream().filter(s -> !playerIds.contains(s.getPlayer().getId())))
                .collect(Collectors.toList());
    }

    public List<TransferStatus> getStreamStatusesForPlayer(Player player) {
        // unsynchronized stream access, but should be okay, we'll just be a bit behind
        return streamStatuses.parallelStream()
                .filter(s -> s.getPlayer().getId().equals(player.getId()))
                .collect(Collectors.toList());
    }

    public TransferStatus getInactiveStreamStatusForPlayer(Player player) {
        return inactiveStreamStatuses.get(player.getId());
    }

    public TransferStatus createDownloadStatus(Player player) {
        return createStatus(player, downloadStatuses);
    }

    public void removeDownloadStatus(TransferStatus status) {
        downloadStatuses.remove(status);
    }

    public List<TransferStatus> getAllDownloadStatuses() {
        return new ArrayList<>(downloadStatuses);
    }

    public TransferStatus createUploadStatus(Player player) {
        return createStatus(player, uploadStatuses);
    }

    public void removeUploadStatus(TransferStatus status) {
        uploadStatuses.remove(status);
    }

    public List<TransferStatus> getAllUploadStatuses() {
        return new ArrayList<>(uploadStatuses);
    }

    public void cleanupRemotePlays() {
        Set<PlayStatus> expired = remotePlays.parallelStream().filter(PlayStatus::isExpired).collect(Collectors.toSet());
        expired.forEach(e -> {
            remotePlays.remove(e);
            broadcast(e, "recent/remove");
        });
    }

    public void addRemotePlay(PlayStatus playStatus) {
        remotePlays.add(playStatus);
        broadcast(playStatus, "recent/add");
    }

    public void addActiveLocalPlay(PlayStatus status) {
        activeLocalPlays.add(status);
        broadcast(status, "current/add");
    }

    public void removeActiveLocalPlay(PlayStatus status) {
        activeLocalPlays.remove(status);
        broadcast(status, "current/remove");
    }

    public PlayStatus getPlayStatus(TransferStatus status) {
        MediaFile file = status.getMediaFile();
        if (file == null) {
            file = mediaFileService.getMediaFile(status.getExternalFile());
        }
        return new PlayStatus(status.getId(),
                file,
                status.getPlayer(),
                status.getMillisSinceLastUpdate());
    }

    private TransferStatus createStatus(Player player, List<TransferStatus> statusList) {
        TransferStatus status = new TransferStatus(player);
        statusList.add(status);
        return status;
    }

    private void broadcast(PlayStatus status, String location) {
        CompletableFuture.runAsync(() -> {
            NowPlayingInfo info = NowPlayingInfo.createForBroadcast(status, settingsService);

            if (info != null) {
                messagingTemplate.convertAndSend("/topic/nowPlaying/" + location, info);
            }
        });
    }

    public List<NowPlayingInfo> getActivePlays() {
        return activeLocalPlays.parallelStream()
                .map(s -> NowPlayingInfo.createForBroadcast(s, settingsService))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<NowPlayingInfo> getInactivePlays() {
        return Stream
                .concat(remotePlays.parallelStream(),
                        inactiveStreamStatuses.values().parallelStream().map(ts -> getPlayStatus(ts)))
                .map(s -> NowPlayingInfo.createForBroadcast(s, settingsService))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
}
