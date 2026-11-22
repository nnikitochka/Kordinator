package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerHealth", propOrder = ["component"])
class ServerHealth {
    @field:XmlAttribute var status: String = "UNKNOWN"
    @field:XmlElement(name = "component") val component: MutableList<HealthComponentInfo> = mutableListOf()
}
