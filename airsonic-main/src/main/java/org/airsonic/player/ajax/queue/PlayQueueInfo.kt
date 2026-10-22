package org.airsonic.player.ajax.queue;

import org.airsonic.player.ajax.media.MediaFileEntry;
import org.airsonic.player.domain.PlayQueue.RepeatStatus;
import org.airsonic.player.domain.PlayQueue.Status;

import java.util.List;

/**
 * The playlist of a player.
 */
public class PlayQueueInfo {
    private final List<MediaFileEntry> entries;
    private final Status playStatus;
    private final RepeatStatus repeatStatus;
    private final boolean shuffleRadioEnabled;
    private final float gain;
    private int startPlayerAt = -1;
    private long startPlayerAtPosition; // millis

    public PlayQueueInfo(
            List<MediaFileEntry> entries,
            Status playStatus,
            RepeatStatus repeatStatus,
            boolean shuffleRadioEnabled,
            float gain
    ) {
        this.entries = entries;
        this.playStatus = playStatus;
        this.repeatStatus = repeatStatus;
        this.shuffleRadioEnabled = shuffleRadioEnabled;
        this.gain = gain;
    }

    public List<MediaFileEntry> getEntries() {
        return entries;
    }

    public Status getPlayStatus() {
        return playStatus;
    }

    public RepeatStatus getRepeatStatus() {
        return repeatStatus;
    }

    public boolean isShuffleRadioEnabled() {
        return shuffleRadioEnabled;
    }

    public float getGain() {
        return gain;
    }

    public int getStartPlayerAt() {
        return startPlayerAt;
    }

    public PlayQueueInfo setStartPlayerAt(int startPlayerAt) {
        this.startPlayerAt = startPlayerAt;
        return this;
    }

    public long getStartPlayerAtPosition() {
        return startPlayerAtPosition;
    }

    public PlayQueueInfo setStartPlayerAtPosition(long startPlayerAtPosition) {
        this.startPlayerAtPosition = startPlayerAtPosition;
        return this;
    }
}
