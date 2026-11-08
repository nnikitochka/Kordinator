package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UploadStatuses", propOrder = ["upload"])
class UploadStatuses {
    @field:XmlElement(name = "upload") val upload: MutableList<UploadStatus> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UploadStatus")
class UploadStatus {
    @field:XmlAttribute var transferId: String? = null
    @field:XmlAttribute var bytesUploaded: Long = 0
    @field:XmlAttribute var bytesTotal: Long = 0
}
