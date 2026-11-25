package su.nnedition.kord.service.metadata

import java.util.*

class Track(
    val id: Int,
    type: String?,
    language: String?,
    codec: String?
) {
    val type: String? = type?.trim()
    val language: String? = language?.trim()
    val codec: String? = codec?.trim()

    val languageName: String
        get() {
            language ?: return this.id.toString()
            val locale = Locale.of(language)
            return locale.getDisplayLanguage(Locale.ENGLISH).trim()
        }

    override fun toString(): String {
        return "$id $type $language $codec"
    }

    val isAudio: Boolean get() = "audio" == this.type

    val isVideo: Boolean
        get() = ("video" == this.type && ("mjpeg" != this.codec) && ("bmp" != this.codec))

    val isSubtitle: Boolean
        get() = "subtitle" == this.type

    val isStreamable: Boolean
        get() = STREAMABLE_CODECS.contains(codec)

    companion object {
        private val STREAMABLE_CODECS = setOf("h264", "aac")
    }
}
