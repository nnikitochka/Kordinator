package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TranscodingSettings", propOrder = ["transcoding"])
class TranscodingSettings {
    @field:XmlAttribute var brand: String = ""
    @field:XmlAttribute var transcodeDirectory: String = ""
    @field:XmlAttribute var downsampleCommand: String = ""
    @field:XmlAttribute var hlsCommand: String = ""
    @field:XmlAttribute var jukeboxCommand: String = ""
    @field:XmlAttribute var videoImageCommand: String = ""
    @field:XmlAttribute var subtitlesExtractionCommand: String = ""
    @field:XmlAttribute var transcodeEstimateTimePadding: String = ""
    @field:XmlAttribute var transcodeEstimateBytePadding: String = ""
    @field:XmlElement var transcoding: MutableList<TranscodingProfile> = mutableListOf()
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TranscodingProfile")
class TranscodingProfile {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var sourceFormats: String = ""
    @field:XmlAttribute var targetFormat: String = ""
    @field:XmlAttribute var step1: String = ""
    @field:XmlAttribute var step2: String = ""
    @field:XmlAttribute var step3: String = ""
    @field:XmlAttribute var defaultActive: Boolean = false
}
