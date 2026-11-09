package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PathWatchers", propOrder = ["watcher"])
class PathWatchers {
    @field:XmlElement(name = "watcher") val watcher: MutableList<PathWatcherInfo> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PathWatcherInfo")
class PathWatcherInfo {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var path: String = ""
}
