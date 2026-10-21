@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "User", propOrder = ["folder"])
class User {
    @field:XmlElement(name = "folder")
    val folder: MutableList<Int> = mutableListOf()

    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "email")
    var email: String? = null

    @field:XmlAttribute(name = "scrobblingEnabled", required = true)
    @get:JvmName("isScrobblingEnabled")
    var scrobblingEnabled: Boolean = false

    @field:XmlAttribute(name = "maxBitRate")
    var maxBitRate: Int? = null

    @field:XmlAttribute(name = "adminRole", required = true)
    @get:JvmName("isAdminRole")
    var adminRole: Boolean = false

    @field:XmlAttribute(name = "settingsRole", required = true)
    @get:JvmName("isSettingsRole")
    var settingsRole: Boolean = false

    @field:XmlAttribute(name = "downloadRole", required = true)
    @get:JvmName("isDownloadRole")
    var downloadRole: Boolean = false

    @field:XmlAttribute(name = "uploadRole", required = true)
    @get:JvmName("isUploadRole")
    var uploadRole: Boolean = false

    @field:XmlAttribute(name = "playlistRole", required = true)
    @get:JvmName("isPlaylistRole")
    var playlistRole: Boolean = false

    @field:XmlAttribute(name = "coverArtRole", required = true)
    @get:JvmName("isCoverArtRole")
    var coverArtRole: Boolean = false

    @field:XmlAttribute(name = "commentRole", required = true)
    @get:JvmName("isCommentRole")
    var commentRole: Boolean = false

    @field:XmlAttribute(name = "podcastRole", required = true)
    @get:JvmName("isPodcastRole")
    var podcastRole: Boolean = false

    @field:XmlAttribute(name = "streamRole", required = true)
    @get:JvmName("isStreamRole")
    var streamRole: Boolean = false

    @field:XmlAttribute(name = "jukeboxRole", required = true)
    @get:JvmName("isJukeboxRole")
    var jukeboxRole: Boolean = false

    @field:XmlAttribute(name = "shareRole", required = true)
    @get:JvmName("isShareRole")
    var shareRole: Boolean = false

    @field:XmlAttribute(name = "videoConversionRole", required = true)
    @get:JvmName("isVideoConversionRole")
    var videoConversionRole: Boolean = false

    @field:XmlAttribute(name = "avatarLastChanged")
    @field:XmlSchemaType(name = "dateTime")
    var avatarLastChanged: XMLGregorianCalendar? = null

}
