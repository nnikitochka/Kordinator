package org.airsonic.player.service;

import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.UserSettings;
import org.junit.Test;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PlayQueueBroadcastTest {
    @Test
    public void webPlaybackBroadcastsQueueButTargetsStartToOriginatingSession() {
        MediaFileService mediaFiles = mock(MediaFileService.class);
        SettingsService settings = mock(SettingsService.class);
        SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
        Player player = mock(Player.class);
        PlayQueue queue = new PlayQueue(id -> null);
        MediaFile file = mock(MediaFile.class);
        UserSettings userSettings = mock(UserSettings.class);
        when(player.getId()).thenReturn(42);
        when(player.getUsername()).thenReturn("listener");
        when(player.getPlayQueue()).thenReturn(queue);
        when(player.isWeb()).thenReturn(true);
        when(file.isFile()).thenReturn(true);
        when(mediaFiles.getMediaFile(7)).thenReturn(file);
        when(settings.getUserSettings("listener")).thenReturn(userSettings);
        PlayQueueService service = new PlayQueueService(mock(SearchService.class), mock(JukeboxService.class), settings,
                mediaFiles, mock(LastFmService.class), mock(RatingService.class), mock(PlaylistService.class),
                mock(MediaFileDao.class), broker, mock(MediaFolderService.class), mock(PlayQueueDao.class), mock(JWTSecurityService.class));

        service.playMediaFile(player, 7, PlayQueueService.PlaybackTarget.websocket("origin-session"));

        verify(broker, timeout(5000)).convertAndSendToUser(eq("listener"), eq("/queue/playqueues/42/updated"), any());
        var headers = org.mockito.ArgumentCaptor.forClass(MessageHeaders.class);
        verify(broker, timeout(5000)).convertAndSendToUser(eq("listener"), eq("/queue/playqueues/42/skip"),
                eq(Map.of("index", 0, "offset", 0L)), headers.capture());
        assertEquals("origin-session", headers.getValue().get("simpSessionId"));
        assertEquals(1, queue.size());
    }
    @Test
    public void webPlaybackReturnsStartWithoutBroadcastingSkip() {
        MediaFileService mediaFiles = mock(MediaFileService.class);
        SettingsService settings = mock(SettingsService.class);
        SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
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
                mock(MediaFileDao.class), broker, mock(MediaFolderService.class), mock(PlayQueueDao.class), mock(JWTSecurityService.class));

        var result = service.playMediaFile(player, 7, PlayQueueService.PlaybackTarget.response());
        assertEquals(0, result.getStartPlayerAt());
        assertEquals(0L, result.getStartPlayerAtPosition());
        assertTrue(result.getRevision() > 0);
        verify(broker, timeout(5000)).convertAndSendToUser(eq("listener"), eq("/queue/playqueues/42/updated"), any());
        verify(broker, after(100).never()).convertAndSendToUser(eq("listener"), eq("/queue/playqueues/42/skip"), any());
        verify(broker, after(100).never()).convertAndSendToUser(eq("listener"), eq("/queue/playqueues/42/skip"), any(), any(MessageHeaders.class));
    }
}
