package org.airsonic.player.domain;

/**
 * Parameters used when transcoding videos.
 */
public class VideoTranscodingSettings {

    private final int width;
    private final int height;
    private final int timeOffset;
    private final double duration;

    private int audioTrackIndex = 1;

    // HLS stuff
    private int hlsSegmentIndex;
    private String hlsSegmentFilename;
    // TODO shouldn't be part of this file
    private String outputFilename;

    public VideoTranscodingSettings(int width, int height, int timeOffset, double duration) {
        this.width = width;
        this.height = height;
        this.timeOffset = timeOffset;
        this.duration = duration;
    }

    public VideoTranscodingSettings(int width, int height, int timeOffset, double duration, int audioTrackIndex,
            int hlsSegmentIndex, String hlsSegmentFilename, String outputFilename) {
        this(width, height, timeOffset, duration);
        this.audioTrackIndex = audioTrackIndex;
        this.hlsSegmentIndex = hlsSegmentIndex;
        this.hlsSegmentFilename = hlsSegmentFilename;
        this.outputFilename = outputFilename;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTimeOffset() {
        return timeOffset;
    }

    public double getDuration() {
        return duration;
    }

    public int getAudioTrackIndex() {
        return audioTrackIndex;
    }

    public int getHlsSegmentIndex() {
        return hlsSegmentIndex;
    }

    public String getHlsSegmentFilename() {
        return hlsSegmentFilename;
    }

    public String getOutputFilename() {
        return outputFilename;
    }

}
