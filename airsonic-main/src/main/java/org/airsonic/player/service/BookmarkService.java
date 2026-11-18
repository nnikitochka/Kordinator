package org.airsonic.player.service;

import org.airsonic.player.dao.BookmarkDao;
import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

public class BookmarkService {
    private static final Logger LOG = LoggerFactory.getLogger(BookmarkService.class);
    private final BookmarkDao dao;
    private final MediaFileService mediaFileService;

    public BookmarkService(BookmarkDao dao, MediaFileService mediaFileService) {
        this.dao = dao;
        this.mediaFileService = mediaFileService;
    }

    public Bookmark getBookmark(String username, int mediaFileId) {
        return dao.getBookmark(username, mediaFileId);
    }

    public boolean setBookmark(String username, int mediaFileId, long positionMillis, String comment) {
        MediaFile mediaFile = this.mediaFileService.getMediaFile(mediaFileId);
        if (mediaFile == null) {
            return false;
        }
        long durationMillis = mediaFile.getDuration() == null ? 0L : mediaFile.getDuration().longValue() * 1000L;
        if (durationMillis > 0L && durationMillis - positionMillis < 60000L) {
            LOG.debug("Deleting bookmark for {} because it's close to the end", mediaFileId);
            deleteBookmark(username, mediaFileId);
            return false;
        }
        LOG.debug("Setting bookmark for {}: {}", mediaFileId, positionMillis);
        Instant now = Instant.now();
        Bookmark bookmark = new Bookmark(0, mediaFileId, positionMillis, username, comment, now, now);
        dao.createOrUpdateBookmark(bookmark);

        return true;
    }

    public void deleteBookmark(String username, int mediaFileId) {
        dao.deleteBookmark(username, mediaFileId);
    }

    public List<Bookmark> getBookmarks(String username) {
        return dao.getBookmarks(username);
    }
}
