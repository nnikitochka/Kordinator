package su.nnedition.kord.dao;

import su.nnedition.kord.domain.MusicFolder;
import su.nnedition.kord.domain.MusicFolder.Type;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test of {@link MusicFolderDao}.
 *
 * @author Sindre Mehus
 */
public class MusicFolderDaoTestCase extends DaoTestCase {

    @Before
    public void setUp() {
        getJdbcHelper().update("delete from music_folder");
    }

    @Test
    public void testCreateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        daos().getMusicFolderDao().createMusicFolder(musicFolder);

        // no duplicates
        daos().getMusicFolderDao().createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        assertThat(daos().getMusicFolderDao().getAllMusicFolders()).usingElementComparatorIgnoringFields("id")
                .containsExactly(musicFolder);
    }

    @Test
    public void testUpdateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        daos().getMusicFolderDao().createMusicFolder(musicFolder);
        musicFolder = daos().getMusicFolderDao().getAllMusicFolders().get(0);

        musicFolder.setPath(Paths.get("newPath"));
        musicFolder.setName("newName");
        musicFolder.setEnabled(false);
        musicFolder.setChanged(Instant.ofEpochMilli(234234L));
        daos().getMusicFolderDao().updateMusicFolder(musicFolder);

        assertThat(daos().getMusicFolderDao().getAllMusicFolders()).element(0).isEqualToComparingFieldByField(musicFolder);
    }

    @Test
    public void testDeleteMusicFolder() {
        assertThat(daos().getMusicFolderDao().getAllMusicFolders()).hasSize(0);

        daos().getMusicFolderDao().createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        List<MusicFolder> musicFolders = daos().getMusicFolderDao().getAllMusicFolders();
        assertThat(musicFolders).hasSize(1);

        daos().getMusicFolderDao().deleteMusicFolder(musicFolders.get(0).getId());
        assertThat(daos().getMusicFolderDao().getAllMusicFolders()).hasSize(0);
    }
}
