package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MusicFolderSettings", propOrder = ["folders", "deletedFolders", "types"])
class MusicFolderSettings {
    @field:XmlElement var folders = AdminMusicFolders()
    @field:XmlElement var deletedFolders = AdminMusicFolders()
    @field:XmlElement var types = MusicFolderTypes()
    @field:XmlAttribute var interval: Int = -1
    @field:XmlAttribute var hour: Int = 0
    @field:XmlAttribute var scanning: Boolean = false
    @field:XmlAttribute var fastCache: Boolean = false
    @field:XmlAttribute var organizeByFolderStructure: Boolean = false
    @field:XmlAttribute var uploadsFolder: String = ""
    @field:XmlAttribute var excludePatternString: String = ""
    @field:XmlAttribute var ignoreSymLinks: Boolean = false
    @field:XmlAttribute var fullScan: Boolean = false
    @field:XmlAttribute var clearFullScanSettingAfterScan: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AdminMusicFolders", propOrder = ["folder"])
class AdminMusicFolders { @field:XmlElement var folder: MutableList<AdminMusicFolder> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MusicFolderTypes", propOrder = ["type"])
class MusicFolderTypes { @field:XmlElement var type: MutableList<String> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AdminMusicFolder")
class AdminMusicFolder {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var path: String = ""
    @field:XmlAttribute var type: String = "MEDIA"
    @field:XmlAttribute var enabled: Boolean = false
    @field:XmlAttribute var existing: Boolean = false
    @field:XmlAttribute var overlap: Boolean = false
    @field:XmlAttribute var overlapStatus: String = ""
}
