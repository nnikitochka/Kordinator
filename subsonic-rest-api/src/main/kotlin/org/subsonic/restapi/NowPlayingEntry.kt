@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NowPlayingEntry")
class NowPlayingEntry : Child() {
    @field:XmlAttribute(name = "username", required = true)
    var username: String? = null

    @field:XmlAttribute(name = "minutesAgo", required = true)
    var minutesAgo: Int = 0

    @field:XmlAttribute(name = "playerId", required = true)
    var playerId: Int = 0

    @field:XmlAttribute(name = "playerName")
    var playerName: String? = null

}
