package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlType

/** Administrative settings; credentials are deliberately absent from read responses. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AdvancedSettings")
class AdvancedSettings {
    @field:XmlAttribute var downloadLimit: String = ""
    @field:XmlAttribute var uploadLimit: String = ""
    @field:XmlAttribute var ldapUrl: String = ""
    @field:XmlAttribute var ldapSearchFilter: String = ""
    @field:XmlAttribute var ldapManagerDn: String = ""
    @field:XmlAttribute var smtpServer: String = ""
    @field:XmlAttribute var smtpEncryption: String = ""
    @field:XmlAttribute var smtpPort: String = ""
    @field:XmlAttribute var smtpUser: String = ""
    @field:XmlAttribute var smtpFrom: String = ""
    @field:XmlAttribute var recaptchaSiteKey: String = ""
    @field:XmlAttribute var brand: String = ""
    @field:XmlAttribute var ldapEnabled: Boolean = false
    @field:XmlAttribute var ldapAutoShadowing: Boolean = false
    @field:XmlAttribute var captchaEnabled: Boolean = false
}
