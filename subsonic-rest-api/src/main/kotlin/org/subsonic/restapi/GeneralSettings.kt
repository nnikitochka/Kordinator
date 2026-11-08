package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GeneralSettings", propOrder = ["themes", "locales"])
class GeneralSettings {
    @field:XmlAttribute var playlistFolder: String = ""
    @field:XmlAttribute var musicFileTypes: String = ""
    @field:XmlAttribute var videoFileTypes: String = ""
    @field:XmlAttribute var coverArtFileTypes: String = ""
    @field:XmlAttribute var index: String = ""
    @field:XmlAttribute var ignoredArticles: String = ""
    @field:XmlAttribute var genreSeparators: String = ""
    @field:XmlAttribute var shortcuts: String = ""
    @field:XmlAttribute var welcomeTitle: String = ""
    @field:XmlAttribute var welcomeSubtitle: String = ""
    @field:XmlAttribute var welcomeMessage: String = ""
    @field:XmlAttribute var loginMessage: String = ""
    @field:XmlAttribute var sessionDuration: String = ""
    @field:XmlAttribute var coverArtSource: String = ""
    @field:XmlAttribute var themeId: String = ""
    @field:XmlAttribute var locale: String = ""
    @field:XmlAttribute var sortAlbumsByYear: Boolean = false
    @field:XmlAttribute var gettingStartedEnabled: Boolean = false
    @field:XmlAttribute var coverArtQuality: Int = 100
    @field:XmlAttribute var coverArtConcurrency: Int = 1
    @field:XmlElement var themes = GeneralSettingsThemes()
    @field:XmlElement var locales = GeneralSettingsLocales()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GeneralSettingsThemes", propOrder = ["theme"])
class GeneralSettingsThemes { @field:XmlElement var theme: MutableList<GeneralSettingsOption> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GeneralSettingsLocales", propOrder = ["locale"])
class GeneralSettingsLocales { @field:XmlElement var locale: MutableList<GeneralSettingsOption> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "GeneralSettingsOption")
class GeneralSettingsOption {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var name: String = ""
}
