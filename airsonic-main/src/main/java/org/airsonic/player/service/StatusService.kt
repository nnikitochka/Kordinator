package org.airsonic.player.service

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.domain.Player
import org.airsonic.player.domain.TransferStatus
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

/** Maintains stream, download, upload and playback statuses. */
open class StatusService(
    private var mediaFileService: MediaFileService,
    private val mediaFolderService: MediaFolderService,
    private var settingsService: SettingsService,
    private val taskService: TaskSchedulingService
) {
    private val streamStatuses: MutableList<TransferStatus> = Collections.synchronizedList(ArrayList())
    private val downloadStatuses: MutableList<TransferStatus> = Collections.synchronizedList(ArrayList())
    private val uploadStatuses: MutableList<TransferStatus> = Collections.synchronizedList(ArrayList())
    private val remotePlays: MutableSet<PlayStatus> = ConcurrentHashMap.newKeySet()
    private val activeLocalPlays: MutableSet<PlayStatus> = ConcurrentHashMap.newKeySet()
    private val inactiveStreamStatuses = ConcurrentHashMap<Int, TransferStatus>()

    open fun cleanup() {
        taskService.scheduleFixedDelayTask(
            "remote-playstatus-cleanup",
            this::cleanupRemotePlays,
            Instant.now().plus(3, ChronoUnit.HOURS),
            Duration.ofHours(3),
            true
        )
    }

    open fun createStreamStatus(player: Player): TransferStatus = createStatus(player, streamStatuses)

    open fun removeStreamStatus(status: TransferStatus) {
        if (status.mediaFile == null && status.externalFile == null) {
            streamStatuses.remove(status)
            status.isActive = false
            return
        }
        inactiveStreamStatuses.compute(status.player!!.id!!) { _, _ ->
            streamStatuses.remove(status)
            status.isActive = false
            status
        }
    }

    open fun getAllStreamStatuses(): List<TransferStatus> {
        val snapshot = ArrayList(streamStatuses)
        val playerIds = snapshot.parallelStream()
            .map { it.player!!.id }
            .collect(Collectors.toCollection { ConcurrentHashMap.newKeySet() })
        return Stream.concat(
            snapshot.parallelStream(),
            inactiveStreamStatuses.values.parallelStream().filter { !playerIds.contains(it.player!!.id) }
        ).collect(Collectors.toList())
    }

    open fun getStreamStatusesForPlayer(player: Player): List<TransferStatus> = streamStatuses.parallelStream()
        .filter { it.player!!.id == player.id }
        .collect(Collectors.toList())

    open fun getInactiveStreamStatusForPlayer(player: Player): TransferStatus? = inactiveStreamStatuses[player.id]

    open fun createDownloadStatus(player: Player): TransferStatus = createStatus(player, downloadStatuses)

    open fun removeDownloadStatus(status: TransferStatus) {
        downloadStatuses.remove(status)
    }

    open fun getAllDownloadStatuses(): List<TransferStatus> = ArrayList(downloadStatuses)

    open fun createUploadStatus(player: Player): TransferStatus = createStatus(player, uploadStatuses)

    open fun removeUploadStatus(status: TransferStatus) {
        uploadStatuses.remove(status)
    }

    open fun getAllUploadStatuses(): List<TransferStatus> = ArrayList(uploadStatuses)

    open fun cleanupRemotePlays() {
        val expired = remotePlays.parallelStream().filter(PlayStatus::isExpired).collect(Collectors.toSet())
        remotePlays.removeAll(expired)
    }

    open fun addRemotePlay(playStatus: PlayStatus) {
        remotePlays.add(playStatus)
    }

    open fun addActiveLocalPlay(status: PlayStatus) {
        activeLocalPlays.add(status)
    }

    open fun removeActiveLocalPlay(status: PlayStatus) {
        activeLocalPlays.remove(status)
    }

    open fun getPlayStatus(status: TransferStatus): PlayStatus {
        val file: MediaFile? = status.mediaFile ?: mediaFileService.getMediaFile(status.externalFile)
        return PlayStatus(status.id, file, status.player, status.millisSinceLastUpdate)
    }

    private fun createStatus(player: Player, statusList: MutableList<TransferStatus>): TransferStatus =
        TransferStatus(player).also(statusList::add)

    open fun getActivePlays(): List<PlayStatus> = activeLocalPlays.parallelStream()
        .filter(::isVisibleNowPlaying)
        .collect(Collectors.toList())

    open fun getInactivePlays(): List<PlayStatus> = Stream.concat(
        remotePlays.parallelStream(),
        inactiveStreamStatuses.values.parallelStream().map(this::getPlayStatus)
    ).filter(::isVisibleNowPlaying)
        .collect(Collectors.toList())

    private fun isVisibleNowPlaying(play: PlayStatus): Boolean {
        if (play.mediaFile == null || play.minutesAgo > MAX_NOW_PLAYING_AGE_MINUTES) return false
        val username = play.player?.username ?: return false
        return settingsService.getUserSettings(username).nowPlayingAllowed
    }

    open fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    open fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    companion object {
        private const val MAX_NOW_PLAYING_AGE_MINUTES = 60
    }
}
