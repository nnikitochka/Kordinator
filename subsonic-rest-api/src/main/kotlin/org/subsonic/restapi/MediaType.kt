@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlEnum
import jakarta.xml.bind.annotation.XmlEnumValue
import jakarta.xml.bind.annotation.XmlType

@XmlType(name = "MediaType")
@XmlEnum
enum class MediaType(private val xmlValue: String) {
    @XmlEnumValue("music")
    MUSIC("music"),
    @XmlEnumValue("podcast")
    PODCAST("podcast"),
    @XmlEnumValue("audiobook")
    AUDIOBOOK("audiobook"),
    @XmlEnumValue("video")
    VIDEO("video");

    fun value(): String = xmlValue

    companion object {
        @JvmStatic
        fun fromValue(value: String): MediaType = entries.firstOrNull { it.xmlValue == value }
            ?: throw IllegalArgumentException(value)
    }
}
