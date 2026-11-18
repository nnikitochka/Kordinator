package org.airsonic.player.spring

import org.airsonic.player.runtime.RuntimeConfiguration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.core.env.EnumerablePropertySource
import java.util.LinkedHashMap

/** Bridges Spring's property sources into the framework-neutral runtime configuration. */
class SpringEnvironmentRuntimeConfigurationAdapter {
    companion object {
        @JvmStatic
        fun from(environment: Environment?): RuntimeConfiguration {
            if (environment == null) return RuntimeConfiguration(emptyMap())

            val properties = LinkedHashMap<String, String>()
            if (environment is ConfigurableEnvironment) {
                environment.propertySources.forEach { source ->
                    if (source is EnumerablePropertySource<*>) {
                        source.propertyNames.forEach { name ->
                            if (!properties.containsKey(name)) {
                                environment.getProperty(name)?.let { properties[name] = it }
                            }
                        }
                    }
                }
            }

            return RuntimeConfiguration(
                properties,
                { name -> environment.getProperty(name) },
                environment.getProperty(
                    RuntimeConfiguration.HEALTH_STATUS_ORDER_KEY,
                    RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER,
                ),
            )
        }
    }
}
