package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TransferStatistics", propOrder = ["transfer"])
class TransferStatistics {
    @field:XmlElement(name = "transfer") val transfer: MutableList<TransferStatisticsEntry> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TransferStatisticsEntry", propOrder = ["sample"])
class TransferStatisticsEntry {
    @field:XmlAttribute var transferType: String = ""
    @field:XmlAttribute var playerDescription: String? = null
    @field:XmlAttribute var playerType: String? = null
    @field:XmlAttribute var username: String? = null
    @field:XmlAttribute var path: String? = null
    @field:XmlAttribute var bytesTransferred: String = ""
    @field:XmlElement(name = "sample") val sample: MutableList<TransferStatisticsSample> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TransferStatisticsSample")
class TransferStatisticsSample {
    @field:XmlAttribute var timestamp: Long = 0
    @field:XmlAttribute var bytesTransferred: Long = 0
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UserTransferStatistics", propOrder = ["user"])
class UserTransferStatistics {
    @field:XmlElement(name = "user") val user: MutableList<UserTransferStatisticsEntry> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UserTransferStatisticsEntry")
class UserTransferStatisticsEntry {
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var streamed: Long = 0
    @field:XmlAttribute var downloaded: Long = 0
    @field:XmlAttribute var uploaded: Long = 0
}
