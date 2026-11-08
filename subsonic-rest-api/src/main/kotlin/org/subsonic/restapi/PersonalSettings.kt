package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PersonalSettings", propOrder = ["mainVisibility", "playlistVisibility", "playqueueVisibility", "theme", "localeOption", "albumList", "avatar", "customAvatar"])
class PersonalSettings {
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var themeId: String = ""
    @field:XmlAttribute var locale: String = ""
    @field:XmlAttribute var albumListId: String = ""
    @field:XmlAttribute var listenBrainzUrl: String = ""
    @field:XmlAttribute var avatarId: Int = -1
    @field:XmlAttribute var admin: Boolean = false
    @field:XmlAttribute var lastfmCredsAbsent: Boolean = false
    @field:XmlAttribute var listenBrainzCredsAbsent: Boolean = false
    @field:XmlAttribute var partyModeEnabled: Boolean = false
    @field:XmlAttribute var queueFollowingSongs: Boolean = false
    @field:XmlAttribute var showNowPlayingEnabled: Boolean = false
    @field:XmlAttribute var showArtistInfoEnabled: Boolean = false
    @field:XmlAttribute var nowPlayingAllowed: Boolean = false
    @field:XmlAttribute var finalVersionNotificationEnabled: Boolean = false
    @field:XmlAttribute var betaVersionNotificationEnabled: Boolean = false
    @field:XmlAttribute var songNotificationEnabled: Boolean = false
    @field:XmlAttribute var autoHidePlayQueue: Boolean = false
    @field:XmlAttribute var keyboardShortcutsEnabled: Boolean = false
    @field:XmlAttribute var lastFmEnabled: Boolean = false
    @field:XmlAttribute var listenBrainzEnabled: Boolean = false
    @field:XmlAttribute var autoBookmark: Boolean = false
    @field:XmlAttribute var paginationSizeFiles: Int = 0
    @field:XmlAttribute var paginationSizeFolders: Int = 0
    @field:XmlAttribute var paginationSizePlaylist: Int = 0
    @field:XmlAttribute var paginationSizePlayqueue: Int = 0
    @field:XmlAttribute var paginationSizeBookmarks: Int = 0
    @field:XmlAttribute var audioBookmarkFrequency: Int = 0
    @field:XmlAttribute var videoBookmarkFrequency: Int = 0
    @field:XmlAttribute var searchCount: Int = 0
    @field:XmlElement var mainVisibility = PersonalSettingsVisibility()
    @field:XmlElement var playlistVisibility = PersonalSettingsVisibility()
    @field:XmlElement var playqueueVisibility = PersonalSettingsVisibility()
    @field:XmlElement var theme: MutableList<PersonalSettingsOption> = mutableListOf()
    @field:XmlElement var localeOption: MutableList<PersonalSettingsOption> = mutableListOf()
    @field:XmlElement var albumList: MutableList<PersonalSettingsOption> = mutableListOf()
    @field:XmlElement var avatar: MutableList<PersonalSettingsAvatar> = mutableListOf()
    @field:XmlElement var customAvatar: PersonalSettingsAvatar? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PersonalSettingsVisibility")
class PersonalSettingsVisibility {
    @field:XmlAttribute var trackNumberVisible: Boolean = false
    @field:XmlAttribute var discNumberVisible: Boolean = false
    @field:XmlAttribute var artistVisible: Boolean = false
    @field:XmlAttribute var albumArtistVisible: Boolean = false
    @field:XmlAttribute var albumVisible: Boolean = false
    @field:XmlAttribute var genreVisible: Boolean = false
    @field:XmlAttribute var yearVisible: Boolean = false
    @field:XmlAttribute var bitRateVisible: Boolean = false
    @field:XmlAttribute var durationVisible: Boolean = false
    @field:XmlAttribute var formatVisible: Boolean = false
    @field:XmlAttribute var fileSizeVisible: Boolean = false
    @field:XmlAttribute var headerVisible: Boolean = false
    @field:XmlAttribute var playCountVisible: Boolean = false
    @field:XmlAttribute var lastPlayedVisible: Boolean = false
    @field:XmlAttribute var createdVisible: Boolean = false
    @field:XmlAttribute var changedVisible: Boolean = false
    @field:XmlAttribute var lastScannedVisible: Boolean = false
    @field:XmlAttribute var entryTypeVisible: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PersonalSettingsOption")
class PersonalSettingsOption {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var name: String = ""
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PersonalSettingsAvatar")
class PersonalSettingsAvatar {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var width: Int = 0
    @field:XmlAttribute var height: Int = 0
}
