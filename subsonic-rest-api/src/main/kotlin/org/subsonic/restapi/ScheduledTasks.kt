package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ScheduledTasks", propOrder = ["task"])
class ScheduledTasks {
    @field:XmlElement(name = "task") val task: MutableList<ScheduledTaskInfo> = mutableListOf()
}

/** Dates describe estimates, not observed execution history. Null means no planned run. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ScheduledTaskInfo")
class ScheduledTaskInfo {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var created: String? = null
    @field:XmlAttribute var scheduledBy: String = ""
    @field:XmlAttribute var type: String? = null
    @field:XmlAttribute var firstRun: String? = null
    @field:XmlAttribute var lastRun: String? = null
    @field:XmlAttribute var nextRun: String? = null
}
