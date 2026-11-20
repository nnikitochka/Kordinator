package org.airsonic.player.dao;

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test of {@link BookmarkDao}.
 */
public class BookmarkDaoTestCase extends DaoTestCase {

    private static final String USERNAME = "admin";

    private int mediaFileId;
    private int musicFolderId;

    @Before
    public void setUp() {
        getJdbcHelper().update("delete from bookmark");

        Instant now = Instant.now();
        MusicFolder folder = new MusicFolder(java.nio.file.Paths.get("/bookmark-test-" + UUID.randomUUID()), "Bookmark test",
                MusicFolder.Type.MEDIA, true, now);
        daos().getMusicFolderDao().createMusicFolder(folder);
        musicFolderId = folder.getId();

        MediaFile file = new MediaFile(null, "track-" + UUID.randomUUID(), folder.getId(), MediaFile.MediaType.MUSIC,
                "mp3", "Bookmark test track", null, null, null, null, null, null, null, null,
                false, 1.0, 0L, null, null, "", 0, null, null, now, now, now, now, true, 1, null, null);
        daos().getMediaFileDao().createOrUpdateMediaFile(file, ignored -> { });
        mediaFileId = file.getId();
    }

    @After
    public void tearDown() {
        getJdbcHelper().update("delete from bookmark where media_file_id=?", mediaFileId);
        getJdbcHelper().update("delete from media_file where id=?", mediaFileId);
        getJdbcHelper().update("delete from music_folder where id=?", musicFolderId);
    }

    @Test
    public void testConcurrentCreateOrUpdateCreatesOnlyOneBookmark() throws Exception {
        int requestCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<java.util.concurrent.Future<Object>> futures = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(position -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        daos().getBookmarkDao().createOrUpdateBookmark(bookmark(position));
                        return null;
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (java.util.concurrent.Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(daos().getBookmarkDao().getBookmarks(USERNAME))
                .hasSize(1)
                .allSatisfy(bookmark -> assertThat(bookmark.getMediaFileId()).isEqualTo(mediaFileId));
    }

    private Bookmark bookmark(int position) {
        Instant now = Instant.now();
        return new Bookmark(0, mediaFileId, position, USERNAME, "comment", now, now);
    }
}
