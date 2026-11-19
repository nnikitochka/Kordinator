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
    private org.airsonic.player.runtime.DaoGraph daoGraph;

    @Before
    public void setUp() {
        getJdbcHelper().update("delete from music_folder");
    }

    @Test
    public void testCreateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        daoGraph.getMusicFolderDao().createMusicFolder(musicFolder);

        // no duplicates
        daoGraph.getMusicFolderDao().createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        assertThat(daoGraph.getMusicFolderDao().getAllMusicFolders()).usingElementComparatorIgnoringFields("id")
                .containsExactly(musicFolder);
    }

    @Test
    public void testUpdateMusicFolder() {
        MusicFolder musicFolder = new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now());
        daoGraph.getMusicFolderDao().createMusicFolder(musicFolder);
        musicFolder = daoGraph.getMusicFolderDao().getAllMusicFolders().get(0);

        musicFolder.setPath(Paths.get("newPath"));
        musicFolder.setName("newName");
        musicFolder.setEnabled(false);
        musicFolder.setChanged(Instant.ofEpochMilli(234234L));
        daoGraph.getMusicFolderDao().updateMusicFolder(musicFolder);

        assertThat(daoGraph.getMusicFolderDao().getAllMusicFolders()).element(0).isEqualToComparingFieldByField(musicFolder);
    }

    @Test
    public void testDeleteMusicFolder() {
        assertThat(daoGraph.getMusicFolderDao().getAllMusicFolders()).hasSize(0);

        daoGraph.getMusicFolderDao().createMusicFolder(new MusicFolder(Paths.get("path"), "name", Type.MEDIA, true, Instant.now()));

        List<MusicFolder> musicFolders = daoGraph.getMusicFolderDao().getAllMusicFolders();
        assertThat(musicFolders).hasSize(1);

        daoGraph.getMusicFolderDao().deleteMusicFolder(musicFolders.get(0).getId());
        assertThat(daoGraph.getMusicFolderDao().getAllMusicFolders()).hasSize(0);
    }
}
