package org.airsonic.player.runtime

import java.nio.file.Path
import java.util.Properties

/** Configuration for the independent Youamp server process. */
class StandaloneConfiguration private constructor(
    private val values: Map<String, String>,
) {
    fun getProperty(key: String, defaultValue: String): String = values[key] ?: defaultValue

    fun getIntProperty(key: String, defaultValue: Int): Int = values[key]?.toIntOrNull() ?: defaultValue

    fun getBooleanProperty(key: String, defaultValue: Boolean): Boolean = values[key]?.toBooleanStrictOrNull() ?: defaultValue

    val airsonicHome: Path
        get() = Path.of(getProperty("airsonic.home", DEFAULT_HOME))

    companion object {
        private const val DEFAULT_HOME = "/var/airsonic"

        fun load(args: Array<String>, systemProperties: Properties, environment: Map<String, String>): StandaloneConfiguration {
            val commandLine = parseArguments(args)
            val system = systemProperties.stringPropertyNames().associateWith(systemProperties::getProperty)
            val relaxedEnvironment = environment.asRelaxedProperties()
            val applicationProperties = Properties().apply {
                StandaloneConfiguration::class.java.getResourceAsStream("/application.properties")?.use { load(it) }
            }
            val application = applicationProperties.stringPropertyNames().associateWith(applicationProperties::getProperty)
            val home = sequenceOf(commandLine, system, relaxedEnvironment, application)
                .mapNotNull { it["airsonic.home"] }
                .firstOrNull() ?: DEFAULT_HOME
            val fileProperties = Properties().apply {
                Path.of(home).resolve("airsonic.properties").toFile().takeIf { it.isFile }?.inputStream()?.use { load(it) }
            }
            val file = fileProperties.stringPropertyNames().associateWith(fileProperties::getProperty)
            val keys = LinkedHashSet<String>().apply {
                addAll(file.keys)
                addAll(application.keys)
                addAll(relaxedEnvironment.keys)
                addAll(system.keys)
                addAll(commandLine.keys)
            }
            val merged = keys.associateWith { key ->
                commandLine[key] ?: system[key] ?: relaxedEnvironment[key] ?: application[key] ?: file[key].orEmpty()
            }
            return StandaloneConfiguration(merged)
        }

        private fun parseArguments(args: Array<String>): Map<String, String> = buildMap {
            var index = 0
            while (index < args.size) {
                val argument = args[index]
                if (argument.startsWith("--")) {
                    val option = argument.removePrefix("--")
                    val separator = option.indexOf('=')
                    if (separator > 0) {
                        put(option.substring(0, separator), option.substring(separator + 1))
                    } else if (index + 1 < args.size && !args[index + 1].startsWith("--")) {
                        put(option, args[++index])
                    } else {
                        put(option, "true")
                    }
                }
                index++
            }
        }

        private fun Map<String, String>.asRelaxedProperties(): Map<String, String> = entries.associate { (key, value) ->
            key.replace('_', '.').lowercase() to value
        }
    }
}
