package su.nnedition.kord.library.model

import su.nnedition.kord.library.tags.AudioTagsManager
import java.io.File

class TrackTags(
    trackFile: File,
) {
    val rawTags = AudioTagsManager.readTags(trackFile).toMutableMap()

    val title: String? = rawTags["TITLE"]?.firstOrNull()

    val albumName: String? = rawTags["ALBUM"]?.firstOrNull()

    val date: String? = rawTags["DATE"]?.firstOrNull()

    val version = rawTags["VERSION"]?.firstOrNull()

    val genre = rawTags["GENRE"]?.firstOrNull()

    var yandexMusicId: Long? = rawTags["YM_ID"]?.firstOrNull()?.toLongOrNull()
        set(value) {
            field = value
            if (value == null)
                rawTags.remove("YM_ID")
            else rawTags["YM_ID"] = listOf(value.toString())
        }

    var artists: List<String>? = rawTags["ARTIST"]
        set(value) {
            field = value
            if (value == null)
                rawTags.remove("ARTIST")
            else rawTags["ARTIST"] = value
        }

    var albumArtist: String? = rawTags["ALBUMARTIST"]?.firstOrNull()
        set(value) {
            field = value
            if (value == null)
                rawTags.remove("ALBUMARTIST")
            else rawTags["ALBUMARTIST"] = listOf(value)
        }


    var trackNum: Int? = rawTags["TRACKNUMBER"]?.firstOrNull()?.toIntOrNull()
        set(value) {
            field = value
            if (value == null)
                rawTags.remove("TRACKNUMBER")
            else rawTags["TRACKNUMBER"] = listOf(value.toString())
        }
    var tracksTotal: Int? = rawTags["TRACKTOTAL"]?.firstOrNull()?.toIntOrNull()
        set(value) {
            field = value
            if (value == null)
                rawTags.remove("TRACKTOTAL")
            else rawTags["TRACKTOTAL"] = listOf(value.toString())
        }
}