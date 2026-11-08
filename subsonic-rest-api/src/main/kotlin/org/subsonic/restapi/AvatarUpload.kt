package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AvatarUpload", propOrder = ["avatar"])
class AvatarUpload {
    @field:XmlAttribute var resized: Boolean = false
    @field:XmlElement var avatar: PersonalSettingsAvatar? = null
}
