package org.airsonic.player.service;

import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.UserSettings;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PlayQueueBroadcastTest {
    @Test
    public void jukeboxPlaybackStartsAtSelectedTrack() {
        MediaFileService media = mock(MediaFileService.class);
        SettingsService settings = mock(SettingsService.class);
        JukeboxService jukebox = mock(JukeboxService.class);
        Player player = mock(Player.class);
        PlayQueue queue = new PlayQueue(id -> null);
        MediaFile file = mock(MediaFile.class);
        when(player.getUsername()).thenReturn("listener");
        when(player.getPlayQueue()).thenReturn(queue);
        when(player.isJukebox()).thenReturn(true);
        when(file.isFile()).thenReturn(true);
        when(media.getMediaFile(7)).thenReturn(file);
        when(settings.getUserSettings("listener")).thenReturn(mock(UserSettings.class));
        PlayQueueService service = new PlayQueueService(mock(SearchService.class), jukebox, settings,
                media, mock(LastFmService.class), mock(RatingService.class), mock(PlaylistService.class),
                mock(MediaFileDao.class), mock(MediaFolderService.class), mock(PlayQueueDao.class), mock(JWTSecurityService.class));
        service.playMediaFile(player, 7, PlayQueueService.PlaybackTarget.response());
        verify(jukebox).skip(player, 0, 0);
    }
    @Test
    public void webPlaybackReturnsStartWithoutBroadcastingSkip() {
        MediaFileService mediaFiles = mock(MediaFileService.class);
        SettingsService settings = mock(SettingsService.class);
        Player player = mock(Player.class);
        PlayQueue queue = new PlayQueue(id -> null);
        MediaFile file = mock(MediaFile.class);
        when(player.getId()).thenReturn(42);
        when(player.getUsername()).thenReturn("listener");
        when(player.getPlayQueue()).thenReturn(queue);
        when(player.isWeb()).thenReturn(true);
        when(file.isFile()).thenReturn(true);
        when(mediaFiles.getMediaFile(7)).thenReturn(file);
        when(settings.getUserSettings("listener")).thenReturn(mock(UserSettings.class));
        PlayQueueService service = new PlayQueueService(mock(SearchService.class), mock(JukeboxService.class), settings,
                mediaFiles, mock(LastFmService.class), mock(RatingService.class), mock(PlaylistService.class),
                mock(MediaFileDao.class), mock(MediaFolderService.class), mock(PlayQueueDao.class), mock(JWTSecurityService.class));

        var result = service.playMediaFile(player, 7, PlayQueueService.PlaybackTarget.response());
        assertEquals(0, result.getStartPlayerAt());
        assertEquals(0L, result.getStartPlayerAtPosition());
        assertTrue(result.getRevision() > 0);
    }
}
