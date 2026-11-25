package org.airsonic.player.service.settings

/** Owns DLNA identity, advertisement URL, enablement, and listener port settings. */
internal class SettingsDlnaPreferences(private val values: SettingsValueStore) {
    fun isEnabled(): Boolean = values.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) = values.setProperty(KEY_ENABLED, enabled)
    fun getServerName(): String? = values.getString(KEY_SERVER_NAME, DEFAULT_SERVER_NAME)
    fun setServerName(name: String?) = values.setProperty(KEY_SERVER_NAME, name)
    fun getServerId(): String? = values.getString(KEY_SERVER_ID, null)
    fun setServerId(id: String?) = values.setProperty(KEY_SERVER_ID, id)
    fun getBaseLanUrl(): String? = values.getString(KEY_BASE_LAN_URL, null)
    fun setBaseLanUrl(url: String?) = values.setProperty(KEY_BASE_LAN_URL, url)
    fun getUpnpPort(): Int = values.getInt(KEY_UPNP_PORT, DEFAULT_UPNP_PORT)

    private companion object {
        const val KEY_ENABLED = "DlnaEnabled"
        const val KEY_SERVER_NAME = "DlnaServerName"
        const val KEY_SERVER_ID = "DlnaServerId"
        const val KEY_BASE_LAN_URL = "DlnaBaseLANURL"
        const val KEY_UPNP_PORT = "UPnpPort"
        const val DEFAULT_SERVER_NAME = "Airsonic"
        const val DEFAULT_UPNP_PORT = 4041
    }
}
