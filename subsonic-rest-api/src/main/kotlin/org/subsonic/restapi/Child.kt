@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlSeeAlso
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Child")
@XmlSeeAlso(NowPlayingEntry::class, PodcastEpisode::class)
open class Child {
    @field:XmlAttribute(name = "id", required = true)
    var id: String? = null

    @field:XmlAttribute(name = "parent")
    var parent: String? = null

    @field:XmlAttribute(name = "isDir", required = true)
    @get:JvmName("isIsDir")
    @set:JvmName("setIsDir")
    var directory: Boolean = false

    @field:XmlAttribute(name = "title", required = true)
    var title: String? = null

    @field:XmlAttribute(name = "album")
    var album: String? = null

    @field:XmlAttribute(name = "artist")
    var artist: String? = null

    @field:XmlAttribute(name = "track")
    var track: Int? = null

    @field:XmlAttribute(name = "year")
    var year: Int? = null

    @field:XmlAttribute(name = "genre")
    var genre: String? = null

    @field:XmlAttribute(name = "coverArt")
    var coverArt: String? = null

    @field:XmlAttribute(name = "size")
    var size: Long? = null

    @field:XmlAttribute(name = "contentType")
    var contentType: String? = null

    @field:XmlAttribute(name = "suffix")
    var suffix: String? = null

    @field:XmlAttribute(name = "transcodedContentType")
    var transcodedContentType: String? = null

    @field:XmlAttribute(name = "transcodedSuffix")
    var transcodedSuffix: String? = null

    @field:XmlAttribute(name = "duration")
    var duration: Int? = null

    @field:XmlAttribute(name = "bitRate")
    var bitRate: Int? = null

    @field:XmlAttribute(name = "path")
    var path: String? = null

    @field:XmlAttribute(name = "isVideo")
    @get:JvmName("isIsVideo")
    @set:JvmName("setIsVideo")
    var video: Boolean? = null

    @field:XmlAttribute(name = "userRating")
    var userRating: Int? = null

    @field:XmlAttribute(name = "averageRating")
    var averageRating: Double? = null

    @field:XmlAttribute(name = "playCount")
    var playCount: Long? = null

    @field:XmlAttribute(name = "discNumber")
    var discNumber: Int? = null

    @field:XmlAttribute(name = "created")
    @field:XmlSchemaType(name = "dateTime")
    var created: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "starred")
    @field:XmlSchemaType(name = "dateTime")
    var starred: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "albumId")
    var albumId: String? = null

    @field:XmlAttribute(name = "artistId")
    var artistId: String? = null

    @field:XmlAttribute(name = "type")
    var type: MediaType? = null

    @field:XmlAttribute(name = "bookmarkPosition")
    var bookmarkPosition: Long? = null

    @field:XmlAttribute(name = "originalWidth")
    var originalWidth: Int? = null

    @field:XmlAttribute(name = "originalHeight")
    var originalHeight: Int? = null

    @field:XmlAttribute(name = "accentColor")
    var accentColor: String? = null

    // Optional playlist details; populated only by getPlaylist(includeEntryDetails=true).
    @field:XmlAttribute(name = "present")
    var present: Boolean? = null

    @field:XmlAttribute(name = "playlistIndex")
    var playlistIndex: Int? = null

    @field:XmlAttribute(name = "albumArtist")
    var albumArtist: String? = null

    @field:XmlAttribute(name = "lastPlayed")
    var lastPlayed: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "lastScanned")
    var lastScanned: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "changed")
    var changed: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "formattedFileSize")
    var formattedFileSize: String? = null

    @field:XmlAttribute(name = "formattedBitRate")
    var formattedBitRate: String? = null

}
