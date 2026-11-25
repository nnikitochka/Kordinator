package su.nnedition.kord.service;

import su.nnedition.kord.service.media.MediaScannerService;

import su.nnedition.kord.dao.AlbumDao;
import su.nnedition.kord.dao.ArtistDao;
import su.nnedition.kord.dao.MediaFileDao;
import su.nnedition.kord.domain.MediaLibraryStatistics;
import su.nnedition.kord.service.media.MediaFileService;
import su.nnedition.kord.service.media.MediaFolderService;
import su.nnedition.kord.service.search.IndexManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MediaScannerServiceUnitTest {

    MediaScannerService mediaScannerService;

    @Mock
    IndexManager indexManager;

    @Before
    public void setUp() {
        mediaScannerService = new MediaScannerService(
                mock(PlaylistService.class),
                mock(SettingsService.class),
                indexManager,
                mock(MediaFileService.class),
                mock(MediaFolderService.class),
                mock(CoverArtService.class),
                mock(MediaFileDao.class),
                mock(ArtistDao.class),
                mock(AlbumDao.class),
                mock(TaskSchedulingService.class),
                1);
    }

    @Test
    public void neverScanned() {
        when(indexManager.getStatistics()).thenReturn(null);
        assertTrue(mediaScannerService.neverScanned());

        when(indexManager.getStatistics()).thenReturn(new MediaLibraryStatistics());
        assertFalse(mediaScannerService.neverScanned());
    }
}
