package su.nnedition.kord.service.upnp

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.Player
import su.nnedition.kord.domain.User
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.PlayerService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.service.TranscodingService
import su.nnedition.kord.util.StringUtil
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException
import org.fourthline.cling.support.contentdirectory.DIDLParser
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.Res
import org.fourthline.cling.support.model.SortCriterion
import org.seamless.util.MimeType

/** Shared DLNA resources, URL construction, and serialization. */
abstract class CustomContentDirectory : AbstractContentDirectoryService(
    mutableListOf("*"),
    mutableListOf(),
) {
    @JvmField
    protected var settingsService: SettingsService? = null

    private var playerService: PlayerService? = null
    private var transcodingService: TranscodingService? = null

    @JvmField
    protected var jwtSecurityService: JWTSecurityService? = null

    open fun createResourceForSong(song: MediaFile): Res {
        val player: Player = playerService!!.getGuestPlayer()
        val path = buildString {
            append("ext/stream?id=")
            append(song.id)
            append("&player=")
            append(player.id)
            if (song.isVideo) append("&format=").append(TranscodingService.FORMAT_RAW)
        }
        val signedPath = jwtSecurityService!!.addJWTToken(User.USERNAME_ANONYMOUS, path)
        val url = getBaseUrl() + signedPath

        val suffix = if (song.isVideo) FilenameUtils.getExtension(song.path) else transcodingService!!.getSuffix(player, song, null)
        val mimeTypeString = StringUtil.getMimeType(suffix)
        val mimeType = mimeTypeString?.let(MimeType::valueOf)
        return Res(mimeType, null, url).apply {
            duration = formatDuration(song.duration)
        }
    }

    private fun formatDuration(seconds: Double?): String? {
        if (seconds == null) return null
        return StringUtil.formatDuration((seconds * 1000).toLong(), true)
    }

    open fun getBaseUrl(): String {
        val baseUrl = settingsService!!.getDlnaBaseLANURL()
        if (StringUtils.isBlank(baseUrl)) {
            throw RuntimeException("DLNA Base LAN URL is not set correctly")
        }
        return StringUtils.appendIfMissing(baseUrl, "/")
    }

    @Throws(Exception::class)
    protected open fun createBrowseResult(didl: DIDLContent, count: Int, totalMatches: Int): BrowseResult =
        BrowseResult(DIDLParser().generate(didl), count.toLong(), totalMatches.toLong())

    @Throws(ContentDirectoryException::class)
    override fun search(
        containerId: String?,
        searchCriteria: String?,
        filter: String?,
        firstResult: Long,
        maxResults: Long,
        orderBy: Array<out SortCriterion>?,
    ): BrowseResult = super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy)

    open fun setPlayerService(playerService: PlayerService) {
        this.playerService = playerService
    }

    open fun setTranscodingService(transcodingService: TranscodingService) {
        this.transcodingService = transcodingService
    }

    open fun setSettingsService(settingsService: SettingsService) {
        this.settingsService = settingsService
    }

    open fun setJwtSecurityService(jwtSecurityService: JWTSecurityService) {
        this.jwtSecurityService = jwtSecurityService
    }

    companion object {
        const val CONTAINER_ID_ROOT = "0"
    }
}
