package su.nnedition.kord.service

import org.apache.commons.lang.ObjectUtils
import org.apache.commons.lang.RandomStringUtils
import su.nnedition.kord.dao.ShareDao
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.domain.Share
import su.nnedition.kord.domain.User
import su.nnedition.kord.service.media.MediaFileService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Provides services for sharing media. */
open class ShareService(
    private var shareDao: ShareDao,
    private var mediaFileService: MediaFileService
) {
    open fun getAllShares(): List<Share> = shareDao.getAllShares()

    open fun getSharesForUser(user: User): List<Share> = getAllShares()
        .filter { user.isAdminRole || ObjectUtils.equals(user.username, it.username) }

    open fun getShareById(id: Int): Share? = shareDao.getShareById(id)

    open fun getShareByName(name: String): Share? = shareDao.getShareByName(name)

    open fun getSharedFiles(id: Int, musicFolders: List<MusicFolder>): List<MediaFile> =
        shareDao.getSharedFiles(id, musicFolders).mapNotNull(mediaFileService::getMediaFile)

    open fun createShare(username: String, files: List<MediaFile>): Share {
        val share = Share().apply {
            name = RandomStringUtils.random(5, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            created = Instant.now()
            this.username = username
            expires = Instant.now().plus(ChronoUnit.YEARS.duration)
        }

        shareDao.createShare(share)
        shareDao.createSharedFiles(share.id, files.map { it.id })
        LOG.info("Created share '{}' with {} file(s).", share.name, files.size)
        return share
    }

    open fun updateShare(share: Share) {
        shareDao.updateShare(share)
    }

    open fun deleteShare(id: Int) {
        shareDao.deleteShare(id)
    }

    open fun setShareDao(shareDao: ShareDao) {
        this.shareDao = shareDao
    }

    open fun setMediaFileService(mediaFileService: MediaFileService) {
        this.mediaFileService = mediaFileService
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ShareService::class.java)
    }
}
