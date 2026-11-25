package org.airsonic.player.service

import de.umass.lastfm.Album as LastFmAlbum
import de.umass.lastfm.Artist as LastFmArtist
import de.umass.lastfm.ImageSize
import org.airsonic.player.domain.AlbumNotes
import org.airsonic.player.domain.ArtistBio
import org.airsonic.player.domain.MediaFile
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.regex.Pattern

/** Fetches Last.fm biographies and album notes and normalizes their wiki markup. */
internal class LastFmMetadataClient {
    fun getArtistBio(mediaFile: MediaFile, locale: Locale): ArtistBio? =
        getArtistBio(canonicalArtistName(artistName(mediaFile)), locale)

    fun getArtistBio(artist: org.airsonic.player.domain.Artist, locale: Locale): ArtistBio? =
        getArtistBio(canonicalArtistName(artist.name), locale)

    fun getAlbumNotes(mediaFile: MediaFile): AlbumNotes? =
        getAlbumNotes(canonicalArtistName(artistName(mediaFile)), mediaFile.albumName)

    fun getAlbumNotes(album: org.airsonic.player.domain.Album): AlbumNotes? =
        getAlbumNotes(canonicalArtistName(album.artist), album.name)

    fun canonicalArtistName(artistName: String?): String? {
        try {
            if (artistName == null) return null
            val info = LastFmArtist.getInfo(artistName, API_KEY) ?: return null
            val redirect = redirectedArtist(processWikiText(info.wikiSummary))
            return redirect ?: artistName
        } catch (failure: Throwable) {
            log.warn("Failed to find artist bio for {}", artistName, failure)
            return null
        }
    }

    private fun getArtistBio(artistName: String?, locale: Locale): ArtistBio? {
        try {
            if (artistName == null) return null
            val info = LastFmArtist.getInfo(artistName, locale, null, API_KEY) ?: return null
            return ArtistBio(
                processWikiText(info.wikiSummary)!!,
                info.mbid,
                info.url,
                info.getImageURL(ImageSize.MEDIUM),
                info.getImageURL(ImageSize.LARGE),
                info.getImageURL(ImageSize.MEGA)
            )
        } catch (failure: Throwable) {
            log.warn("Failed to find artist bio for {}", artistName, failure)
            return null
        }
    }

    private fun getAlbumNotes(artistName: String?, albumName: String?): AlbumNotes? {
        if (artistName == null || albumName == null) return null
        try {
            val info = LastFmAlbum.getInfo(artistName, albumName, API_KEY) ?: return null
            return AlbumNotes(
                processWikiText(info.wikiSummary),
                info.mbid,
                info.url,
                info.getImageURL(ImageSize.MEDIUM),
                info.getImageURL(ImageSize.LARGE),
                info.getImageURL(ImageSize.MEGA)
            )
        } catch (failure: Throwable) {
            log.warn("Failed to find album notes for {} - {}", artistName, albumName, failure)
            return null
        }
    }

    internal fun redirectedArtist(biography: String?): String? {
        if (biography == null) return null
        val matcher = REDIRECT_PATTERN.matcher(biography)
        return if (matcher.find()) matcher.group(4) else null
    }

    internal fun processWikiText(text: String?): String? {
        val processed = org.apache.commons.lang.StringUtils.trimToNull(
            org.apache.commons.lang.StringUtils.replace(
                org.apache.commons.lang.StringUtils.replace(
                    org.apache.commons.lang.StringUtils.replace(text, "User-contributed text.*", ""),
                    "<a ", "<a target='_blank' "
                ),
                "\n", " "
            )
        )
        return if (processed != null && processed.startsWith("This is an incorrect tag")) null else processed
    }

    private fun artistName(mediaFile: MediaFile): String? = when {
        mediaFile.isAlbum || mediaFile.isFile -> mediaFile.albumArtist ?: mediaFile.artist
        else -> mediaFile.name
    }

    companion object {
        private const val API_KEY = "ece4499898a9440896dfdce5dab26bbf"
        private val log = LoggerFactory.getLogger(LastFmMetadataClient::class.java)
        private val REDIRECT_PATTERN = Pattern.compile(
            "((This is mistagged for)|(Fix your tags to)).*class=\"bbcode_artist\">(.*?)</a>"
        )
    }
}
