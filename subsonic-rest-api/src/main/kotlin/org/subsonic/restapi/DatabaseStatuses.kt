package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatabaseStatuses", propOrder = ["backup", "export", "import"])
class DatabaseStatuses {
    @field:XmlElement var backup: DatabaseOperationStatus? = null
    @field:XmlElement var export: DatabaseOperationStatus? = null
    @field:XmlElement var `import`: DatabaseOperationStatus? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatabaseOperationStatus")
class DatabaseOperationStatus {
    @field:XmlAttribute var version: Long = 0
    @field:XmlAttribute var running: Boolean = false
    @field:XmlAttribute var failed: Boolean = false
    @field:XmlAttribute var message: String? = null
}
