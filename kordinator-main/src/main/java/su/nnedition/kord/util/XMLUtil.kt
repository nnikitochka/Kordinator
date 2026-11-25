package su.nnedition.kord.util

import org.jdom2.input.SAXBuilder

class XMLUtil {
    companion object {
        @JvmStatic
        fun createSAXBuilder(): SAXBuilder = SAXBuilder().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    }
}
