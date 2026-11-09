package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerHealth", propOrder = ["component"])
class ServerHealth {
    @field:XmlAttribute var status: String = "UNKNOWN"
    @field:XmlElement(name = "component") val component: MutableList<HealthComponentInfo> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HealthComponentInfo", propOrder = ["details", "component"])
class HealthComponentInfo {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var status: String = "UNKNOWN"
    // JSON preserves heterogeneous provider details in both XML and JSON responses.
    @field:XmlElement var details: String? = null
    @field:XmlElement(name = "component") val component: MutableList<HealthComponentInfo> = mutableListOf()
}
