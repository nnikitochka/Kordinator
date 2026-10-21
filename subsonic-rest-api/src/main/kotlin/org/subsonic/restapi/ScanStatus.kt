@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ScanStatus")
class ScanStatus {
    @field:XmlAttribute(name = "scanning", required = true)
    @get:JvmName("isScanning")
    var scanning: Boolean = false

    @field:XmlAttribute(name = "count")
    var count: Long? = null

}
