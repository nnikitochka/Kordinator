package su.nnedition.kord.runtime

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.function.Function
import java.util.regex.Pattern

/** Framework-neutral configuration used by the application runtime. */
class RuntimeConfiguration {
    private val properties: Map<String, String>
    private val dynamicResolver: Function<String, String>?
    private val healthStatusOrder: List<String>

    constructor(healthStatusOrder: String) : this(
        mapOf(HEALTH_STATUS_ORDER_KEY to requireNotNull(healthStatusOrder)),
        null,
        healthStatusOrder,
    )

    constructor(properties: Map<String, String>?) : this(
        properties,
        null,
        properties?.get(HEALTH_STATUS_ORDER_KEY),
    )

    constructor(properties: Map<String, String>?, healthStatusOrder: String?) : this(
        properties,
        null,
        healthStatusOrder,
    )

    constructor(
        properties: Map<String, String>?,
        dynamicResolver: Function<String, String>?,
        healthStatusOrder: String?,
    ) {
        this.properties = if (properties == null) {
            emptyMap()
        } else {
            Collections.unmodifiableMap(LinkedHashMap(properties))
        }
        this.dynamicResolver = dynamicResolver

        val configuredOrder = healthStatusOrder
            ?.takeIf { it.isNotBlank() }
            ?: this.properties[HEALTH_STATUS_ORDER_KEY]
            ?: dynamicResolver?.apply(HEALTH_STATUS_ORDER_KEY)
            ?: DEFAULT_HEALTH_STATUS_ORDER
        this.healthStatusOrder = Collections.unmodifiableList(
            configuredOrder.split(',').map(String::trim).filter(String::isNotEmpty),
        )
    }

    fun getProperty(key: String): String? = properties[key] ?: dynamicResolver?.apply(key)

    fun getProperty(key: String, defaultValue: String?): String? = getProperty(key) ?: defaultValue

    fun containsKey(key: String): Boolean = properties.containsKey(key) || dynamicResolver?.apply(key) != null

    fun getIntProperty(key: String, defaultValue: Int): Int = properties[key]
        ?.takeIf(String::isNotBlank)
        ?.trim()
        ?.toIntOrNull()
        ?: defaultValue

    fun getBooleanProperty(key: String, defaultValue: Boolean): Boolean = when (
        properties[key]?.trim()?.lowercase(Locale.ROOT)
    ) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> defaultValue
    }

    val propertyNames: Set<String>
        get() = properties.keys

    fun asMap(): Map<String, String> = properties

    fun asMaskedMap(): Map<String, String> = Collections.unmodifiableMap(
        properties.mapValues { (key, value) ->
            if (SENSITIVE_KEY_PATTERN.matcher(key).matches()) "******" else value
        },
    )

    fun getHealthStatusOrder(): List<String> = healthStatusOrder

    val kordinatorHome: Path
        get() {
            getProperty("kordinator.home")?.takeIf(String::isNotBlank)?.let { return Paths.get(it.trim()) }
            getProperty("libresonic.home")?.takeIf(String::isNotBlank)?.let { return Paths.get(it.trim()) }
            return if (System.getProperty("os.name", "unknown").lowercase(Locale.ROOT).contains("win")) {
                KORDINATOR_HOME_WINDOWS
            } else {
                KORDINATOR_HOME_OTHER
            }
        }

    val logFile: String
        get() = getProperty("logging.file.name")
            ?.takeIf(String::isNotBlank)
            ?.trim()
            ?: kordinatorHome.toAbsolutePath().resolve("kordinator.log").toString()

    val databaseUrl: String?
        get() = getProperty(DATABASE_URL_KEY, getProperty("spring.datasource.url"))

    val databaseDriver: String?
        get() = getProperty(DATABASE_DRIVER_KEY, getProperty("spring.datasource.driver-class-name"))

    val databaseUsername: String
        get() = getProperty(
            DATABASE_USERNAME_KEY,
            getProperty("spring.datasource.username", "sa"),
        ) ?: "sa"

    val databasePassword: String
        get() = getProperty(
            DATABASE_PASSWORD_KEY,
            getProperty("spring.datasource.password", ""),
        ) ?: ""

    val databaseJndiName: String?
        get() = getProperty(
            DATABASE_JNDI_NAME_KEY,
            getProperty("spring.datasource.jndi-name"),
        )

    val liquibaseRollbackFile: String
        get() = getProperty(
            DATABASE_MIGRATION_ROLLBACK_FILE_KEY,
            getProperty("spring.liquibase.rollback-file"),
        )?.takeIf(String::isNotBlank)?.trim()
            ?: kordinatorHome.toAbsolutePath().resolve("rollback.sql").toString()

    val liquibaseMysqlVarcharLimit: Int
        get() = getProperty(
            DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY,
            getProperty("spring.liquibase.parameters.mysqlVarcharLimit"),
        )?.takeIf(String::isNotBlank)?.trim()?.toIntOrNull() ?: 255

    val liquibaseDefaultMusicFolder: String?
        get() = getProperty(
            DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY,
            getProperty("spring.liquibase.parameters.defaultMusicFolder"),
        )

    companion object {
        const val DEFAULT_HEALTH_STATUS_ORDER = "DOWN,OUT_OF_SERVICE,UP,UNKNOWN"
        const val HEALTH_STATUS_ORDER_KEY = "management.endpoint.health.status.order"
        const val DATABASE_DRIVER_KEY = "kordinator.database.driver"
        const val DATABASE_URL_KEY = "kordinator.database.url"
        const val DATABASE_USERNAME_KEY = "kordinator.database.username"
        const val DATABASE_PASSWORD_KEY = "kordinator.database.password"
        const val DATABASE_JNDI_NAME_KEY = "kordinator.database.jndi-name"
        const val DATABASE_HIKARI_PREFIX = "kordinator.database.hikari."
        const val DATABASE_MIGRATION_PREFIX = "kordinator.database.migrations."
        const val DATABASE_MIGRATION_ENABLED_KEY = DATABASE_MIGRATION_PREFIX + "enabled"
        const val DATABASE_MIGRATION_CHANGELOG_KEY = DATABASE_MIGRATION_PREFIX + "change-log"
        const val DATABASE_MIGRATION_CONTEXTS_KEY = DATABASE_MIGRATION_PREFIX + "contexts"
        const val DATABASE_MIGRATION_LABEL_FILTER_KEY = DATABASE_MIGRATION_PREFIX + "label-filter"
        const val DATABASE_MIGRATION_ROLLBACK_FILE_KEY = DATABASE_MIGRATION_PREFIX + "rollback-file"
        const val DATABASE_MIGRATION_PARAMETERS_PREFIX = DATABASE_MIGRATION_PREFIX + "parameters."
        const val DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY = DATABASE_MIGRATION_PARAMETERS_PREFIX + "mysqlVarcharLimit"
        const val DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY = DATABASE_MIGRATION_PARAMETERS_PREFIX + "defaultMusicFolder"

        private val KORDINATOR_HOME_WINDOWS: Path = Paths.get("c:/kordinator")
        private val KORDINATOR_HOME_OTHER: Path = Paths.get("/var/kordinator")
        private val SENSITIVE_KEY_PATTERN = Pattern.compile(
            ".*(password|secret|key|token|credential).*",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
