package org.airsonic.player.ktor

import org.airsonic.player.runtime.RuntimeCompositionRoot
import org.airsonic.player.runtime.RuntimeConfiguration
import org.airsonic.player.service.MediaFolderService
import java.nio.file.Path

internal class KtorRuntimeFixture(home: Path, defaultMusicFolder: Path, databaseName: String) : AutoCloseable {
    val runtime = RuntimeCompositionRoot(
        RuntimeConfiguration(
            mapOf(
                "airsonic.home" to home.toString(),
                "spring.datasource.url" to "jdbc:hsqldb:mem:$databaseName;hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false",
                "spring.datasource.driver-class-name" to "org.hsqldb.jdbc.JDBCDriver",
                "spring.datasource.username" to "sa",
                "spring.datasource.password" to "",
                "spring.liquibase.parameters.defaultMusicFolder" to defaultMusicFolder.toString(),
                "spring.liquibase.parameters.mysqlVarcharLimit" to "255",
            ),
        ),
    )

    val mediaFolderService: MediaFolderService

    init {
        runtime.createPersistenceGraph()
        mediaFolderService = runtime.createMediaFolderService()
    }

    override fun close() {
        runtime.lifecycleRegistry.shutdown()
    }
}
