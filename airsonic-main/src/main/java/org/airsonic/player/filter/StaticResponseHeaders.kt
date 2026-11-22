package org.airsonic.player.filter

import java.util.Collections
import java.util.LinkedHashMap
import java.util.Objects
import java.util.function.BiConsumer

/** Framework-neutral, immutable set of response headers for an HTTP policy. */
class StaticResponseHeaders(values: Map<String, String>) {
    private val headerValues: Map<String, String> =
        Collections.unmodifiableMap(LinkedHashMap(Objects.requireNonNull(values, "values")))

    fun values(): Map<String, String> = headerValues

    fun apply(headerWriter: BiConsumer<String, String>) {
        headerValues.forEach { (name, value) -> headerWriter.accept(name, value) }
    }
}
