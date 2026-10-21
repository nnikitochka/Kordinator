@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ChatMessage")
class ChatMessage {
    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "time", required = true)
    var time: Long = 0L

    @field:XmlAttribute(name = "message", required = true)
    var message: String? = null

}
