package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UploadResult", propOrder = ["uploadedFile", "unzippedFile", "error"])
class UploadResult {
    @field:XmlElement val uploadedFile: MutableList<String> = ArrayList()
    @field:XmlElement val unzippedFile: MutableList<String> = ArrayList()
    @field:XmlElement val error: MutableList<String> = ArrayList()
}
