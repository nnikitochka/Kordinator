package org.airsonic.player.service.settings

import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory

/** Owns LDAP connection, manager credential, and shadow-account preferences. */
internal class SettingsLdapPreferences(private val values: SettingsValueStore) : SettingsLdapAccess {
    override fun isLdapEnabled(): Boolean = values.getBoolean(KEY_LDAP_ENABLED, false)
    override fun setLdapEnabled(enabled: Boolean) = values.setProperty(KEY_LDAP_ENABLED, enabled)

    override fun getLdapUrl(): String? = values.getString(KEY_LDAP_URL, DEFAULT_LDAP_URL)
    override fun setLdapUrl(url: String?) = values.setProperty(KEY_LDAP_URL, url)

    override fun getLdapSearchFilter(): String = requireNotNull(values.getString(KEY_LDAP_SEARCH_FILTER, DEFAULT_LDAP_SEARCH_FILTER))
    override fun setLdapSearchFilter(filter: String?) = values.setProperty(KEY_LDAP_SEARCH_FILTER, filter)

    override fun getLdapManagerDn(): String? = values.getString(KEY_LDAP_MANAGER_DN, null)
    override fun setLdapManagerDn(dn: String?) = values.setProperty(KEY_LDAP_MANAGER_DN, dn)

    override fun getLdapManagerPassword(): String? {
        val password = values.getString(KEY_LDAP_MANAGER_PASSWORD, null)
        return try {
            StringUtil.utf8HexDecode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to decode LDAP manager password.", failure)
            password
        }
    }

    override fun setLdapManagerPassword(password: String?) {
        val encoded = try {
            StringUtil.utf8HexEncode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to encode LDAP manager password.", failure)
            password
        }
        values.setProperty(KEY_LDAP_MANAGER_PASSWORD, encoded)
    }

    override fun isLdapAutoShadowing(): Boolean = values.getBoolean(KEY_LDAP_AUTO_SHADOWING, false)
    override fun setLdapAutoShadowing(enabled: Boolean) = values.setProperty(KEY_LDAP_AUTO_SHADOWING, enabled)

    private companion object {
        val LOG = LoggerFactory.getLogger(SettingsLdapPreferences::class.java)

        const val KEY_LDAP_ENABLED = "LdapEnabled"
        const val KEY_LDAP_URL = "LdapUrl"
        const val KEY_LDAP_MANAGER_DN = "LdapManagerDn"
        const val KEY_LDAP_MANAGER_PASSWORD = "LdapManagerPassword"
        const val KEY_LDAP_SEARCH_FILTER = "LdapSearchFilter"
        const val KEY_LDAP_AUTO_SHADOWING = "LdapAutoShadowing"
        const val DEFAULT_LDAP_URL = "ldap://host.domain.com:389/cn=Users,dc=domain,dc=com"
        const val DEFAULT_LDAP_SEARCH_FILTER = "(sAMAccountName={0})"
    }
}
