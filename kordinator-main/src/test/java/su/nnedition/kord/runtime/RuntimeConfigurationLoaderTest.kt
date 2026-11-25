package su.nnedition.kord.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class RuntimeConfigurationLoaderTest {
    private val loader = RuntimeConfigurationLoader()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun loadsKordinatorPropertiesFromConfiguredHomeAndKeepsHigherPrecedence() {
        val home = Files.createDirectory(tempDir.resolve("configured-home"))
        Files.writeString(
            home.resolve("kordinator.properties"),
            "server.port=9091\nlogging.file.name=${home.resolve("from-file.log")}\n",
        )
        val application = Properties().apply { setProperty("kordinator.home", home.toString()) }

        val fromFile = loader.load(emptyArray(), Properties(), emptyMap(), application)
        val fromCli = loader.load(arrayOf("--server.port=9092"), Properties(), emptyMap(), application)

        assertEquals("9091", fromFile.getProperty("server.port"))
        assertEquals(home.resolve("from-file.log").toString(), fromFile.logFile)
        assertEquals("9092", fromCli.getProperty("server.port"))
    }

    @Test
    fun normalizesAndMasksLegacyAliasesLoadedFromKordinatorProperties() {
        val home = Files.createDirectory(tempDir.resolve("legacy-config-home"))
        Files.writeString(
            home.resolve("kordinator.properties"),
            "spring.datasource.url=jdbc:postgresql://legacy/music\n" +
                "spring.datasource.password=hidden-from-logs\n" +
                "spring.liquibase.parameters.mysqlVarcharLimit=512\n",
        )
        val application = Properties().apply { setProperty("kordinator.home", home.toString()) }

        val config = loader.load(emptyArray(), Properties(), emptyMap(), application)

        assertEquals("jdbc:postgresql://legacy/music", config.databaseUrl)
        assertEquals("512", config.getProperty(RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY))
        assertEquals("******", config.asMaskedMap()["spring.datasource.password"])
    }

    @ParameterizedTest(name = "layers: cli={0}, jvm={1}, env={2}, app={3}, file={4} => expected={5}")
    @CsvSource(
        "cli, jvm, env, app, file, cli",
        "none, jvm, env, app, file, jvm",
        "none, none, env, app, file, env",
        "none, none, none, app, file, app",
        "none, none, none, none, file, file",
        "none, none, none, none, none, default-fallback",
    )
    fun precedenceFollowsStrictOrder(
        cliVal: String,
        jvmVal: String,
        envVal: String,
        appVal: String,
        fileVal: String,
        expected: String,
    ) {
        val key = "test.property"
        val cli = if (cliVal == "none") emptyArray() else arrayOf("--$key=$cliVal")
        val jvm = Properties().apply { if (jvmVal != "none") setProperty(key, jvmVal) }
        val env = if (envVal == "none") emptyMap() else mapOf(key to envVal)
        val app = Properties().apply { if (appVal != "none") setProperty(key, appVal) }
        val file = if (fileVal == "none") emptyMap() else mapOf(key to fileVal)

        val config = loader.load(cli, jvm, env, app, file)

        assertEquals(expected, config.getProperty(key, "default-fallback"))
    }

    @Test
    fun relaxedEnvironmentVariablesAreResolved() {
        val env = mapOf(
            "SPRING_DATASOURCE_URL" to "jdbc:postgresql://localhost:5432/kordinator",
            "KORDINATOR_HOME" to "/custom/kordinator",
            "SERVER_PORT" to "9090",
        )

        val config = loader.load(emptyArray(), Properties(), env, Properties())

        assertEquals("jdbc:postgresql://localhost:5432/kordinator", config.getProperty("spring.datasource.url"))
        assertEquals("/custom/kordinator", config.getProperty("kordinator.home"))
        assertEquals(Paths.get("/custom/kordinator"), config.kordinatorHome)
        assertEquals("9090", config.getProperty("server.port"))
        assertEquals(9090, config.getIntProperty("server.port", 8080))
    }

    @Test
    fun legacySpringDatabaseAndMigrationKeysNormalizeToKordinatorKeys() {
        val application = Properties().apply {
            setProperty("spring.datasource.url", "jdbc:postgresql://db/kordinator")
            setProperty("spring.liquibase.change-log", "classpath:custom-changelog.xml")
            setProperty("spring.liquibase.parameters.customValue", "legacy-value")
            setProperty("spring.datasource.hikari.maximum-pool-size", "12")
        }

        val config = loader.load(emptyArray(), Properties(), emptyMap(), application, emptyMap())

        assertEquals("jdbc:postgresql://db/kordinator", config.getProperty(RuntimeConfiguration.DATABASE_URL_KEY))
        assertEquals("jdbc:postgresql://db/kordinator", config.databaseUrl)
        assertEquals("classpath:custom-changelog.xml", config.getProperty(RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY))
        assertEquals("legacy-value", config.getProperty(RuntimeConfiguration.DATABASE_MIGRATION_PARAMETERS_PREFIX + "customValue"))
        assertEquals("12", config.getProperty(RuntimeConfiguration.DATABASE_HIKARI_PREFIX + "maximum-pool-size"))
        assertEquals("jdbc:postgresql://db/kordinator", config.getProperty("spring.datasource.url"))
    }

    @Test
    fun canonicalDatabaseKeysWinWithinALayerAndLegacyKeysKeepLayerPrecedence() {
        val application = Properties().apply {
            setProperty("spring.datasource.url", "jdbc:postgresql://legacy/app")
            setProperty(RuntimeConfiguration.DATABASE_URL_KEY, "jdbc:postgresql://canonical/app")
        }
        val environment = mapOf("KORDINATOR_DATABASE_URL" to "jdbc:postgresql://env/app")

        val config = loader.load(
            arrayOf("--spring.datasource.url=jdbc:postgresql://cli/app"),
            Properties(), environment, application, emptyMap(),
        )
        assertEquals("jdbc:postgresql://cli/app", config.databaseUrl)

        val appOnly = loader.load(emptyArray(), Properties(), emptyMap(), application, emptyMap())
        assertEquals("jdbc:postgresql://canonical/app", appOnly.databaseUrl)
    }

    @Test
    fun defaultEmbeddedDatabaseUrlHasOptionsRequiredByTheLiquibaseSchema() {
        val config = loader.load(emptyArray(), Properties(), emptyMap(), Properties(), emptyMap())

        assertTrue(config.databaseUrl!!.contains("sql.enforce_size=false"))
        assertTrue(config.databaseUrl!!.contains("sql.char_literal=false"))
        assertTrue(config.databaseUrl!!.contains("sql.nulls_first=false"))
        assertTrue(config.databaseUrl!!.contains("sql.pad_space=false"))
    }

    @Test
    fun cliArgumentsParsingHandlesVariousFormats() {
        val args = arrayOf("--server.port=8080", "--kordinator.home", "/opt/kordinator", "--feature.enabled")

        val parsed = RuntimeConfigurationLoader.parseCliArgs(args)
        assertEquals("8080", parsed["server.port"])
        assertEquals("/opt/kordinator", parsed["kordinator.home"])
        assertEquals("true", parsed["feature.enabled"])
    }

    @Test
    fun migrationAliasesResolveWithProperPrecedence() {
        // DatabaseConfigEmbedUrl is a legacy alias for the canonical database URL.
        val env = mapOf("DatabaseConfigEmbedUrl" to "jdbc:mysql://localhost:3306/kordinator")
        val config = loader.load(emptyArray(), Properties(), env, Properties(), emptyMap())

        assertEquals("jdbc:mysql://localhost:3306/kordinator", config.getProperty(RuntimeConfiguration.DATABASE_URL_KEY))
        assertEquals("jdbc:mysql://localhost:3306/kordinator", config.databaseUrl)
    }

    @Test
    fun chainMigrationAliasesResolveCorrectly() {
        // database.config.embed.url -> DatabaseConfigEmbedUrl -> canonical database URL
        val fileProps = mapOf("database.config.embed.url" to "jdbc:mariadb://localhost/kordinator")
        val config = loader.load(emptyArray(), Properties(), emptyMap(), Properties(), fileProps)

        assertEquals("jdbc:mariadb://localhost/kordinator", config.getProperty(RuntimeConfiguration.DATABASE_URL_KEY))
        assertEquals("jdbc:mariadb://localhost/kordinator", config.databaseUrl)
    }

    @Test
    fun directConfigurationOverridesMigratedAlias() {
        // App defines current property name, but File defines old alias
        val appProps = Properties().apply { setProperty("spring.datasource.url", "jdbc:hsqldb:mem:test") }
        val fileProps = mapOf("DatabaseConfigEmbedUrl" to "jdbc:mysql://legacy")

        val config = loader.load(emptyArray(), Properties(), emptyMap(), appProps, fileProps)
        assertEquals("jdbc:hsqldb:mem:test", config.getProperty("spring.datasource.url"))
    }

    @Test
    fun typeConversionsAndInvalidHandling() {
        val jvm = Properties().apply {
            setProperty("int.valid", "4040")
            setProperty("int.invalid", "not-a-number")
            setProperty("bool.true", "true")
            setProperty("bool.yes", "yes")
            setProperty("bool.one", "1")
            setProperty("bool.false", "false")
            setProperty("bool.invalid", "maybe")
        }

        val config = loader.load(emptyArray(), jvm, emptyMap(), Properties(), emptyMap())

        assertEquals(4040, config.getIntProperty("int.valid", 1000))
        assertEquals(1000, config.getIntProperty("int.invalid", 1000))
        assertEquals(1000, config.getIntProperty("int.missing", 1000))
        assertTrue(config.getBooleanProperty("bool.true", false))
        assertTrue(config.getBooleanProperty("bool.yes", false))
        assertTrue(config.getBooleanProperty("bool.one", false))
        assertFalse(config.getBooleanProperty("bool.false", true))
        assertFalse(config.getBooleanProperty("bool.invalid", false))
        assertTrue(config.getBooleanProperty("bool.missing", true))
    }

    @Test
    fun emptyPropertyDoesNotFallBackToLowerLayer() {
        val jvm = Properties().apply { setProperty("empty.prop", "") }
        val app = Properties().apply { setProperty("empty.prop", "from-app") }

        val config = loader.load(emptyArray(), jvm, emptyMap(), app, emptyMap())

        assertEquals("", config.getProperty("empty.prop"))
        assertEquals("", config.getProperty("empty.prop", "fallback"))
    }

    @Test
    fun secretMaskingHidesSensitiveKeys() {
        val props = Properties().apply {
            setProperty("spring.datasource.password", "secretPassword123")
            setProperty("DatabaseConfigEmbedPassword", "legacySecret")
            setProperty("server.remember-me-key", "mySuperKey")
            setProperty("kordinator.jwt.key", "jwtSecretKey")
            setProperty("ldap.managerPassword", "ldapPass")
            setProperty("server.port", "8080")
            setProperty("kordinator.home", "/var/kordinator")
        }

        val config = loader.load(emptyArray(), props, emptyMap(), Properties(), emptyMap())
        val masked = config.asMaskedMap()

        assertEquals("******", masked["spring.datasource.password"])
        assertEquals("******", masked["DatabaseConfigEmbedPassword"])
        assertEquals("******", masked["server.remember-me-key"])
        assertEquals("******", masked["kordinator.jwt.key"])
        assertEquals("******", masked["ldap.managerPassword"])
        // Non-sensitive keys remain unmasked
        assertEquals("8080", masked["server.port"])
        assertEquals("/var/kordinator", masked["kordinator.home"])
    }

    @Test
    fun loggingDefaultLocationAndOverrides() {
        val props = Properties().apply { setProperty("kordinator.home", "/tmp/kordinator-test") }
        val config = loader.load(emptyArray(), props, emptyMap(), Properties(), emptyMap())

        assertEquals("/tmp/kordinator-test/kordinator.log", config.logFile)

        props.setProperty("logging.file.name", "/var/log/kordinator/custom.log")
        val configOverridden = loader.load(emptyArray(), props, emptyMap(), Properties(), emptyMap())

        assertEquals("/var/log/kordinator/custom.log", configOverridden.logFile)
    }

    @Test
    fun runtimeLoaderExposesApplicationConfigurationWithoutFrameworkAdapters() {
        val application = Properties().apply {
            setProperty("server.port", "7070")
            setProperty("spring.datasource.username", "testuser")
            setProperty("spring.datasource.password", "testpass")
            setProperty("management.endpoint.health.status.order", "FATAL,DOWN,UP")
        }

        val configuration = loader.load(emptyArray(), Properties(), emptyMap(), application, emptyMap())

        assertEquals("7070", configuration.getProperty("server.port"))
        assertEquals("testuser", configuration.databaseUsername)
        assertEquals("testpass", configuration.databasePassword)
        assertEquals(listOf("FATAL", "DOWN", "UP"), configuration.getHealthStatusOrder())
        assertTrue(configuration.containsKey("server.port"))
        assertFalse(configuration.containsKey("nonexistent"))
        assertEquals("******", configuration.asMaskedMap()["spring.datasource.password"])
    }
}
