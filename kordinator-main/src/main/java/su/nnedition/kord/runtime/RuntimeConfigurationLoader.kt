package su.nnedition.kord.runtime

import su.nnedition.kord.service.SettingsService
import org.apache.commons.configuration2.Configuration
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.Properties
import java.util.function.Function

/** Loads framework-neutral configuration with legacy aliases normalized per source layer. */
class RuntimeConfigurationLoader {
    fun load(): RuntimeConfiguration = load(
        emptyArray<String?>(),
        System.getProperties(),
        System.getenv(),
        null,
        null as Map<String, String>?,
    )

    fun load(
        cliArgs: Array<out String?>?,
        jvmProperties: Properties?,
        environment: Map<String, String>?,
        applicationProperties: Properties?,
    ): RuntimeConfiguration = load(cliArgs, jvmProperties, environment, applicationProperties, null as Map<String, String>?)

    fun load(
        cliArgs: Array<out String?>?,
        jvmProperties: Properties?,
        environment: Map<String, String>?,
        applicationProperties: Properties?,
        kordinatorProperties: Map<String, String>?,
    ): RuntimeConfiguration {
        val rawCliMap = parseCliArgs(cliArgs)
        val rawJvmMap = propertiesToMap(jvmProperties)
        val rawEnvMap = environment?.let(::LinkedHashMap) ?: emptyMap()
        val rawAppMap = applicationProperties?.let(::propertiesToMap) ?: loadClasspathProperties(APP_PROPERTIES_RESOURCE)
        val keyMaps = SettingsService.getMigratedPropertyKeys()
        val cliMap = normalizeLayer(rawCliMap, keyMaps, rawCliMap::get, "cli")
        val jvmMap = normalizeLayer(rawJvmMap, keyMaps, rawJvmMap::get, "jvm")
        val envMap = normalizeLayer(rawEnvMap, keyMaps, { key -> lookupEnv(rawEnvMap, key) }, "environment")
        val appMap = normalizeLayer(rawAppMap, keyMaps, rawAppMap::get, "application.properties")
        val rawFileMap = kordinatorProperties?.let(::LinkedHashMap)
            ?: loadKordinatorProperties(cliMap, jvmMap, envMap, appMap)
        val fileMap = normalizeLayer(rawFileMap, keyMaps, rawFileMap::get, "kordinator.properties")

        return buildConfiguration(cliMap, jvmMap, envMap, appMap, fileMap)
    }

    fun load(
        cliArgs: Array<out String?>?,
        jvmProperties: Properties?,
        environment: Map<String, String>?,
        applicationProperties: Properties?,
        kordinatorConfig: Configuration?,
    ): RuntimeConfiguration {
        val fileMap = LinkedHashMap<String, String>()
        if (kordinatorConfig != null) {
            val keys = kordinatorConfig.keys
            while (keys.hasNext()) {
                val key = keys.next()
                kordinatorConfig.getProperty(key)?.toString()?.let { fileMap[key] = it }
            }
        }
        return load(cliArgs, jvmProperties, environment, applicationProperties, fileMap)
    }

    private fun buildConfiguration(
        cli: Map<String, String>,
        jvm: Map<String, String>,
        env: Map<String, String>,
        app: Map<String, String>,
        file: Map<String, String>,
    ): RuntimeConfiguration {
        val migratedStringMap = emptyMap<String, String>()
        var kordinatorHome = resolveRaw(cli, jvm, env, app, file, migratedStringMap, emptyMap(), "kordinator.home")
        if (kordinatorHome.isNullOrBlank()) {
            kordinatorHome = resolveRaw(cli, jvm, env, app, file, migratedStringMap, emptyMap(), "libresonic.home")
        }
        val homePath = if (!kordinatorHome.isNullOrBlank()) {
            Paths.get(kordinatorHome.trim())
        } else {
            defaultHome()
        }

        val defaults = linkedMapOf(
            "management.endpoint.health.status.order" to RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER,
            RuntimeConfiguration.DATABASE_USERNAME_KEY to "sa",
            RuntimeConfiguration.DATABASE_PASSWORD_KEY to "",
            RuntimeConfiguration.DATABASE_DRIVER_KEY to "org.hsqldb.jdbc.JDBCDriver",
            RuntimeConfiguration.DATABASE_URL_KEY to defaultDatabaseUrl(homePath),
            "logging.file.name" to homePath.resolve("kordinator.log").toString(),
            RuntimeConfiguration.DATABASE_MIGRATION_ENABLED_KEY to "true",
            RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY to "classpath:liquibase/db-changelog.xml",
            RuntimeConfiguration.DATABASE_MIGRATION_CONTEXTS_KEY to "",
            RuntimeConfiguration.DATABASE_MIGRATION_LABEL_FILTER_KEY to "",
            RuntimeConfiguration.DATABASE_MIGRATION_ROLLBACK_FILE_KEY to homePath.resolve("rollback.sql").toString(),
            RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY to "255",
            RuntimeConfiguration.DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY to defaultMusicFolder(),
        )

        val allKeys = LinkedHashSet<String>()
        allKeys.addAll(cli.keys)
        allKeys.addAll(jvm.keys)
        allKeys.addAll(env.keys)
        env.keys.filter { '_' in it }.forEach { allKeys.add(it.lowercase(Locale.ROOT).replace('_', '.')) }
        allKeys.addAll(app.keys)
        allKeys.addAll(file.keys)
        allKeys.addAll(migratedStringMap.keys)
        allKeys.addAll(defaults.keys)

        val resolved = LinkedHashMap<String, String>()
        allKeys.forEach { key ->
            resolveRaw(cli, jvm, env, app, file, migratedStringMap, defaults, key)?.let { resolved[key] = it }
        }
        return RuntimeConfiguration(resolved)
    }

