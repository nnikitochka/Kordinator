package org.airsonic.player.controller.rest.settings

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.domain.AlbumListType
import org.airsonic.player.domain.Avatar
import org.airsonic.player.domain.AvatarScheme
import org.airsonic.player.domain.User
import org.airsonic.player.domain.UserCredential.App
import org.airsonic.player.domain.UserSettings
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.SettingsService
import org.slf4j.LoggerFactory
import org.subsonic.restapi.*
import java.net.URI
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Full personal settings snapshot; the account always comes from authentication. */
class PersonalSettingsController(
    private val settings: SettingsService, private val security: SecurityService, private val writer: JAXBWriter
) {
    fun routes(root: Route, request: (ApplicationCall) -> UsersRequest) {
        with(root) {
            get(READ_PATH) { respond(call, request(call), false) }
            post(SAVE_PATH) { respond(call, request(call), true) }
        }
    }

    private suspend fun respond(call: ApplicationCall, request: UsersRequest, save: Boolean) {
        val response = try {
            if (!request.user.isAdminRole && !request.user.isSettingsRole) {
                writer.createErrorResponse(ErrorCode.NOT_AUTHORIZED, "Personal settings access denied")
            } else {
                if (save) save(request.user.username, request.parameters)
                writer.createResponse(true).apply { personalSettings = read(request.user) }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: IllegalArgumentException) { writer.createErrorResponse(ErrorCode.GENERIC, e.message ?: "Invalid settings")
        } catch (e: Exception) {
            LOG.warn("Personal settings failed", e)
            writer.createErrorResponse(ErrorCode.GENERIC, "Personal settings failed")
        }
        val serialized = writer.serializeForType(request.parameters["f"] ?: "xml", request.parameters["callback"], response)
        call.respondText(serialized.value, ContentType.parse(serialized.key))
    }

    private fun read(user: User): PersonalSettings {
        val value = settings.getUserSettings(user.username)
        val credentials = security.getDecodableCredsForApps(user.username, App.LASTFM, App.LISTENBRAINZ)
        return PersonalSettings().apply {
            username = user.username; admin = user.isAdminRole
            themeId = value.themeId.orEmpty(); locale = value.locale?.toLanguageTag().orEmpty()
            albumListId = value.defaultAlbumList.id
            listenBrainzUrl = value.listenBrainzUrl.orEmpty()
            avatarId = if (value.avatarScheme == AvatarScheme.SYSTEM) value.systemAvatarId else value.avatarScheme.code
            lastfmCredsAbsent = credentials[App.LASTFM] == null
            listenBrainzCredsAbsent = credentials[App.LISTENBRAINZ] == null
            partyModeEnabled = value.partyModeEnabled
            queueFollowingSongs = value.queueFollowingSongs
            showNowPlayingEnabled = value.showNowPlayingEnabled
            showArtistInfoEnabled = value.showArtistInfoEnabled
            nowPlayingAllowed = value.nowPlayingAllowed
            finalVersionNotificationEnabled = value.finalVersionNotificationEnabled
            betaVersionNotificationEnabled = value.betaVersionNotificationEnabled
            songNotificationEnabled = value.songNotificationEnabled
            autoHidePlayQueue = value.autoHidePlayQueue
            keyboardShortcutsEnabled = value.keyboardShortcutsEnabled
            lastFmEnabled = value.lastFmEnabled
            listenBrainzEnabled = value.listenBrainzEnabled
            autoBookmark = value.autoBookmark
            paginationSizeFiles = value.paginationSizeFiles
            paginationSizeFolders = value.paginationSizeFolders
            paginationSizePlaylist = value.paginationSizePlaylist
            paginationSizePlayqueue = value.paginationSizePlayqueue
            paginationSizeBookmarks = value.paginationSizeBookmarks
            audioBookmarkFrequency = value.audioBookmarkFrequency
            videoBookmarkFrequency = value.videoBookmarkFrequency
            searchCount = value.searchCount
            mainVisibility = visibility(value.mainVisibility)
            playlistVisibility = visibility(value.playlistVisibility)
            playqueueVisibility = visibility(value.playqueueVisibility)
            theme = settings.availableThemes.map { option(it.id, it.name) }.toMutableList()
            localeOption = settings.availableLocales.map { option(it.toLanguageTag(), it.getDisplayName(it)) }.toMutableList()
            albumList = AlbumListType.entries.map { option(it.id, it.description) }.toMutableList()
            avatar = settings.allSystemAvatars.map { this@PersonalSettingsController.avatar(it) }.toMutableList()
            customAvatar = settings.getCustomAvatar(user.username)?.let { this@PersonalSettingsController.avatar(it) }
        }
    }

    private fun option(id: String, name: String) = PersonalSettingsOption().apply { this.id = id; this.name = name }
    private fun avatar(value: Avatar) = PersonalSettingsAvatar().apply {
        id = value.id; name = value.name; width = value.width; height = value.height
    }
    private fun visibility(value: UserSettings.Visibility) = PersonalSettingsVisibility().apply {
        trackNumberVisible = value.trackNumberVisible
        discNumberVisible = value.discNumberVisible
        artistVisible = value.artistVisible
        albumArtistVisible = value.albumArtistVisible
        albumVisible = value.albumVisible
        genreVisible = value.genreVisible
        yearVisible = value.yearVisible
        bitRateVisible = value.bitRateVisible
        durationVisible = value.durationVisible
        formatVisible = value.formatVisible
        fileSizeVisible = value.fileSizeVisible
        headerVisible = value.headerVisible
        playCountVisible = value.playCountVisible
        lastPlayedVisible = value.lastPlayedVisible
        createdVisible = value.createdVisible
        changedVisible = value.changedVisible
        lastScannedVisible = value.lastScannedVisible
        entryTypeVisible = value.entryTypeVisible
    }

    private fun save(username: String, p: Parameters) {
        fun text(key: String) = p[key] ?: throw IllegalArgumentException("Missing $key")
        fun bool(key: String) = when (text(key)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean: $key")
        }
        fun number(key: String, pagination: Boolean = false): Int {
            val value = text(key).toIntOrNull() ?: throw IllegalArgumentException("Invalid integer: $key")
            require(value > 0 || (pagination && value == -1)) { "Invalid value: $key" }
            return value
        }
        // Parse the complete snapshot before touching the cached UserSettings object.
        val value = PersonalSettings().apply {
            themeId = text("themeId"); locale = text("locale"); albumListId = text("albumListId")
            listenBrainzUrl = text("listenBrainzUrl").trim()
            avatarId = text("avatarId").toIntOrNull() ?: throw IllegalArgumentException("Invalid avatarId")
            partyModeEnabled = bool("partyModeEnabled")
            queueFollowingSongs = bool("queueFollowingSongs")
            showNowPlayingEnabled = bool("showNowPlayingEnabled")
            showArtistInfoEnabled = bool("showArtistInfoEnabled")
            nowPlayingAllowed = bool("nowPlayingAllowed")
            finalVersionNotificationEnabled = bool("finalVersionNotificationEnabled")
            betaVersionNotificationEnabled = bool("betaVersionNotificationEnabled")
            songNotificationEnabled = bool("songNotificationEnabled")
            autoHidePlayQueue = bool("autoHidePlayQueue")
            keyboardShortcutsEnabled = bool("keyboardShortcutsEnabled")
            lastFmEnabled = bool("lastFmEnabled")
            listenBrainzEnabled = bool("listenBrainzEnabled")
            autoBookmark = bool("autoBookmark")
            paginationSizeFiles = number("paginationSizeFiles", true)
            paginationSizeFolders = number("paginationSizeFolders", true)
            paginationSizePlaylist = number("paginationSizePlaylist", true)
            paginationSizePlayqueue = number("paginationSizePlayqueue", true)
            paginationSizeBookmarks = number("paginationSizeBookmarks", true)
            audioBookmarkFrequency = number("audioBookmarkFrequency", false)
            videoBookmarkFrequency = number("videoBookmarkFrequency", false)
            searchCount = number("searchCount", false)
            mainVisibility = PersonalSettingsVisibility().apply {
                trackNumberVisible = bool("mainVisibility.trackNumberVisible")
                discNumberVisible = bool("mainVisibility.discNumberVisible")
                artistVisible = bool("mainVisibility.artistVisible")
                albumArtistVisible = bool("mainVisibility.albumArtistVisible")
                albumVisible = bool("mainVisibility.albumVisible")
                genreVisible = bool("mainVisibility.genreVisible")
                yearVisible = bool("mainVisibility.yearVisible")
                bitRateVisible = bool("mainVisibility.bitRateVisible")
                durationVisible = bool("mainVisibility.durationVisible")
                formatVisible = bool("mainVisibility.formatVisible")
                fileSizeVisible = bool("mainVisibility.fileSizeVisible")
                headerVisible = bool("mainVisibility.headerVisible")
                playCountVisible = bool("mainVisibility.playCountVisible")
                lastPlayedVisible = bool("mainVisibility.lastPlayedVisible")
                createdVisible = bool("mainVisibility.createdVisible")
                changedVisible = bool("mainVisibility.changedVisible")
                lastScannedVisible = bool("mainVisibility.lastScannedVisible")
                entryTypeVisible = bool("mainVisibility.entryTypeVisible")
            }
            playlistVisibility = PersonalSettingsVisibility().apply {
                trackNumberVisible = bool("playlistVisibility.trackNumberVisible")
                discNumberVisible = bool("playlistVisibility.discNumberVisible")
                artistVisible = bool("playlistVisibility.artistVisible")
                albumArtistVisible = bool("playlistVisibility.albumArtistVisible")
                albumVisible = bool("playlistVisibility.albumVisible")
                genreVisible = bool("playlistVisibility.genreVisible")
                yearVisible = bool("playlistVisibility.yearVisible")
                bitRateVisible = bool("playlistVisibility.bitRateVisible")
                durationVisible = bool("playlistVisibility.durationVisible")
                formatVisible = bool("playlistVisibility.formatVisible")
                fileSizeVisible = bool("playlistVisibility.fileSizeVisible")
                headerVisible = bool("playlistVisibility.headerVisible")
                playCountVisible = bool("playlistVisibility.playCountVisible")
                lastPlayedVisible = bool("playlistVisibility.lastPlayedVisible")
                createdVisible = bool("playlistVisibility.createdVisible")
                changedVisible = bool("playlistVisibility.changedVisible")
                lastScannedVisible = bool("playlistVisibility.lastScannedVisible")
                entryTypeVisible = bool("playlistVisibility.entryTypeVisible")
            }
            playqueueVisibility = PersonalSettingsVisibility().apply {
                trackNumberVisible = bool("playqueueVisibility.trackNumberVisible")
                discNumberVisible = bool("playqueueVisibility.discNumberVisible")
                artistVisible = bool("playqueueVisibility.artistVisible")
                albumArtistVisible = bool("playqueueVisibility.albumArtistVisible")
                albumVisible = bool("playqueueVisibility.albumVisible")
                genreVisible = bool("playqueueVisibility.genreVisible")
                yearVisible = bool("playqueueVisibility.yearVisible")
                bitRateVisible = bool("playqueueVisibility.bitRateVisible")
                durationVisible = bool("playqueueVisibility.durationVisible")
                formatVisible = bool("playqueueVisibility.formatVisible")
                fileSizeVisible = bool("playqueueVisibility.fileSizeVisible")
                headerVisible = bool("playqueueVisibility.headerVisible")
                playCountVisible = bool("playqueueVisibility.playCountVisible")
                lastPlayedVisible = bool("playqueueVisibility.lastPlayedVisible")
                createdVisible = bool("playqueueVisibility.createdVisible")
                changedVisible = bool("playqueueVisibility.changedVisible")
                lastScannedVisible = bool("playqueueVisibility.lastScannedVisible")
                entryTypeVisible = bool("playqueueVisibility.entryTypeVisible")
            }
        }
        require(value.themeId.isEmpty() || settings.availableThemes.any { it.id == value.themeId }) { "Unknown themeId" }
        require(value.locale.isEmpty() || settings.availableLocales.any { it.toLanguageTag() == value.locale }) { "Unknown locale" }
        val albumList = AlbumListType.fromId(value.albumListId) ?: throw IllegalArgumentException("Unknown albumListId")
        require(value.avatarId in setOf(-1, -2) || settings.getSystemAvatar(value.avatarId) != null) { "Unknown avatarId" }
        if (value.listenBrainzUrl.isNotEmpty()) {
            val uri = URI.create(value.listenBrainzUrl)
            require(uri.scheme in setOf("http", "https") && uri.host != null) { "Invalid ListenBrainz URL" }
        }
        val target = settings.getUserSettings(username)
        target.themeId = value.themeId.ifEmpty { null }
        target.locale = value.locale.ifEmpty { null }?.let { Locale.forLanguageTag(it) }
        target.defaultAlbumList = albumList
        target.listenBrainzUrl = value.listenBrainzUrl.ifEmpty { null }
        target.avatarScheme = when (value.avatarId) { -1 -> AvatarScheme.NONE; -2 -> AvatarScheme.CUSTOM; else -> AvatarScheme.SYSTEM }
        target.systemAvatarId = value.avatarId.takeIf { it >= 0 }
        target.partyModeEnabled = value.partyModeEnabled
        target.queueFollowingSongs = value.queueFollowingSongs
        target.showNowPlayingEnabled = value.showNowPlayingEnabled
        target.showArtistInfoEnabled = value.showArtistInfoEnabled
        target.nowPlayingAllowed = value.nowPlayingAllowed
        target.finalVersionNotificationEnabled = value.finalVersionNotificationEnabled
        target.betaVersionNotificationEnabled = value.betaVersionNotificationEnabled
        target.songNotificationEnabled = value.songNotificationEnabled
        target.autoHidePlayQueue = value.autoHidePlayQueue
        target.keyboardShortcutsEnabled = value.keyboardShortcutsEnabled
        target.lastFmEnabled = value.lastFmEnabled
        target.listenBrainzEnabled = value.listenBrainzEnabled
        target.autoBookmark = value.autoBookmark
        target.paginationSizeFiles = value.paginationSizeFiles
        target.paginationSizeFolders = value.paginationSizeFolders
        target.paginationSizePlaylist = value.paginationSizePlaylist
        target.paginationSizePlayqueue = value.paginationSizePlayqueue
        target.paginationSizeBookmarks = value.paginationSizeBookmarks
        target.audioBookmarkFrequency = value.audioBookmarkFrequency
        target.videoBookmarkFrequency = value.videoBookmarkFrequency
        target.searchCount = value.searchCount
        target.mainVisibility = domainVisibility(value.mainVisibility)
        target.playlistVisibility = domainVisibility(value.playlistVisibility)
        target.playqueueVisibility = domainVisibility(value.playqueueVisibility)
        target.changed = Instant.now()
        settings.updateUserSettings(target)
    }

    private fun domainVisibility(value: PersonalSettingsVisibility) = UserSettings.Visibility().apply {
        trackNumberVisible = value.trackNumberVisible
        discNumberVisible = value.discNumberVisible
        artistVisible = value.artistVisible
        albumArtistVisible = value.albumArtistVisible
        albumVisible = value.albumVisible
        genreVisible = value.genreVisible
        yearVisible = value.yearVisible
        bitRateVisible = value.bitRateVisible
        durationVisible = value.durationVisible
        formatVisible = value.formatVisible
        fileSizeVisible = value.fileSizeVisible
        headerVisible = value.headerVisible
        playCountVisible = value.playCountVisible
        lastPlayedVisible = value.lastPlayedVisible
        createdVisible = value.createdVisible
        changedVisible = value.changedVisible
        lastScannedVisible = value.lastScannedVisible
        entryTypeVisible = value.entryTypeVisible
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PersonalSettingsController::class.java)
        const val READ_PATH = "/rest/getPersonalSettings"
        const val SAVE_PATH = "/rest/savePersonalSettings"
    }
}
