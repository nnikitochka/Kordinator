package org.airsonic.player.dao;

import org.airsonic.player.domain.Bookmark;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides database services for media file bookmarks.
 */
@Repository
public class BookmarkDao extends AbstractDao {

    private static final String INSERT_COLUMNS = "media_file_id, position_millis, username, comment, created, changed";
    private static final String QUERY_COLUMNS = "id, " + INSERT_COLUMNS;
    private static final int LOCK_STRIPES = 256;

    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
    private final BookmarkRowMapper bookmarkRowMapper = new BookmarkRowMapper();

    public BookmarkDao(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @PostConstruct
    public void register() throws Exception {
        registerInserts("bookmark", "id", Arrays.asList(INSERT_COLUMNS.split(", ")), Bookmark.class);
    }

    /**
     * Returns all bookmarks.
     *
     * @return Possibly empty list of all bookmarks.
     */
    public List<Bookmark> getBookmarks() {
        String sql = "select " + QUERY_COLUMNS + " from bookmark";
        return query(sql, bookmarkRowMapper);
    }

    /**
     * Returns all bookmarks for a given user.
     *
     * @return Possibly empty list of all bookmarks for the user.
     */
    public List<Bookmark> getBookmarks(String username) {
        String sql = "select " + QUERY_COLUMNS + " from bookmark where username=?";
        return query(sql, bookmarkRowMapper, username);
    }

    public Bookmark getBookmark(String username, int mediaFileId) {
        String sql = "select " + QUERY_COLUMNS + " from bookmark where username=? and media_file_id=?";
        return queryOne(sql, bookmarkRowMapper, username, mediaFileId);
    }

    /**
     * Creates or updates a bookmark.
     *
     * <p>Several requests for the same bookmark may arrive at once. The
     * operation is consequently serialized by username and media-file ID. The
     * transaction is completed while the lock is held, so the next request sees
     * the inserted row and updates it by primary key. Updating by primary key also
     * avoids an HSQLDB issue when a unique-constrained column is used in an update
     * predicate.</p>
     *
     * If created, the ID of the bookmark will be set by this method.
     */
    public void createOrUpdateBookmark(Bookmark bookmark) {
        ReentrantLock lock = getLock(bookmark.getUsername(), bookmark.getMediaFileId());
        lock.lock();
        try {
            transactionTemplate.executeWithoutResult(status -> createOrUpdateBookmarkInTransaction(bookmark));
        } finally {
            lock.unlock();
        }
    }

    private void createOrUpdateBookmarkInTransaction(Bookmark bookmark) {
        Bookmark existing = getBookmark(bookmark.getUsername(), bookmark.getMediaFileId());
        if (existing == null) {
            Integer id = insert("bookmark", bookmark);
            bookmark.setId(id);
            return;
        }

        bookmark.setId(existing.getId());
        update("update bookmark set position_millis=?, comment=?, changed=? where id=?",
                bookmark.getPositionMillis(), bookmark.getComment(), bookmark.getChanged(), existing.getId());
    }

    /**
     * Deletes the bookmark for the given username and media file.
     */
    public void deleteBookmark(String username, int mediaFileId) {
        ReentrantLock lock = getLock(username, mediaFileId);
        lock.lock();
        try {
            transactionTemplate.executeWithoutResult(status ->
                    update("delete from bookmark where username=? and media_file_id=?", username, mediaFileId));
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock getLock(String username, int mediaFileId) {
        return locks[Math.floorMod(31 * mediaFileId + (username == null ? 0 : username.hashCode()), LOCK_STRIPES)];
    }

    private static class BookmarkRowMapper implements RowMapper<Bookmark> {
        @Override
        public Bookmark mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Bookmark(
                    rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                    Optional.ofNullable(rs.getTimestamp(6)).map(Timestamp::toInstant).orElse(null),
                    Optional.ofNullable(rs.getTimestamp(7)).map(Timestamp::toInstant).orElse(null)
            );
        }
    }
}
