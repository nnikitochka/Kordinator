package org.airsonic.player.service

import org.apache.commons.lang.ObjectUtils
import org.apache.commons.lang.RandomStringUtils
import org.airsonic.player.dao.ShareDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Share
import org.airsonic.player.domain.User
import org.airsonic.player.service.media.MediaFileService
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
