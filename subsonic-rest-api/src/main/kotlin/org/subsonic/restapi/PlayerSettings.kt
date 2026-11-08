package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlayerSettings", propOrder = ["player", "transcoding", "transcodeScheme", "selectedPlayer"])
class PlayerSettings {
    @field:XmlAttribute var admin: Boolean = false
    @field:XmlAttribute var transcodingSupported: Boolean = false
    @field:XmlElement var player: MutableList<PlayerSettingsEntry> = mutableListOf()
    @field:XmlElement var transcoding: MutableList<PlayerTranscodingOption> = mutableListOf()
    @field:XmlElement var transcodeScheme: MutableList<PlayerTranscodeOption> = mutableListOf()
    @field:XmlElement var selectedPlayer: PlayerSettingsEntry? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlayerSettingsEntry", propOrder = ["activeTranscodingId"])
class PlayerSettingsEntry {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var description: String = ""
    @field:XmlAttribute var type: String = ""
    @field:XmlAttribute var lastSeen: String = ""
    @field:XmlAttribute var dynamicIp: Boolean = false
    @field:XmlAttribute var autoControlEnabled: Boolean = false
    @field:XmlAttribute var m3uBomEnabled: Boolean = false
    @field:XmlAttribute var technologyName: String = ""
    @field:XmlAttribute var transcodeSchemeName: String = ""
    @field:XmlElement var activeTranscodingId: MutableList<Int> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlayerTranscodingOption")
class PlayerTranscodingOption {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlayerTranscodeOption")
class PlayerTranscodeOption {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var description: String = ""
}
