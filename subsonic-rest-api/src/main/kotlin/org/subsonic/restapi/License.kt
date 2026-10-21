@file:Suppress("unused")

package org.subsonic.restapi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlSchemaType
import jakarta.xml.bind.annotation.XmlType
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.jvm.JvmName

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "License")
class License {
    @field:XmlAttribute(name = "valid", required = true)
    @get:JvmName("isValid")
    var valid: Boolean = false

    @field:XmlAttribute(name = "email")
    var email: String? = null

    @field:XmlAttribute(name = "licenseExpires")
    @field:XmlSchemaType(name = "dateTime")
    var licenseExpires: XMLGregorianCalendar? = null

    @field:XmlAttribute(name = "trialExpires")
    @field:XmlSchemaType(name = "dateTime")
    var trialExpires: XMLGregorianCalendar? = null

}
