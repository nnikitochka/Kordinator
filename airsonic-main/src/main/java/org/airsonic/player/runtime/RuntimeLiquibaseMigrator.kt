package org.airsonic.player.runtime

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import javax.sql.DataSource

/** Applies the application's changelog before DAO and service graphs are created. */
object RuntimeLiquibaseMigrator {
    private const val CHANGELOG_PREFIX = "classpath:"
    private const val DEFAULT_CHANGELOG = "classpath:liquibase/db-changelog.xml"

    fun update(configuration: RuntimeConfiguration, dataSource: DataSource) {
        if (!configuration.getBooleanProperty("spring.liquibase.enabled", true)) return
        System.setProperty("liquibase.analytics.enabled", "false")

        val changelog = configuration.getProperty("spring.liquibase.change-log", DEFAULT_CHANGELOG)
            .removePrefix(CHANGELOG_PREFIX)
        val contexts = Contexts(configuration.getProperty("spring.liquibase.contexts", ""))
        val labels = LabelExpression(configuration.getProperty("spring.liquibase.label-filter", ""))

        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            val liquibase = Liquibase(
                changelog,
                ClassLoaderResourceAccessor(RuntimeLiquibaseMigrator::class.java.classLoader),
                database,
            )
            try {
                configuration.getPropertyNames()
                    .asSequence()
                    .filter { it.startsWith("spring.liquibase.parameters.") }
                    .forEach { key ->
                        configuration.getProperty(key)?.let { value ->
                            liquibase.changeLogParameters.set(
                                key.removePrefix("spring.liquibase.parameters."), value,
                            )
                        }
                    }
                liquibase.update(contexts, labels)
            } finally {
                liquibase.close()
            }
        }
    }
}