    private fun normalizeLayer(
        source: Map<String, String>,
        keyMaps: Map<String, String>,
        propertyLookup: Function<String, Any?>,
        sourceName: String,
    ): Map<String, String> {
        val normalized = LinkedHashMap(source)
        val migrated = LinkedHashMap<String, Any>()
        SettingsService.migrateKeys(keyMaps, propertyLookup, sourceName, migrated)
        migrated.forEach { (key, value) -> normalized.putIfAbsent(key, value.toString()) }

        source.forEach { (key, value) ->
            val canonicalKey = when {
                key.startsWith("spring.liquibase.parameters.") ->
                    RuntimeConfiguration.DATABASE_MIGRATION_PARAMETERS_PREFIX + key.removePrefix("spring.liquibase.parameters.")
                key.startsWith("spring.datasource.hikari.") ->
                    RuntimeConfiguration.DATABASE_HIKARI_PREFIX + key.removePrefix("spring.datasource.hikari.")
                else -> null
            }
            if (canonicalKey != null) normalized.putIfAbsent(canonicalKey, value)
        }
        return normalized
    }

    private fun resolveRaw(
        cli: Map<String, String>,
        jvm: Map<String, String>,
        env: Map<String, String>,
        app: Map<String, String>,
        file: Map<String, String>,
        migrated: Map<String, String>,
        defaults: Map<String, String>,
        key: String,
    ): String? = cli[key]
        ?: jvm[key]
        ?: lookupEnv(env, key)
        ?: app[key]
        ?: file[key]
        ?: migrated[key]
        ?: defaults[key]

    private fun loadKordinatorProperties(
        cli: Map<String, String>,
        jvm: Map<String, String>,
        env: Map<String, String>,
        app: Map<String, String>,
    ): Map<String, String> {
        var home = resolveRaw(cli, jvm, env, app, emptyMap(), emptyMap(), emptyMap(), "kordinator.home")
        if (home.isNullOrBlank()) {
            home = resolveRaw(cli, jvm, env, app, emptyMap(), emptyMap(), emptyMap(), "libresonic.home")
        }
        val homePath = if (home.isNullOrBlank()) defaultHome() else Paths.get(home.trim())
        val fileName = if (homePath.toString().contains("libresonic")) "libresonic.properties" else "kordinator.properties"
        val propertyFile = homePath.resolve(fileName)
        if (!Files.isRegularFile(propertyFile)) return emptyMap()

        return try {
            Files.newInputStream(propertyFile).use(::loadProperties)
        } catch (failure: IOException) {
            throw IllegalStateException("Could not load application properties from $propertyFile", failure)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RuntimeConfigurationLoader::class.java)
        private const val APP_PROPERTIES_RESOURCE = "/application.properties"

        @JvmStatic
        fun lookupEnv(env: Map<String, String>?, key: String?): String? {
            if (env == null || key == null) return null
            env[key]?.let { return it }
            val relaxed = key.replace('.', '_').replace('-', '_').uppercase(Locale.ROOT)
            return env[relaxed]
        }

        @JvmStatic
        fun parseCliArgs(args: Array<out String?>?): Map<String, String> {
            if (args.isNullOrEmpty()) return emptyMap()
            val result = LinkedHashMap<String, String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                if (arg != null && arg.startsWith("--")) {
                    val trimmed = arg.substring(2)
                    val equals = trimmed.indexOf('=')
                    val nextArgument = args.getOrNull(index + 1)
                    when {
                        equals > 0 -> result[trimmed.substring(0, equals).trim()] = trimmed.substring(equals + 1).trim()
                        nextArgument != null && !nextArgument.startsWith("--") -> {
                            result[trimmed.trim()] = nextArgument.trim()
                            index++
                        }
                        else -> result[trimmed.trim()] = "true"
                    }
                }
                index++
            }
            return Collections.unmodifiableMap(result)
        }

        private fun propertiesToMap(properties: Properties?): Map<String, String> {
            if (properties == null) return emptyMap()
            return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        }

        private fun loadClasspathProperties(resourcePath: String): Map<String, String> {
            val properties = Properties()
            try {
                RuntimeConfigurationLoader::class.java.getResourceAsStream(resourcePath)?.use(properties::load)
            } catch (failure: Exception) {
                LOG.warn("Could not load classpath properties from {}", resourcePath, failure)
            }
            return propertiesToMap(properties)
        }

        private fun loadProperties(input: InputStream): Map<String, String> = Properties().apply { load(input) }
            .let(::propertiesToMap)

        private fun defaultHome(): Path = if (
            System.getProperty("os.name", "unknown").lowercase(Locale.ROOT).contains("win")
        ) {
            Paths.get("c:/kordinator")
        } else {
            Paths.get("/var/kordinator")
        }

        private fun defaultDatabaseUrl(home: Path): String = buildString {
            append("jdbc:hsqldb:file:")
            append(home.resolve("db/kordinator"))
            append(";hsqldb.default_table_type=cached;hsqldb.cache_rows=100000;hsqldb.cache_size=65536;hsqldb.applog=0")
            append(";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;sql.pad_space=false")
            append(";hsqldb.defrag_limit=50;shutdown=true")
        }

        private fun defaultMusicFolder(): String = if (
            System.getProperty("os.name", "unknown").lowercase(Locale.ROOT).contains("win")
        ) {
            "c:\\music"
        } else {
            "/var/music"
        }
    }
}
