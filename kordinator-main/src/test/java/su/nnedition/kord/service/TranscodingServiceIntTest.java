package su.nnedition.kord.service;

import su.nnedition.kord.domain.Transcoding;
import su.nnedition.kord.runtime.RuntimeDatabaseRule;
import org.junit.ClassRule;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class TranscodingServiceIntTest {

    @ClassRule
    public static final RuntimeDatabaseRule database = new RuntimeDatabaseRule();

    @Test
    public void createTranscodingTest() {
        var runtime = database.getRuntime();
        var playerService = mock(PlayerService.class);
        var transcodingService = runtime.createTranscodingService(
                database.getPersistence().getDaos().getTranscodingDao(), runtime.createSettingsService(),
                runtime.createMediaFolderService(), () -> playerService);
        // Given
        Transcoding transcoding = new Transcoding(null,
                "test-transcoding",
                "mp3",
                "wav",
                "step1",
                "step2",
                "step3",
                true);

        transcodingService.createTranscoding(transcoding);
        verify(playerService).getAllPlayers();
    }
}
