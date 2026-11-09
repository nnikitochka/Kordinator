package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CacheStatistics", propOrder = ["cache"])
class CacheStatistics {
    @field:XmlElement(name = "cache") val cache: MutableList<CacheStatisticsEntry> = mutableListOf()
}

/** Missing counters mean that the cache provider does not expose that metric. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CacheStatisticsEntry")
class CacheStatisticsEntry {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var hits: Double? = null
    @field:XmlAttribute var misses: Double? = null
    @field:XmlAttribute var puts: Double? = null
    @field:XmlAttribute var removals: Double? = null
}
