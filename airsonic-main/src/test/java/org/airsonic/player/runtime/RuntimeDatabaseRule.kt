package org.airsonic.player.runtime

import org.airsonic.player.service.ConfigurationPropertiesService
import org.airsonic.player.service.SettingsService
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Runs the JDBC schema migrations against a fresh database owned by the test. */
class RuntimeDatabaseRule : ExternalResource() {
    private val home = TemporaryFolder()
    private var previousHome: String? = null

    lateinit var runtime: RuntimeCompositionRoot
        private set

    lateinit var persistence: PersistenceGraph
        private set

    override fun apply(base: Statement, description: Description): Statement =
        home.apply(super.apply(base, description), description)

    override fun before() {
        previousHome = System.setProperty("airsonic.home", home.root.absolutePath)
        try {
            ConfigurationPropertiesService.reset()
            val loader = RuntimeConfigurationLoader()
            val configuration = loader.load()
            val url = configuration.databaseUrl
            val isolatedConfiguration = if (url != null && url.startsWith("jdbc:hsqldb:mem:")) {
                val options = url.substringAfter(';', "")
                val isolatedUrl = "jdbc:hsqldb:mem:${home.root.name};$options"
                loader.load(
                    arrayOf("--${SettingsService.KEY_DATABASE_URL}=$isolatedUrl"),
                    System.getProperties(), System.getenv(), null, emptyMap(),
                )
            } else {
                configuration
            }
            runtime = RuntimeCompositionRoot(isolatedConfiguration)
            persistence = runtime.createPersistenceGraph()
        } catch (failure: Throwable) {
            after()
            throw failure
        }
    }

    override fun after() {
        try {
            if (::runtime.isInitialized) runtime.lifecycleRegistry.shutdown()
        } finally {
            previousHome?.let { System.setProperty("airsonic.home", it) }
                ?: System.clearProperty("airsonic.home")
        }
    }
}
