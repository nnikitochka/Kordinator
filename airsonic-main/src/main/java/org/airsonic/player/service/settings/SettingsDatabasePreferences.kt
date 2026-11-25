package org.airsonic.player.service.settings

import org.airsonic.player.runtime.RuntimeConfiguration

/** Owns the configured database connection values used by the runtime. */
internal class SettingsDatabasePreferences(private val values: SettingsValueStore) {
    fun getDriver(): String? = values.getString(RuntimeConfiguration.DATABASE_DRIVER_KEY, null)
    fun setDriver(driver: String?) = values.setProperty(RuntimeConfiguration.DATABASE_DRIVER_KEY, driver)
    fun getUrl(): String? = values.getString(RuntimeConfiguration.DATABASE_URL_KEY, null)
    fun setUrl(url: String?) = values.setProperty(RuntimeConfiguration.DATABASE_URL_KEY, url)
    fun getUsername(): String? = values.getString(RuntimeConfiguration.DATABASE_USERNAME_KEY, null)
    fun setUsername(username: String?) = values.setProperty(RuntimeConfiguration.DATABASE_USERNAME_KEY, username)
    fun getPassword(): String? = values.getString(RuntimeConfiguration.DATABASE_PASSWORD_KEY, null)
    fun setPassword(password: String?) = values.setProperty(RuntimeConfiguration.DATABASE_PASSWORD_KEY, password)
    fun getJndiName(): String? = values.getString(RuntimeConfiguration.DATABASE_JNDI_NAME_KEY, null)
    fun setJndiName(name: String?) = values.setProperty(RuntimeConfiguration.DATABASE_JNDI_NAME_KEY, name)
    fun getMysqlVarcharMaxlength(): Int = values.getInt(
        RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY,
        DEFAULT_MYSQL_VARCHAR_MAXLENGTH,
    )
    fun setMysqlVarcharMaxlength(maxlength: Int) = values.setProperty(RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY, maxlength)

    private companion object {
        const val DEFAULT_MYSQL_VARCHAR_MAXLENGTH = 384
    }
}
