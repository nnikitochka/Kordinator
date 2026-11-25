package org.airsonic.player.service.settings

import org.airsonic.player.runtime.RuntimeConfiguration

/** Owns the configured database connection values used by the runtime. */
internal class SettingsDatabasePreferences(private val values: SettingsValueStore) : SettingsDatabaseAccess {
    override fun getDatabaseDriver(): String? = values.getString(RuntimeConfiguration.DATABASE_DRIVER_KEY, null)
    override fun setDatabaseDriver(driver: String?) = values.setProperty(RuntimeConfiguration.DATABASE_DRIVER_KEY, driver)
    override fun getDatabaseUrl(): String? = values.getString(RuntimeConfiguration.DATABASE_URL_KEY, null)
    override fun setDatabaseUrl(url: String?) = values.setProperty(RuntimeConfiguration.DATABASE_URL_KEY, url)
    override fun getDatabaseUsername(): String? = values.getString(RuntimeConfiguration.DATABASE_USERNAME_KEY, null)
    override fun setDatabaseUsername(username: String?) = values.setProperty(RuntimeConfiguration.DATABASE_USERNAME_KEY, username)
    override fun getDatabasePassword(): String? = values.getString(RuntimeConfiguration.DATABASE_PASSWORD_KEY, null)
    override fun setDatabasePassword(password: String?) = values.setProperty(RuntimeConfiguration.DATABASE_PASSWORD_KEY, password)
    override fun getDatabaseJNDIName(): String? = values.getString(RuntimeConfiguration.DATABASE_JNDI_NAME_KEY, null)
    override fun setDatabaseJNDIName(name: String?) = values.setProperty(RuntimeConfiguration.DATABASE_JNDI_NAME_KEY, name)
    override fun getDatabaseMysqlVarcharMaxlength(): Int = values.getInt(
        RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY,
        DEFAULT_MYSQL_VARCHAR_MAXLENGTH,
    )
    override fun setDatabaseMysqlVarcharMaxlength(maxlength: Int) = values.setProperty(RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY, maxlength)

    private companion object {
        const val DEFAULT_MYSQL_VARCHAR_MAXLENGTH = 384
    }
}
