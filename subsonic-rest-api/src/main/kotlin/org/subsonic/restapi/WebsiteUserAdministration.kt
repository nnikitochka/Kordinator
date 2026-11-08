package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserAdministration", propOrder = ["users", "folders", "schemes", "user"])
class WebsiteUserAdministration {
    @field:XmlElement(name = "users") var users: WebsiteUserNames = WebsiteUserNames()
    @field:XmlElement(name = "folders") var folders: WebsiteUserFolders = WebsiteUserFolders()
    @field:XmlElement(name = "schemes") var schemes: WebsiteUserSchemes = WebsiteUserSchemes()
    @field:XmlElement(name = "user") var user: WebsiteUserDetails = WebsiteUserDetails()
    @field:XmlAttribute var ldapEnabled: Boolean = false
    @field:XmlAttribute var transcodingSupported: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserNames", propOrder = ["user"])
class WebsiteUserNames { @field:XmlElement(name = "user") var user: MutableList<String> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserFolders", propOrder = ["folder"])
class WebsiteUserFolders { @field:XmlElement(name = "folder") var folder: MutableList<WebsiteUserFolder> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserFolder")
class WebsiteUserFolder {
    @field:XmlAttribute var id: Int = 0
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var path: String = ""
    @field:XmlAttribute var allowed: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserSchemes", propOrder = ["scheme"])
class WebsiteUserSchemes { @field:XmlElement(name = "scheme") var scheme: MutableList<WebsiteUserScheme> = mutableListOf() }

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserScheme")
class WebsiteUserScheme {
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var description: String = ""
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "WebsiteUserDetails")
class WebsiteUserDetails {
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var email: String = ""
    @field:XmlAttribute var newUser: Boolean = true
    @field:XmlAttribute var currentUser: Boolean = false
    @field:XmlAttribute var ldapAuthenticated: Boolean = false
    @field:XmlAttribute var adminRole: Boolean = false
    @field:XmlAttribute var settingsRole: Boolean = true
    @field:XmlAttribute var streamRole: Boolean = true
    @field:XmlAttribute var jukeboxRole: Boolean = false
    @field:XmlAttribute var downloadRole: Boolean = false
    @field:XmlAttribute var uploadRole: Boolean = false
    @field:XmlAttribute var shareRole: Boolean = false
    @field:XmlAttribute var coverArtRole: Boolean = false
    @field:XmlAttribute var commentRole: Boolean = false
    @field:XmlAttribute var transcodeSchemeName: String = "OFF"
}
