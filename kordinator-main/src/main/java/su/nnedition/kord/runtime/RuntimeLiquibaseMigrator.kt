package su.nnedition.kord.runtime

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
        val enabled = configuration.getBooleanProperty(RuntimeConfiguration.DATABASE_MIGRATION_ENABLED_KEY,
            configuration.getBooleanProperty("spring.liquibase.enabled", true))
        if (!enabled) return
        System.setProperty("liquibase.analytics.enabled", "false")

        val changelog = (configuration.getProperty(
            RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY,
            configuration.getProperty("spring.liquibase.change-log", DEFAULT_CHANGELOG),
        ) ?: DEFAULT_CHANGELOG).removePrefix(CHANGELOG_PREFIX)
        val contexts = Contexts(configuration.getProperty(
            RuntimeConfiguration.DATABASE_MIGRATION_CONTEXTS_KEY,
            configuration.getProperty("spring.liquibase.contexts", ""),
        ) ?: "")
        val labels = LabelExpression(configuration.getProperty(
            RuntimeConfiguration.DATABASE_MIGRATION_LABEL_FILTER_KEY,
            configuration.getProperty("spring.liquibase.label-filter", ""),
        ) ?: "")

        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            val liquibase = Liquibase(
                changelog,
                ClassLoaderResourceAccessor(RuntimeLiquibaseMigrator::class.java.classLoader),
                database,
            )
            try {
                val parameters = linkedMapOf<String, String>()
                configuration.propertyNames.asSequence()
                    .filter { it.startsWith("spring.liquibase.parameters.") }
                    .forEach { key ->
                        configuration.getProperty(key)?.let { value ->
                            parameters[key.removePrefix("spring.liquibase.parameters.")] = value
                        }
                    }
                configuration.propertyNames.asSequence()
                    .filter { it.startsWith(RuntimeConfiguration.DATABASE_MIGRATION_PARAMETERS_PREFIX) }
                    .forEach { key ->
                        configuration.getProperty(key)?.let { value ->
                            parameters[key.removePrefix(RuntimeConfiguration.DATABASE_MIGRATION_PARAMETERS_PREFIX)] = value
                        }
                    }
                parameters.forEach { (key, value) -> liquibase.changeLogParameters.set(key, value) }
                liquibase.update(contexts, labels)
            } finally {
                liquibase.close()
            }
        }
    }
}
