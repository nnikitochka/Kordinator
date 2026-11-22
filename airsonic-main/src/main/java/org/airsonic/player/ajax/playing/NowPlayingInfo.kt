package org.airsonic.player.ajax.playing

import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.domain.PlayStatus
import org.airsonic.player.service.SettingsService
import org.airsonic.player.util.StringUtil
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import java.util.*

/**
 * Details about what a user is currently listening to.
 */
data class NowPlayingInfo(
    val transferId: UUID?,
    val playerId: Int?,
    val mediaFileId: Int?,
    val username: String?,
    val artist: String?,
    val title: String?,
    val tooltip: String?,
    val streamUrl: String?,
    val albumUrl: String?,
    val lyricsUrl: String?,
    val coverArtUrl: String?,
    val avatarUrl: String?,
    val minutesAgo: Long,
    private val playStatus: PlayStatus?
) {
    constructor(
        playerId: Int?,
        user: String?,
        artist: String?,
        title: String?,
        tooltip: String?,
        streamUrl: String?,
        albumUrl: String?,
        lyricsUrl: String?,
        coverArtUrl: String?,
        avatarUrl: String?,
        minutesAgo: Long
    ) : this(
        null, playerId, null, user, artist, title, tooltip, streamUrl, albumUrl, lyricsUrl, coverArtUrl, avatarUrl,
        minutesAgo, null
    )

    // deliberately do not make it a getter so it doesn't serialize
    fun fromPlayStatus(): PlayStatus? = playStatus

    companion object {
        @JvmStatic
        fun createForBroadcast(status: PlayStatus, settingsService: SettingsService): NowPlayingInfo? {
            val url = "" // NetworkService.getBaseUrl(request);

            val mediaFile = status.mediaFile ?: return null

            val player = status.player ?: return null
            var username = player.username ?: return null

            val minutesAgo = status.minutesAgo
            if (minutesAgo > 60) return null

            val userSettings = settingsService.getUserSettings(username)
            if (!userSettings.nowPlayingAllowed) return null

            var artist = mediaFile.artist
            var title = mediaFile.title
            val streamUrl = url + "stream?player=" + player.id + "&id=" + mediaFile.id
            val albumUrl = url + "main.view?id=" + mediaFile.id
            var lyricsUrl: String? = null
            if (!mediaFile.isVideo) {
                lyricsUrl = (url + "lyrics.view?artistUtf8Hex=" + StringUtil.utf8HexEncode(artist) + "&songUtf8Hex=" + StringUtil.utf8HexEncode(title))
            }
            val coverArtUrl = url + "coverArt.view?size=60&id=" + mediaFile.id

            var avatarUrl: String? = null
            if (userSettings.avatarScheme == AvatarScheme.SYSTEM) {
                avatarUrl = url + "avatar.view?id=" + userSettings.systemAvatarId
            } else if (userSettings.avatarScheme == AvatarScheme.CUSTOM
                && settingsService.getCustomAvatar(username) != null
            ) {
                avatarUrl = url + "avatar.view?usernameUtf8Hex=" + StringUtil.utf8HexEncode(username)
            }

            val tooltip = StringEscapeUtils.escapeHtml(artist) + " &ndash; " + StringEscapeUtils.escapeHtml(title)

            if (StringUtils.isNotBlank(player.name)) {
                username += "@" + player.name
            }
            artist = StringEscapeUtils.escapeHtml(StringUtils.abbreviate(artist, 25))
            title = StringEscapeUtils.escapeHtml(StringUtils.abbreviate(title, 25))
            username = StringEscapeUtils.escapeHtml(StringUtils.abbreviate(username, 25))

            return NowPlayingInfo(
                status.transferId, player.id, mediaFile.id, username, artist, title,
                tooltip, streamUrl, albumUrl, lyricsUrl, coverArtUrl, avatarUrl, minutesAgo, status
            )
        }
    }
}
