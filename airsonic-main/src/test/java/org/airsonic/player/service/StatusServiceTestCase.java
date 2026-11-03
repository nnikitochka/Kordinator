package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.UserSettings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Unit test of {@link StatusService}.
 *
 * @author Sindre Mehus
 */
@RunWith(MockitoJUnitRunner.class)
public class StatusServiceTestCase {

    private StatusService service;
    private Player player1;
    private Player player2;

    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private TaskSchedulingService taskSchedulingService;
    @Mock
    private UserSettings settings;

    @Before
    public void setUp() {
        doReturn(new MediaFile()).when(mediaFileService).getMediaFile(any(Path.class));
        doReturn(settings).when(settingsService).getUserSettings(any(String.class));
        doReturn(true).when(settings).getNowPlayingAllowed();
        service = new StatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
        player1 = new Player();
        player1.setId(1);
        player1.setUsername("p1");
        player2 = new Player();
        player2.setId(2);
        player2.setUsername("p2");
    }

    @Test
    public void testSimpleAddRemoveTransferStatus() {
        TransferStatus status = service.createStreamStatus(player1);
        status.setExternalFile(Paths.get("bla"));
        assertThat(status.isActive()).isTrue();
        assertThat(service.getAllStreamStatuses()).containsExactly(status);
        assertThat(service.getStreamStatusesForPlayer(player1)).containsExactly(status);
        assertThat(service.getStreamStatusesForPlayer(player2)).isEmpty();
        assertThat(service.getInactivePlays()).isEmpty();
        // won't start until file starts playing
        assertThat(service.getActivePlays()).isEmpty();

        service.removeStreamStatus(status);
        assertThat(status.isActive()).isFalse();
        assertThat(service.getAllStreamStatuses()).containsExactly(status);
        assertThat(service.getStreamStatusesForPlayer(player1)).isEmpty();
        assertThat(service.getStreamStatusesForPlayer(player2)).isEmpty();
        assertThat(service.getInactivePlays()).isNotEmpty();
        assertThat(service.getActivePlays()).isEmpty();
    }

    @Test
    public void testSimpleAddRemovePlayStatus() {
        PlayStatus status = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, 0);
        service.addActiveLocalPlay(status);
        assertThat(service.getInactivePlays()).isEmpty();
        assertThat(service.getActivePlays()).isNotEmpty();

        service.removeActiveLocalPlay(status);
        assertThat(service.getInactivePlays()).isEmpty();
        assertThat(service.getActivePlays()).isEmpty();

        service.addRemotePlay(status);
        assertThat(service.getInactivePlays()).isNotEmpty();
        assertThat(service.getActivePlays()).isEmpty();
    }

    @Test
    public void testFilteredStatusesAreNotVisible() {
        // No media file
        TransferStatus tStatus = service.createStreamStatus(player1);
        service.removeStreamStatus(tStatus);

        PlayStatus pStatus = new PlayStatus(UUID.randomUUID(), null, player1, 0);
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        // Old status
        pStatus = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, Instant.now().minus(75, ChronoUnit.MINUTES));
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        // User settings
        doReturn(false).when(settings).getNowPlayingAllowed();

        pStatus = new PlayStatus(UUID.randomUUID(), new MediaFile(), player1, 0);
        service.addActiveLocalPlay(pStatus);
        service.removeActiveLocalPlay(pStatus);
        service.addRemotePlay(pStatus);

        tStatus = service.createStreamStatus(player1);
        tStatus.setExternalFile(Paths.get("bla"));
        service.removeStreamStatus(tStatus);

        assertThat(service.getActivePlays()).isEmpty();
        assertThat(service.getInactivePlays()).isEmpty();
    }

    @Test
    public void testMultipleStreamsSamePlayer() {
        TransferStatus statusA = service.createStreamStatus(player1);
        TransferStatus statusB = service.createStreamStatus(player1);
        statusA.setExternalFile(Paths.get("a"));
        statusB.setExternalFile(Paths.get("b"));

        assertThat(service.getAllStreamStatuses()).containsExactly(statusA, statusB);
        assertThat(service.getStreamStatusesForPlayer(player1)).containsExactly(statusA, statusB);

        // Stop stream A.
        service.removeStreamStatus(statusA);
        assertThat(statusA.isActive()).isFalse();
        assertThat(statusB.isActive()).isTrue();
        assertThat(service.getAllStreamStatuses()).containsExactly(statusB);
        assertThat(service.getStreamStatusesForPlayer(player1)).containsExactly(statusB);

        // Stop stream B.
        service.removeStreamStatus(statusB);
        assertThat(statusB.isActive()).isFalse();
        assertThat(service.getAllStreamStatuses()).containsExactly(statusB);
        assertThat(service.getStreamStatusesForPlayer(player1)).isEmpty();

        // Start stream C.
        TransferStatus statusC = service.createStreamStatus(player1);
        assertThat(statusC.isActive()).isTrue();
        assertThat(service.getAllStreamStatuses()).containsExactly(statusC);
        assertThat(service.getStreamStatusesForPlayer(player1)).containsExactly(statusC);
    }
}
