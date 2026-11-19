package org.airsonic.player.runtime

import org.airsonic.player.util.LegacyHsqlMigrationUtil
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Properties
import javax.naming.InitialContext
import javax.sql.DataSource

/** Creates JDBC connections without Spring JDBC auto-configuration. */
object RuntimeDataSourceFactory {
    private const val HIKARI_PREFIX = "spring.datasource.hikari."

    fun create(configuration: RuntimeConfiguration): DataSource {
        LegacyHsqlMigrationUtil.upgradeFileHsqlDbIfNeeded(configuration::getProperty)

        configuration.databaseJndiName?.takeIf(String::isNotBlank)?.let { name ->
            val context = InitialContext()
            return try {
                context.lookup(name) as DataSource
            } finally {
                context.close()
            }
        }

        val properties = Properties().apply {
            configuration.databaseUrl?.let { setProperty("jdbcUrl", it) }
            configuration.databaseDriver?.let { setProperty("driverClassName", it) }
            setProperty("username", configuration.databaseUsername)
            setProperty("password", configuration.databasePassword)
            configuration.propertyNames.asSequence()
                .filter { it.startsWith(HIKARI_PREFIX) }
                .forEach { key ->
                    configuration.getProperty(key)?.let { value ->
                        setProperty(key.removePrefix(HIKARI_PREFIX).toHikariPropertyName(), value)
                    }
                }
        }
        return HikariDataSource(HikariConfig(properties))
    }

    private fun String.toHikariPropertyName(): String = buildString(length) {
        var uppercaseNext = false
        for (character in this@toHikariPropertyName) {
            if (character == '-') {
                uppercaseNext = true
            } else {
                append(if (uppercaseNext) character.uppercaseChar() else character)
                uppercaseNext = false
            }
        }
    }
}
