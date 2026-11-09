package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

/** Admin extension. Stored database passwords are never returned. Changes apply after restart. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatabaseSettings")
class DatabaseSettings {
    @field:XmlAttribute var configType: String = "BUILTIN"
    @field:XmlAttribute var driver: String = ""
    @field:XmlAttribute var url: String = ""
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var jndiName: String = ""
    @field:XmlAttribute var mysqlVarcharMaxlength: Int = 0
    @field:XmlAttribute var dbBackupInterval: Int = -1
    @field:XmlAttribute var dbBackupRetentionCount: Int = -1
    @field:XmlAttribute var backuppable: Boolean = false
}
