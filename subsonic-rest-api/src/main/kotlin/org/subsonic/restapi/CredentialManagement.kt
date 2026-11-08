package org.subsonic.restapi

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CredentialManagement", propOrder = ["credential", "app", "encoder", "adminControls"])
class CredentialManagement {
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var ldapAuthEnabled: Boolean = false
    @field:XmlAttribute var preferredEncoder: String = ""
    @field:XmlAttribute var preferredDecodableEncoder: String = ""
    @field:XmlElement var credential: MutableList<ManagedCredential> = mutableListOf()
    @field:XmlElement var app: MutableList<CredentialApplication> = mutableListOf()
    @field:XmlElement var encoder: MutableList<CredentialEncoder> = mutableListOf()
    @field:XmlElement var adminControls: CredentialAdminControls? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ManagedCredential")
class ManagedCredential {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var app: String = ""
    @field:XmlAttribute var username: String = ""
    @field:XmlAttribute var encoder: String = ""
    @field:XmlAttribute var comment: String = ""
    @field:XmlAttribute var created: String = ""
    @field:XmlAttribute var updated: String = ""
    @field:XmlAttribute var expires: String = ""
    @field:XmlAttribute var decodable: Boolean = false
    @field:XmlAttribute var openText: Boolean = false
    @field:XmlAttribute var legacy: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CredentialApplication")
class CredentialApplication {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var usernameRequired: Boolean = false
    @field:XmlAttribute var nonDecodableEncodersAllowed: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CredentialEncoder")
class CredentialEncoder {
    @field:XmlAttribute var id: String = ""
    @field:XmlAttribute var name: String = ""
    @field:XmlAttribute var decodable: Boolean = false
}

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CredentialAdminControls")
class CredentialAdminControls {
    @field:XmlAttribute var legacyCredsPresent: Boolean = false
    @field:XmlAttribute var openCredsPresent: Boolean = false
    @field:XmlAttribute var defaultAdminCredsPresent: Boolean = false
    @field:XmlAttribute var nonDecodableEncoder: String = ""
    @field:XmlAttribute var decodableEncoder: String = ""
    @field:XmlAttribute var preferNonDecodable: Boolean = false
}

/** Only the separate administrator key-inspection endpoint populates this payload. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CredentialKeys")
class CredentialKeys {
    @field:XmlAttribute var jwtKey: String = ""
    @field:XmlAttribute var encryptionKey: String = ""
    @field:XmlAttribute var encryptionKeySalt: String = ""
}
