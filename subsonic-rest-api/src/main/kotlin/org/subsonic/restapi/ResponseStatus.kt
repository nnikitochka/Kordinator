@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlEnum
import jakarta.xml.bind.annotation.XmlEnumValue
import jakarta.xml.bind.annotation.XmlType

@XmlType(name = "ResponseStatus")
@XmlEnum
enum class ResponseStatus(private val xmlValue: String) {
    @XmlEnumValue("ok")
    OK("ok"),
    @XmlEnumValue("failed")
    FAILED("failed");

    fun value(): String = xmlValue

    companion object {
        @JvmStatic
        fun fromValue(value: String): ResponseStatus = entries.firstOrNull { it.xmlValue == value }
            ?: throw IllegalArgumentException(value)
    }
}
