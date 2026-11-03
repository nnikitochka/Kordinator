package org.airsonic.player.service;

import org.airsonic.player.domain.UserSettings;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MusicIndexStateServiceTest {
    @Test public void selectionIsSavedEvenWhileScanningWithoutChangingTimestamp() {
        MediaScannerService scanner = mock(MediaScannerService.class);
        SettingsService settings = mock(SettingsService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        UserSettings userSettings = new UserSettings("listener");
        Instant changed = userSettings.getChanged();
        when(settings.getUserSettings("listener")).thenReturn(userSettings);
        when(scanner.isScanning()).thenReturn(true);
        MusicIndexStateService service = new MusicIndexStateService(scanner, settings, folders);

        assertEquals(-1L, service.lastModified("listener", 123));
        assertEquals(123, userSettings.getSelectedMusicFolderId());
        assertEquals(changed, userSettings.getChanged());
        verify(settings).getUserSettings("listener");
        verify(settings).updateUserSettings(userSettings);
        verifyNoInteractions(folders);
        assertFalse(service.selectMusicFolder("listener", null));
        verifyNoMoreInteractions(settings);
    }
}
