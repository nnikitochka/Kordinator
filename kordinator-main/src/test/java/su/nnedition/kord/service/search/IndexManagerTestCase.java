package su.nnedition.kord.service.search;

import su.nnedition.kord.dao.AlbumDao;
import su.nnedition.kord.dao.ArtistDao;
import su.nnedition.kord.dao.MediaFileDao;
import su.nnedition.kord.domain.MediaFile;
import su.nnedition.kord.domain.MusicFolder;
import su.nnedition.kord.domain.MusicFolder.Type;
import su.nnedition.kord.domain.SearchCriteria;
import su.nnedition.kord.domain.SearchResult;
import su.nnedition.kord.util.MusicFolderTestData;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class IndexManagerTestCase extends AbstractKordinatorHomeTest {

    private List<MusicFolder> musicFolders;

    @Override
    public List<MusicFolder> getMusicFolders() {
        if (musicFolders == null || musicFolders.isEmpty()) {
            musicFolders = new ArrayList<>();
            Path musicDir = MusicFolderTestData.resolveMusicFolderPath();
            musicFolders.add(new MusicFolder(1, musicDir, "Music", Type.MEDIA, true, Instant.now()));
        }
        return musicFolders;
    }

    private static UUID cleanupId = null;

    @Before
    public void setup() {
        UUID id = populateDatabaseOnlyOnce();
        if (id != null) {
            cleanupId = id;
        }
    }

    @AfterClass
    public static void cleanup() {
        AbstractKordinatorHomeTest.cleanup(cleanupId);
        cleanupId = null;
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testExpunge() {

        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(0);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery("_DIR_ Ravel");

        SearchCriteria criteriaSong = new SearchCriteria();
        criteriaSong.setOffset(0);
        criteriaSong.setCount(Integer.MAX_VALUE);
        criteriaSong.setQuery("Gaspard");

        SearchCriteria criteriaAlbumId3 = new SearchCriteria();
        criteriaAlbumId3.setOffset(0);
        criteriaAlbumId3.setCount(Integer.MAX_VALUE);
        criteriaAlbumId3.setQuery("Complete Piano Works");

        /* Delete DB record. */

        // artist
        SearchResult result = searchService.search(criteria, musicFolders, IndexType.ARTIST);
        assertThat(result.getMediaFiles()).extracting(MediaFile::getName)
                .containsExactlyInAnyOrder("_DIR_ Ravel", "_DIR_ Sixteen Horsepower");

        List<Integer> candidates = daoGraph.getMediaFileDao().getArtistExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> daoGraph.getMediaFileDao().deleteMediaFile(a.getPath(), a.getFolderId()));

        candidates = daoGraph.getMediaFileDao().getArtistExpungeCandidates();
        assertEquals(2, candidates.size());

        // album
        result = searchService.search(criteria, musicFolders, IndexType.ALBUM);
        assertThat(result.getMediaFiles()).extracting(MediaFile::getName)
                .containsExactlyInAnyOrder("_DIR_ Ravel - Complete Piano Works", "_DIR_ Ravel - Chamber Music With Voice");

        candidates = daoGraph.getMediaFileDao().getAlbumExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> daoGraph.getMediaFileDao().deleteMediaFile(a.getPath(), a.getFolderId()));

        candidates = daoGraph.getMediaFileDao().getAlbumExpungeCandidates();
        assertEquals(2, candidates.size());

        // song
        result = searchService.search(criteriaSong, musicFolders, IndexType.SONG);
        assertThat(result.getMediaFiles()).extracting(MediaFile::getName)
                .containsExactlyInAnyOrder("01 - Gaspard de la Nuit - i. Ondine", "02 - Gaspard de la Nuit - ii. Le Gibet");

        candidates = daoGraph.getMediaFileDao().getSongExpungeCandidates();
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(a -> daoGraph.getMediaFileDao().deleteMediaFile(a.getPath(), a.getFolderId()));

        candidates = daoGraph.getMediaFileDao().getSongExpungeCandidates();
        assertEquals(2, candidates.size());

        // artistid3
        result = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
        assertEquals(1, result.getArtists().size());
        assertEquals("_DIR_ Ravel", result.getArtists().get(0).getName());

        candidates = daoGraph.getArtistDao().getExpungeCandidates();
        assertEquals(0, candidates.size());

        daoGraph.getArtistDao().markNonPresent(Instant.now());

        candidates = daoGraph.getArtistDao().getExpungeCandidates();
        assertEquals(4, candidates.size());

        // albumId3
        result = searchService.search(criteriaAlbumId3, musicFolders, IndexType.ALBUM_ID3);
        assertEquals(1, result.getAlbums().size());
        assertEquals("Complete Piano Works", result.getAlbums().get(0).getName());

        candidates = daoGraph.getAlbumDao().getExpungeCandidates();
        assertEquals(0, candidates.size());

        daoGraph.getAlbumDao().markNonPresent(Instant.now());

        candidates = daoGraph.getAlbumDao().getExpungeCandidates();
        assertEquals(4, candidates.size());

        /* Does not scan, only expunges the index. */
        indexManager.startIndexing();
        indexManager.expunge();
        indexManager.stopIndexing(indexManager.getStatistics());

        /*
         * Subsequent search results.
         * Results can also be confirmed with Luke.
         */

        result = searchService.search(criteria, musicFolders, IndexType.ARTIST);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteria, musicFolders, IndexType.ALBUM);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteriaSong, musicFolders, IndexType.SONG);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
        assertEquals(0, result.getArtists().size());

        result = searchService.search(criteriaAlbumId3, musicFolders, IndexType.ALBUM_ID3);
        assertEquals(0, result.getAlbums().size());

    }

}
