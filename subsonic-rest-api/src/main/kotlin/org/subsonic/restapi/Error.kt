@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Error")
class Error {
    @field:XmlAttribute(name = "code", required = true)
    var code: Int = 0

    @field:XmlAttribute(name = "message")
    var message: String? = null

}
