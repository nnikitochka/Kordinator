package su.nnedition.kord.library.model

import java.io.File

data class TrackFile(
    val file: File,
    val tags: MutableMap<String, List<String>>,
) {
    val fileName: String = file.name
    val albumFolderPath: File = file.parentFile

    val title: String? = tags["TITLE"]?.firstOrNull()

    val albumName: String? = tags["ALBUM"]?.firstOrNull()

    val date: String? = tags["DATE"]?.firstOrNull()

    val version = tags["VERSION"]?.firstOrNull()

    val genre = tags["GENRE"]?.firstOrNull()

    var yandexMusicId: Long? = tags["YM_ID"]?.firstOrNull()?.toLongOrNull()
        set(value) {
            field = value
            if (value == null)
                tags.remove("YM_ID")
            else tags["YM_ID"] = listOf(value.toString())
        }

    var artists: List<String>? = tags["ARTIST"]
        set(value) {
            field = value
            if (value == null)
                tags.remove("ARTIST")
            else tags["ARTIST"] = value
        }

    var albumArtist: String? = tags["ALBUMARTIST"]?.firstOrNull()
        set(value) {
            field = value
            if (value == null)
                tags.remove("ALBUMARTIST")
            else tags["ALBUMARTIST"] = listOf(value)
        }


    var trackNum: Int? = tags["TRACKNUMBER"]?.firstOrNull()?.toIntOrNull()
        set(value) {
            field = value
            if (value == null)
                tags.remove("TRACKNUMBER")
            else tags["TRACKNUMBER"] = listOf(value.toString())
        }
    var tracksTotal: Int? = tags["TRACKTOTAL"]?.firstOrNull()?.toIntOrNull()
        set(value) {
            field = value
            if (value == null)
                tags.remove("TRACKTOTAL")
            else tags["TRACKTOTAL"] = listOf(value.toString())
        }
}