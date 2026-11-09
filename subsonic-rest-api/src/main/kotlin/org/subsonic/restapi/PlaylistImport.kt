package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PlaylistImport")
class PlaylistImport {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var name: String = ""
}
