package su.nnedition.kord.domain

import java.time.Instant
import java.util.Locale

/** Holds the preferences associated with a user account. */
open class UserSettings {
    open var username: String? = null
    open var locale: Locale? = null
    open var themeId: String? = null
    open var showNowPlayingEnabled = false
    open var showArtistInfoEnabled = false
    open var finalVersionNotificationEnabled = false
    open var betaVersionNotificationEnabled = false
    open var songNotificationEnabled = false
    open var keyboardShortcutsEnabled = false
    open var autoHidePlayQueue = false
    open var showSideBar = false
    open var viewAsList = false
    open var queueFollowingSongs = false
    open var defaultAlbumList: AlbumListType = AlbumListType.RANDOM
    open var mainVisibility = Visibility()
    open var playlistVisibility = Visibility()
    open var playqueueVisibility = Visibility()
    open var lastFmEnabled = false
    open var listenBrainzEnabled = false
    open var listenBrainzUrl: String? = null
    open var transcodeScheme: TranscodeScheme = TranscodeScheme.OFF
    open var selectedMusicFolderId = -1
    open var partyModeEnabled = false
    open var nowPlayingAllowed = false
    open var avatarScheme: AvatarScheme = AvatarScheme.NONE
    open var systemAvatarId: Int? = null
    open var changed: Instant? = Instant.now()
    open var paginationSizeFiles = 10
    open var paginationSizeFolders = 7
    open var paginationSizePlaylist = 10
    open var paginationSizePlayqueue = 10
    open var paginationSizeBookmarks = 20
    open var autoBookmark = true
    open var videoBookmarkFrequency = 40
    open var audioBookmarkFrequency = 10
    open var searchCount = 25

    constructor()

    constructor(username: String) : this() {
        this.username = username
    }

    /** Configuration of which song metadata fields are visible. */
    open class Visibility {
        open var trackNumberVisible = false
        open var discNumberVisible = false
        open var artistVisible = true
        open var albumArtistVisible = false
        open var albumVisible = true
        open var genreVisible = false
        open var yearVisible = false
        open var bitRateVisible = false
        open var durationVisible = true
        open var formatVisible = false
        open var fileSizeVisible = false
        open var headerVisible = false
        open var playCountVisible = false
        open var lastPlayedVisible = false
        open var createdVisible = false
        open var changedVisible = false
        open var lastScannedVisible = false
        open var entryTypeVisible = false
    }
}
