package org.airsonic.player.service.settings

import java.time.Instant
import java.util.function.Function
import javax.cache.Cache
import org.airsonic.player.cache.CacheHelper
import org.airsonic.player.dao.AvatarDao
import org.airsonic.player.dao.UserDao
import org.airsonic.player.domain.AlbumListType
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.UserSettings

/** Owns user preference defaults, persistence, cache invalidation, and avatar access. */
internal class SettingsUserPreferences(
    private var userDao: UserDao,
    private var avatarDao: AvatarDao,
    private val userSettingsCache: Cache<String, UserSettings>?,
) : SettingsUserAccess {
    override fun getUserSettings(username: String): UserSettings = requireNotNull(
        CacheHelper.getOrCompute(userSettingsCache, username, Function { user ->
            user?.let { userDao.getUserSettings(it) ?: createDefaultUserSettings(it) }
        })
    )

    override fun updateUserSettings(settings: UserSettings?) {
        if (userSettingsCache != null && settings?.username != null) {
            userSettingsCache.remove(settings.username)
        }
        userDao.updateUserSettings(settings!!)
    }

    override fun getAllSystemAvatars(): List<Avatar> = avatarDao.getAllSystemAvatars()

    override fun getSystemAvatar(id: Int): Avatar? = avatarDao.getSystemAvatar(id)

    override fun getCustomAvatar(username: String): Avatar? = avatarDao.getCustomAvatar(username)

    override fun setCustomAvatar(avatar: Avatar?, username: String) {
        avatarDao.setCustomAvatar(avatar, username)
    }

    override fun setUserDao(userDao: UserDao) {
        this.userDao = userDao
    }

    override fun setAvatarDao(avatarDao: AvatarDao) {
        this.avatarDao = avatarDao
    }

    private fun createDefaultUserSettings(username: String): UserSettings = UserSettings(username).apply {
        finalVersionNotificationEnabled = true
        betaVersionNotificationEnabled = false
        songNotificationEnabled = true
        showNowPlayingEnabled = true
        partyModeEnabled = false
        nowPlayingAllowed = true
        autoHidePlayQueue = true
        keyboardShortcutsEnabled = false
        showSideBar = true
        showArtistInfoEnabled = true
        viewAsList = false
        queueFollowingSongs = true
        defaultAlbumList = AlbumListType.RANDOM
        lastFmEnabled = false
        listenBrainzEnabled = false
        changed = Instant.now()
        playlistVisibility.apply {
            artistVisible = true
            albumVisible = true
            yearVisible = true
            durationVisible = true
            bitRateVisible = true
            formatVisible = true
            fileSizeVisible = true
        }
        mainVisibility.apply {
            trackNumberVisible = true
            artistVisible = true
            durationVisible = true
        }
    }
}
