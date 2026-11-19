package org.airsonic.player.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeLiquibaseMigratorTest {
    @Test
    fun `migrates the production schema from runtime configuration`() {
        val configuration = RuntimeConfiguration(
            mapOf(
                "spring.datasource.url" to "jdbc:hsqldb:mem:runtime_liquibase_migrator;hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
                "spring.datasource.driver-class-name" to "org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username" to "sa",
                "spring.datasource.password" to "",
                "spring.liquibase.change-log" to "classpath:liquibase/db-changelog.xml",
                "spring.liquibase.parameters.mysqlVarcharLimit" to "255",
                "spring.liquibase.parameters.defaultMusicFolder" to "/music",
            ),
        )
        val dataSource = RuntimeDataSourceFactory.create(configuration) as com.zaxxer.hikari.HikariDataSource
        try {
            RuntimeLiquibaseMigrator.update(configuration, dataSource)
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM music_folder").use { result ->
                        assertTrue(result.next())
                    }
                }
            }
        } finally {
            dataSource.close()
        }
    }
}
