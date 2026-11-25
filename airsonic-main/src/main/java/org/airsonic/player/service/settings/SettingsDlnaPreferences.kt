package org.airsonic.player.service.settings

/** Owns DLNA identity, advertisement URL, enablement, and listener port settings. */
internal class SettingsDlnaPreferences(private val values: SettingsValueStore) : SettingsDlnaAccess {
    override fun isDlnaEnabled(): Boolean = values.getBoolean(KEY_ENABLED, false)
    override fun setDlnaEnabled(enabled: Boolean) = values.setProperty(KEY_ENABLED, enabled)
    override fun getDlnaServerName(): String = requireNotNull(values.getString(KEY_SERVER_NAME, DEFAULT_SERVER_NAME))
    override fun setDlnaServerName(name: String?) = values.setProperty(KEY_SERVER_NAME, name)
    override fun getDlnaServerId(): String? = values.getString(KEY_SERVER_ID, null)
    override fun setDlnaServerId(id: String?) = values.setProperty(KEY_SERVER_ID, id)
    override fun getDlnaBaseLANURL(): String? = values.getString(KEY_BASE_LAN_URL, null)
    override fun setDlnaBaseLANURL(url: String?) = values.setProperty(KEY_BASE_LAN_URL, url)
    override fun getUPnpPort(): Int = values.getInt(KEY_UPNP_PORT, DEFAULT_UPNP_PORT)

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
