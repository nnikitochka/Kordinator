package su.nnedition.kord.service;

import chameleon.playlist.SpecificPlaylist;

import su.nnedition.kord.dao.MediaFileDao;
import su.nnedition.kord.dao.PlaylistDao;
import su.nnedition.kord.domain.Playlist;
import su.nnedition.kord.service.media.MediaFileService;
import su.nnedition.kord.service.media.MediaFolderService;
import su.nnedition.kord.service.playlist.DefaultPlaylistExportHandler;
import su.nnedition.kord.service.playlist.DefaultPlaylistImportHandler;
import su.nnedition.kord.service.playlist.XspfPlaylistExportHandler;
import su.nnedition.kord.service.playlist.XspfPlaylistImportHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PlaylistServiceHandlerOrderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void specializedHandlersTakePriorityWhenConstructedManually() throws Exception {
        Fixture fixture = new Fixture();
        PlaylistService service = new PlaylistService(fixture.mediaFileDao, fixture.playlistDao,
                fixture.securityService, fixture.settingsService,
                List.of(fixture.defaultExport, fixture.xspfExport),
                List.of(fixture.defaultImport, fixture.xspfImport),
                fixture.pathWatcherService);

        fixture.verifyHandlerSelection(service);
    }

    private class Fixture {
        final MediaFileDao mediaFileDao = mock(MediaFileDao.class);
        final PlaylistDao playlistDao = mock(PlaylistDao.class);
        final SecurityService securityService = mock(SecurityService.class);
        final SettingsService settingsService = mock(SettingsService.class);
        final PathWatcherService pathWatcherService = mock(PathWatcherService.class);
        final MediaFileService mediaFileService = mock(MediaFileService.class);
        final MediaFolderService mediaFolderService = mock(MediaFolderService.class);
        final DefaultPlaylistExportHandler defaultExport = spy(new DefaultPlaylistExportHandler(mediaFileDao, mediaFolderService));
        final XspfPlaylistExportHandler xspfExport = spy(new XspfPlaylistExportHandler(mediaFileDao, playlistDao,
                mediaFolderService, mock(CoverArtService.class)));
        final DefaultPlaylistImportHandler defaultImport = spy(new DefaultPlaylistImportHandler(mediaFileService, settingsService));
        final XspfPlaylistImportHandler xspfImport = spy(new XspfPlaylistImportHandler(mediaFileService, settingsService));
        final Playlist playlist = new Playlist();

        Fixture() throws Exception {
            playlist.setId(23);
            playlist.setName("Handler priority");
            playlist.setUsername("testUser");
            playlist.setShared(true);
            doReturn(mock(SpecificPlaylist.class)).when(xspfExport).handle(eq(23), any());
            when(settingsService.getPlaylistExportFormat()).thenReturn("xspf");
            when(settingsService.getPlaylistFolder()).thenReturn(folder.getRoot().toPath().resolve("missing").toString());
            when(playlistDao.getPlaylist(23)).thenReturn(playlist);
            when(playlistDao.getPlaylistFileStats(23)).thenReturn(Pair.of(0, 0.0));
            when(mediaFileDao.getFilesInPlaylist(23)).thenReturn(List.of());
        }

        void verifyHandlerSelection(PlaylistService service) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            service.exportPlaylist(23, output);
            verify(xspfExport).handle(eq(23), any());
            verify(defaultExport, never()).handle(eq(23), any());

            String xspf = "<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\"><trackList/></playlist>";
            service.importPlaylist("testUser", "Handler priority", "test.xspf", Path.of("test.xspf"),
                    new ByteArrayInputStream(xspf.getBytes(StandardCharsets.UTF_8)), playlist);
            verify(xspfImport).handle(any(), eq(Path.of("test.xspf")));
            verify(defaultImport, never()).handle(any(), any());
            verify(playlistDao).setFilesInPlaylist(23, List.of());
        }
    }
}
