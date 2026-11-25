package org.airsonic.player.service

import java.util.LinkedHashSet
import java.util.Locale
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.regex.Pattern
import org.apache.commons.lang3.StringUtils
import org.airsonic.player.domain.CoverArtSource
import org.airsonic.player.util.StringUtil
import org.airsonic.player.util.Util

/** Owns library indexing, file type, playlist-folder, and cover-art preferences. */
internal class SettingsCatalogPreferences(private val values: SettingsValueStore) {
    fun getIndexString(): String = string(KEY_INDEX_STRING, DEFAULT_INDEX_STRING)
    fun setIndexString(value: String?) = values.setProperty(KEY_INDEX_STRING, value)

    fun getIgnoredArticles(): String = string(KEY_IGNORED_ARTICLES, DEFAULT_IGNORED_ARTICLES)
    fun getIgnoredArticlesAsArray(): Array<String> = derived(KEY_IGNORED_ARTICLES) {
        whitespace.split(getIgnoredArticles())
    }
    fun setIgnoredArticles(value: String?) {
        values.setProperty(KEY_IGNORED_ARTICLES, value)
        values.invalidateDerived(KEY_IGNORED_ARTICLES)
    }

    fun getUploadsFolder(): String = string(KEY_UPLOADS_FOLDER, DEFAULT_UPLOADS_FOLDER)
    fun setUploadsFolder(value: String?) = values.setProperty(KEY_UPLOADS_FOLDER, value)

    fun getGenreSeparators(): String = string(KEY_GENRE_SEPARATORS, DEFAULT_GENRE_SEPARATORS)
    fun setGenreSeparators(value: String?) = values.setProperty(KEY_GENRE_SEPARATORS, value)

    fun getShortcuts(): String = string(KEY_SHORTCUTS, DEFAULT_SHORTCUTS)
    fun getShortcutsAsArray(): Array<String> = StringUtil.split(getShortcuts())
    fun setShortcuts(value: String?) = values.setProperty(KEY_SHORTCUTS, value)

    fun getPlaylistFolder(): String = string(KEY_PLAYLIST_FOLDER, DEFAULT_PLAYLIST_FOLDER)
    fun setPlaylistFolder(value: String?) = values.setProperty(KEY_PLAYLIST_FOLDER, value)

    fun getMusicFileTypes(): String = string(KEY_MUSIC_FILE_TYPES, DEFAULT_MUSIC_FILE_TYPES)
    fun setMusicFileTypes(value: String?) = setFileTypes(KEY_MUSIC_FILE_TYPES, value)
    fun getMusicFileTypesSet(): Set<String> = derived(KEY_MUSIC_FILE_TYPES) { splitLowerString(getMusicFileTypes(), " ") }

    fun getVideoFileTypes(): String = string(KEY_VIDEO_FILE_TYPES, DEFAULT_VIDEO_FILE_TYPES)
    fun setVideoFileTypes(value: String?) = setFileTypes(KEY_VIDEO_FILE_TYPES, value)
    fun getVideoFileTypesSet(): Set<String> = derived(KEY_VIDEO_FILE_TYPES) { splitLowerString(getVideoFileTypes(), " ") }

    fun getCoverArtFileTypes(): String = string(KEY_COVER_ART_FILE_TYPES, DEFAULT_COVER_ART_FILE_TYPES)
    fun setCoverArtFileTypes(value: String?) = setFileTypes(KEY_COVER_ART_FILE_TYPES, value)
    fun getCoverArtFileTypesSet(): Set<String> = derived(KEY_COVER_ART_FILE_TYPES) { splitLowerString(getCoverArtFileTypes(), " ") }

    fun getCoverArtSource(): CoverArtSource = try {
        CoverArtSource.valueOf(string(KEY_COVER_ART_SOURCE, DEFAULT_COVER_ART_SOURCE))
    } catch (_: Exception) {
        CoverArtSource.valueOf(DEFAULT_COVER_ART_SOURCE)
    }

