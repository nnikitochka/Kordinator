package org.airsonic.player.service.settings

import org.airsonic.player.util.StringUtil
import org.slf4j.LoggerFactory

/** Owns LDAP connection, manager credential, and shadow-account preferences. */
internal class SettingsLdapPreferences(private val values: SettingsValueStore) {
    fun isLdapEnabled(): Boolean = values.getBoolean(KEY_LDAP_ENABLED, false)
    fun setLdapEnabled(enabled: Boolean) = values.setProperty(KEY_LDAP_ENABLED, enabled)

    fun getLdapUrl(): String? = values.getString(KEY_LDAP_URL, DEFAULT_LDAP_URL)
    fun setLdapUrl(url: String?) = values.setProperty(KEY_LDAP_URL, url)

    fun getLdapSearchFilter(): String? = values.getString(KEY_LDAP_SEARCH_FILTER, DEFAULT_LDAP_SEARCH_FILTER)
    fun setLdapSearchFilter(filter: String?) = values.setProperty(KEY_LDAP_SEARCH_FILTER, filter)

    fun getLdapManagerDn(): String? = values.getString(KEY_LDAP_MANAGER_DN, null)
    fun setLdapManagerDn(dn: String?) = values.setProperty(KEY_LDAP_MANAGER_DN, dn)

    fun getLdapManagerPassword(): String? {
        val password = values.getString(KEY_LDAP_MANAGER_PASSWORD, null)
        return try {
            StringUtil.utf8HexDecode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to decode LDAP manager password.", failure)
            password
        }
    }

    fun setLdapManagerPassword(password: String?) {
        val encoded = try {
            StringUtil.utf8HexEncode(password)
        } catch (failure: Exception) {
            LOG.warn("Failed to encode LDAP manager password.", failure)
            password
        }
        values.setProperty(KEY_LDAP_MANAGER_PASSWORD, encoded)
    }

    fun isLdapAutoShadowing(): Boolean = values.getBoolean(KEY_LDAP_AUTO_SHADOWING, false)
    fun setLdapAutoShadowing(enabled: Boolean) = values.setProperty(KEY_LDAP_AUTO_SHADOWING, enabled)

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
