package org.airsonic.player.service

import org.airsonic.player.dao.BookmarkDao
import org.airsonic.player.domain.Bookmark
import org.slf4j.LoggerFactory
import java.time.Instant

class BookmarkService(
    private val dao: BookmarkDao,
    private val mediaFileService: MediaFileService,
) {
    fun getBookmark(username: String, mediaFileId: Int): Bookmark? = dao.getBookmark(username, mediaFileId)

    fun setBookmark(username: String, mediaFileId: Int, positionMillis: Long, comment: String?): Boolean {
        val mediaFile = mediaFileService.getMediaFile(mediaFileId) ?: return false
        val durationMillis = mediaFile.duration?.toLong()?.times(1000L) ?: 0L
        if (durationMillis > 0L && durationMillis - positionMillis < 60_000L) {
            LOG.debug("Deleting bookmark for {} because it's close to the end", mediaFileId)
            deleteBookmark(username, mediaFileId)
            return false
        }

        LOG.debug("Setting bookmark for {}: {}", mediaFileId, positionMillis)
        val now = Instant.now()
        dao.createOrUpdateBookmark(Bookmark(0, mediaFileId, positionMillis, username, comment, now, now))
        return true
    }

    fun deleteBookmark(username: String, mediaFileId: Int) = dao.deleteBookmark(username, mediaFileId)

    fun getBookmarks(username: String): List<Bookmark> = dao.getBookmarks(username)

    companion object {
        private val LOG = LoggerFactory.getLogger(BookmarkService::class.java)
    }
}
