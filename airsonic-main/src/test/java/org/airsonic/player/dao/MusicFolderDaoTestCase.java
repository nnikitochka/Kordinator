package org.airsonic.player.dao;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test of {@link MusicFolderDao}.
 *
 * @author Sindre Mehus
 */
public class MusicFolderDaoTestCase extends DaoTestCaseBean2 {

    @Autowired
    MusicFolderDao musicFolderDao;

    @Before
    public void setUp() {
        getJdbcTemplate().execute("delete from music_folder");
    }

    @Test
    public void testCreateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        musicFolderDao.createMusicFolder(musicFolder);

        // no duplicates
        musicFolderDao.createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        assertThat(musicFolderDao.getAllMusicFolders()).usingElementComparatorIgnoringFields("id")
                .containsExactly(musicFolder);
    }

    @Test
    public void testUpdateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        musicFolderDao.createMusicFolder(musicFolder);
        musicFolder = musicFolderDao.getAllMusicFolders().get(0);

        musicFolder.setPath(Paths.get("newPath"));
        musicFolder.setName("newName");
        musicFolder.setEnabled(false);
        musicFolder.setChanged(Instant.ofEpochMilli(234234L));
        musicFolderDao.updateMusicFolder(musicFolder);

        assertThat(musicFolderDao.getAllMusicFolders()).element(0).isEqualToComparingFieldByField(musicFolder);
    }

    @Test
    public void testDeleteMusicFolder() {
        assertThat(musicFolderDao.getAllMusicFolders()).hasSize(0);

        musicFolderDao.createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        List<MusicFolder> musicFolders = musicFolderDao.getAllMusicFolders();
        assertThat(musicFolders).hasSize(1);

        musicFolderDao.deleteMusicFolder(musicFolders.get(0).getId());
        assertThat(musicFolderDao.getAllMusicFolders()).hasSize(0);
    }
}