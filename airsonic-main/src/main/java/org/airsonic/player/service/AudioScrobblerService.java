package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.scrobbler.LastFMScrobbler;
import org.airsonic.player.service.scrobbler.ListenBrainzScrobbler;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

/**
 * Provides services for "audioscrobbling", which is the process of
 * registering what songs are played at website.
 */
public class AudioScrobblerService {
    private LastFMScrobbler lastFMScrobbler;
    private ListenBrainzScrobbler listenBrainzScrobbler;
    private SettingsService settingsService;
    private SecurityService securityService;

    public AudioScrobblerService(SettingsService settingsService, SecurityService securityService) {
        this.settingsService = settingsService;
        this.securityService = securityService;
    }

    /**
     * Registers the given media file at audio scrobble service.
     * This method returns immediately, the actual registration is done by separate threads.
     *
     * @param mediaFile  The media file to register.
     * @param username   The user which played the music file.
     * @param submission Whether this is a submission or a now playing notification.
     * @param time       Event time, or {@code null} to use current time.
     */

    public synchronized void register(MediaFile mediaFile, String username, boolean submission, Instant time) {
        if (mediaFile == null || mediaFile.isVideo()) {
            return;
        }

        UserSettings userSettings = settingsService.getUserSettings(username);

        EnumSet<App> enabledApps = EnumSet.noneOf(App.class);
        if (userSettings.getLastFmEnabled()) {
            enabledApps.add(App.LASTFM);
        }

        if (userSettings.getListenBrainzEnabled()) {
            enabledApps.add(App.LISTENBRAINZ);
        }

        Map<App, UserCredential> creds = securityService.getDecodableCredsForApps(username, enabledApps.toArray(new App[0]));

        UserCredential cred = creds.get(App.LASTFM);
        if (cred != null) {
            if (lastFMScrobbler == null) {
                lastFMScrobbler = new LastFMScrobbler();
            }
            String decoded = securityService.decodeCredentials(cred);
            if (decoded != null) {
                lastFMScrobbler.register(mediaFile, cred.getAppUsername(), decoded, submission, time);
            }
        }

        cred = creds.get(App.LISTENBRAINZ);
        if (cred != null) {
            if (listenBrainzScrobbler == null) {
                listenBrainzScrobbler = new ListenBrainzScrobbler();
            }
            String decoded = securityService.decodeCredentials(cred);
            if (decoded != null) {
                listenBrainzScrobbler.register(mediaFile, userSettings.getListenBrainzUrl(), decoded, submission, time);
            }
        }
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }
}