    fun setCoverArtSource(source: CoverArtSource) = values.setProperty(KEY_COVER_ART_SOURCE, source.name)
    fun getCoverArtConcurrency(): Int = values.getInt(KEY_COVER_ART_CONCURRENCY, DEFAULT_COVER_ART_CONCURRENCY)
    fun setCoverArtConcurrency(value: Int?) = values.setProperty(KEY_COVER_ART_CONCURRENCY, value)
    fun getCoverArtQuality(): Int = values.getInt(KEY_COVER_ART_QUALITY, DEFAULT_COVER_ART_QUALITY)
    fun setCoverArtQuality(value: Int?) = values.setProperty(KEY_COVER_ART_QUALITY, value)

    private fun setFileTypes(key: String, value: String?) {
        values.setProperty(key, value)
        values.invalidateDerived(key)
    }

    private fun string(key: String, default: String): String = requireNotNull(values.getString(key, default))

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> derived(key: String, loader: (String) -> T): T =
        values.derived(key, Function(loader)) as T

    private fun splitLowerString(value: String, splitter: String): Set<String> =
        Stream.of(*value.split(splitter.toRegex()).toTypedArray())
            .filter(StringUtils::isNotBlank)
            .map { it.lowercase(Locale.getDefault()) }
            .collect(Collectors.toCollection { LinkedHashSet() })

    private companion object {
        const val KEY_INDEX_STRING = "IndexString"
        const val KEY_IGNORED_ARTICLES = "IgnoredArticles"
        const val KEY_GENRE_SEPARATORS = "GenreSeparators"
        const val KEY_UPLOADS_FOLDER = "UploadsFolder"
        const val KEY_SHORTCUTS = "Shortcuts"
        const val KEY_PLAYLIST_FOLDER = "PlaylistFolder"
        const val KEY_MUSIC_FILE_TYPES = "MusicFileTypes"
        const val KEY_VIDEO_FILE_TYPES = "VideoFileTypes"
        const val KEY_COVER_ART_FILE_TYPES = "CoverArtFileTypes2"
        const val KEY_COVER_ART_SOURCE = "CoverArtSource"
        const val KEY_COVER_ART_CONCURRENCY = "CoverArtConcurrency"
        const val KEY_COVER_ART_QUALITY = "CoverArtQuality"
        const val DEFAULT_INDEX_STRING = "A B C D E F G H I J K L M N O P Q R S T U V W X-Z(XYZ)"
        const val DEFAULT_IGNORED_ARTICLES = "The El La Los Las Le Les"
        const val DEFAULT_UPLOADS_FOLDER = "%{['USER_MUSIC_FOLDERS'][0]}/Incoming"
        const val DEFAULT_GENRE_SEPARATORS = ";"
        const val DEFAULT_SHORTCUTS = ""
        val DEFAULT_PLAYLIST_FOLDER = Util.getDefaultPlaylistFolder()
        const val DEFAULT_MUSIC_FILE_TYPES = "mp3 ogg oga aac m4a m4b flac wav wma aif aiff ape mpc shn mka opus alm 669 mdl far xm mod fnk imf it liq wow mtm ptm rtm stm s3m ult dmf dbm med okt emod sfx m15 mtn amf gdm stx gmc psm j2b umx amd rad hsc flx gtk mgt mtp wv"
        const val DEFAULT_VIDEO_FILE_TYPES = "flv avi mpg mpeg mp4 m4v mkv mov wmv ogv divx m2ts webm"
        const val DEFAULT_COVER_ART_FILE_TYPES = "cover.jpg cover.png cover.gif folder.jpg jpg jpeg gif png"
        val DEFAULT_COVER_ART_SOURCE = CoverArtSource.FILE_TAG.name
        const val DEFAULT_COVER_ART_CONCURRENCY = 4
        const val DEFAULT_COVER_ART_QUALITY = 90
        val whitespace: Pattern = Pattern.compile("\\s+")
    }
}
