package org.airsonic.player.domain

import org.airsonic.player.util.StringUtil
import kotlin.jvm.JvmName

/** Configuration describing up to three steps used to transcode a media format. */
class Transcoding(
    var id: Int?,
    var name: String?,
    var sourceFormats: String?,
    var targetFormat: String?,
    var step1: String?,
    var step2: String?,
    var step3: String?,
    @get:JvmName("isDefaultActive")
    var defaultActive: Boolean,
) {
    fun getSourceFormatsAsArray(): Array<String> = StringUtil.split(sourceFormats)

    override fun equals(other: Any?): Boolean =
        this === other || (other != null && javaClass == other.javaClass && id == (other as Transcoding).id)

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
