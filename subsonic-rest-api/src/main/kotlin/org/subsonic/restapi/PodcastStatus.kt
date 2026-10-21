@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlEnum
import jakarta.xml.bind.annotation.XmlEnumValue
import jakarta.xml.bind.annotation.XmlType

@XmlType(name = "PodcastStatus")
@XmlEnum
enum class PodcastStatus(private val xmlValue: String) {
    @XmlEnumValue("new")
    NEW("new"),
    @XmlEnumValue("downloading")
    DOWNLOADING("downloading"),
    @XmlEnumValue("completed")
    COMPLETED("completed"),
    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("deleted")
    DELETED("deleted"),
    @XmlEnumValue("skipped")
    SKIPPED("skipped");

    fun value(): String = xmlValue

    companion object {
        @JvmStatic
        fun fromValue(value: String): PodcastStatus = entries.firstOrNull { it.xmlValue == value }
            ?: throw IllegalArgumentException(value)
    }
}
