package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.jukebox.AudioPlayer;
import org.airsonic.player.service.jukebox.AudioPlayerFactory;
import org.airsonic.player.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Plays music on the local audio device.
 */
public class JukeboxLegacySubsonicService implements AudioPlayer.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(JukeboxLegacySubsonicService.class);

    private TranscodingService transcodingService;
    private AudioScrobblerService audioScrobblerService;
    private StatusService statusService;
    private SettingsService settingsService;
    private SecurityService securityService;
    private MediaFileService mediaFileService;

    private AudioPlayer audioPlayer;
    private Player player;
    private TransferStatus status;
    private PlayStatus playStatus;
    private MediaFile currentPlayingFile;
    private float gain = AudioPlayer.DEFAULT_GAIN;
    private int offset;

    public JukeboxLegacySubsonicService(TranscodingService transcodingService, AudioScrobblerService audioScrobblerService, StatusService statusService, SettingsService settingsService, SecurityService securityService, MediaFileService mediaFileService) {
        this.transcodingService = transcodingService;
        this.audioScrobblerService = audioScrobblerService;
        this.statusService = statusService;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
    }

    /**
     * Updates the jukebox by starting or pausing playback on the local audio device.
     *
     * @param player The player in question.
     * @param offset Start playing after this many seconds into the track.
     */
    public synchronized void updateJukebox(Player player, int offset) {
        User user = securityService.getUserByName(player.getUsername());
        if (!user.isJukeboxRole()) {
            LOG.warn(user.getUsername() + " is not authorized for jukebox playback.");
            return;
        }

        updatePlayback(player, offset);
    }

    /** Entry point for calls already authenticated and library-checked by the standalone Ktor API. */
    public synchronized void updateStandaloneJukebox(Player player, int offset) {
        updatePlayback(player, offset);
    }

    /** Releases the local audio device for an authenticated standalone Ktor jukebox owner. */
    public synchronized void releaseStandaloneJukebox(Player player) {
        if (this.player != player) {
            return;
        }
        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }
        if (currentPlayingFile != null) {
            onSongEnd(currentPlayingFile);
            currentPlayingFile = null;
        }
        this.player = null;
    }

    private void updatePlayback(Player player, int offset) {
        if (player.getPlayQueue().getStatus() == PlayQueue.Status.PLAYING) {
            this.player = player;
            MediaFile result;
            synchronized (player.getPlayQueue()) {
                result = player.getPlayQueue().getCurrentFile();
            }
            play(result, offset);
        } else {
            if (audioPlayer != null) {
                audioPlayer.pause();
            }
        }
    }

    private synchronized void play(MediaFile file, int offset) {
        InputStream in = null;
        try {

            // Resume if possible.
            boolean sameFile = file != null && file.equals(currentPlayingFile);
            boolean paused = audioPlayer != null && audioPlayer.getState() == AudioPlayer.State.PAUSED;
            if (sameFile && paused && offset == 0) {
                audioPlayer.play();
            } else {
                this.offset = offset;
                if (audioPlayer != null) {
                    audioPlayer.close();
                    if (currentPlayingFile != null) {
                        onSongEnd(currentPlayingFile);
                    }
                }

                if (file != null) {
                    double duration = file.getDuration() == null ? 0 : file.getDuration() - offset;
                    TranscodingService.Parameters parameters = new TranscodingService.Parameters(file, new VideoTranscodingSettings(0, 0, offset, duration));
                    String command = settingsService.getJukeboxCommand();
                    parameters.setTranscoding(new Transcoding(null, "Jukebox", null, null, command, null, null, false));
                    in = transcodingService.getTranscodedInputStream(parameters);
                    audioPlayer = AudioPlayerFactory.createAudioPlayer(in, this);
                    audioPlayer.setGain(gain);
                    audioPlayer.play();
                    onSongStart(file);
                }
            }

            currentPlayingFile = file;

        } catch (Exception x) {
            LOG.error("Error in jukebox: " + x, x);
            FileUtil.closeQuietly(in);
        }
    }

    @Override
    public synchronized void stateChanged(AudioPlayer audioPlayer, AudioPlayer.State state) {
        if (state == AudioPlayer.State.EOM) {
            player.getPlayQueue().next();
            MediaFile result;
            synchronized (player.getPlayQueue()) {
                result = player.getPlayQueue().getCurrentFile();
            }
            play(result, 0);
        }
    }

    public synchronized float getGain() {
        return gain;
    }

    public synchronized int getPosition() {
        return audioPlayer == null ? 0 : offset + audioPlayer.getPosition();
    }

    /**
     * Returns the player which currently uses the jukebox.
     *
     * @return The player, may be {@code null}.
     */
    public Player getPlayer() {
        return player;
    }

    private void onSongStart(MediaFile file) {
        LOG.info("{} starting jukebox for {} in folder {}", player.getUsername(), FileUtil.getShortPath(file.getRelativePath()), file.getFolderId());
        status = statusService.createStreamStatus(player);
        status.setMediaFile(file);
        status.addBytesTransferred(file.getFileSize());
        mediaFileService.incrementPlayCount(file);
        playStatus = new PlayStatus(status.getId(), file, status.getPlayer(), status.getMillisSinceLastUpdate());
        statusService.addActiveLocalPlay(playStatus);
        scrobble(file, false);
    }

    private void onSongEnd(MediaFile file) {
        LOG.info("{} stopping jukebox for {} in folder {}", player.getUsername(), FileUtil.getShortPath(file.getRelativePath()), file.getFolderId());
        if (playStatus != null) {
            statusService.removeActiveLocalPlay(playStatus);
            playStatus = null;
        }
        if (status != null) {
            statusService.removeStreamStatus(status);
            status = null;
        }
        scrobble(file, true);
    }

    private void scrobble(MediaFile file, boolean submission) {
        if (player.getClientId() == null) {  // Don't scrobble REST players.
            audioScrobblerService.register(file, player.getUsername(), submission, null);
        }
    }

    public synchronized void setGain(float gain) {
        this.gain = gain;
        if (audioPlayer != null) {
            audioPlayer.setGain(gain);
        }
    }

    public void setTranscodingService(TranscodingService transcodingService) {
        this.transcodingService = transcodingService;
    }

    public void setAudioScrobblerService(AudioScrobblerService audioScrobblerService) {
        this.audioScrobblerService = audioScrobblerService;
    }

    public void setStatusService(StatusService statusService) {
        this.statusService = statusService;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }
}
