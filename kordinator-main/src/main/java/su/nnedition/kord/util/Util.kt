package su.nnedition.kord.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.primitives.Ints
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.apache.commons.lang3.SystemUtils
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.slf4j.LoggerFactory
import java.util.LinkedHashMap
import java.util.stream.Collectors
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** Miscellaneous general utility methods. */
object Util {
    private val log = LoggerFactory.getLogger(Util::class.java)
    private const val URL_SENSITIVE_REPLACEMENT_STRING = "<hidden>"
    private val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @JvmStatic
    fun getDefaultMusicFolder(): String {
        val defaultFolder = if (isWindows()) "c:\\music" else "/var/music"
        return System.getProperty("kordinator.defaultMusicFolder", defaultFolder)
    }

    @JvmStatic
    fun getDefaultPlaylistFolder(): String {
        val defaultFolder = if (isWindows()) "c:\\playlists" else "/var/playlists"
        return System.getProperty("kordinator.defaultPlaylistFolder", defaultFolder)
    }

    @JvmStatic
    fun isWindows(): Boolean = SystemUtils.IS_OS_WINDOWS

    @JvmStatic
    fun <T> subList(list: List<T>, offset: Long, max: Long): List<T> =
        if (list.size == Int.MAX_VALUE) {
            list.stream().skip(offset).limit(max).collect(Collectors.toList())
        } else {
            list.subList(
                minOf(list.size, Ints.saturatedCast(offset)),
                minOf(list.size, Ints.saturatedCast(offset + max)),
            )
        }

    @JvmStatic
    fun toIntArray(values: List<Int?>?): IntArray {
        if (values == null) return IntArray(0)
        return IntArray(values.size) { index -> values[index]!! }
    }

    @JvmStatic
    fun getObjectMapper(): ObjectMapper = objectMapper

    @JvmStatic
    fun debugObject(value: Any?): String = try {
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    } catch (exception: Exception) {
        log.warn("Cant output debug object", exception)
        ""
    }

    /** Return a URL with sensitive authentication parameters anonymized. */
    @JvmStatic
    fun anonymizeUrl(url: String): String {
        val fragmentStart = url.indexOf('#')
        val fragment = if (fragmentStart < 0) "" else url.substring(fragmentStart)
        val urlWithoutFragment = if (fragmentStart < 0) url else url.substring(0, fragmentStart)
        val queryStart = urlWithoutFragment.indexOf('?')
        if (queryStart < 0) {
            // Normalize an incomplete IPv6 authority to an empty address literal.
            if (urlWithoutFragment.matches(Regex("(?i)^https?://\\[[^]]*$"))) {
                return urlWithoutFragment.substring(0, urlWithoutFragment.indexOf('[')) + "[]" + fragment
            }
            return url
        }

        val query = urlWithoutFragment.substring(queryStart + 1)
        if (query.isEmpty()) return urlWithoutFragment.substring(0, queryStart) + fragment
        val queryParameters = LinkedHashMap<String, MutableList<String>>()
        for (parameter in query.split('&')) {
            if (parameter.isEmpty()) continue
            val equals = parameter.indexOf('=')
            val name = if (equals < 0) parameter else parameter.substring(0, equals)
            val value = if (equals < 0) "" else parameter.substring(equals + 1)
            queryParameters.computeIfAbsent(name) { ArrayList() }.add(value)
        }

        // replaceQueryParam removes all values and appends one replacement at the end.
        for (sensitiveName in listOf("p", "t", "s", "u")) {
            if (queryParameters.containsKey(sensitiveName)) {
                queryParameters.remove(sensitiveName)
                queryParameters[sensitiveName] = mutableListOf(URL_SENSITIVE_REPLACEMENT_STRING)
            }
        }

        val anonymized = StringBuilder(urlWithoutFragment.substring(0, queryStart)).append('?')
        var first = true
        for ((name, values) in queryParameters) {
            for (value in values) {
                if (!first) anonymized.append('&')
                first = false
                anonymized.append(name).append('=').append(value)
            }
        }
        return anonymized.append(fragment).toString()
    }

    /** Return true if the given object is an instance of the named class. */
    @JvmStatic
    fun isInstanceOfClassName(value: Any?, className: String): Boolean = try {
        Class.forName(className).isInstance(value)
    } catch (_: ClassNotFoundException) {
        false
    }

    @JvmStatic
    fun toJson(value: Any?): String? = try {
        objectMapper.writeValueAsString(value)
    } catch (_: Exception) {
        null
    }

    @JvmStatic
    fun toJson(value: Any?, feature: SerializationFeature): String? = try {
        objectMapper.writer(feature).writeValueAsString(value)
    } catch (_: Exception) {
        null
    }

    @JvmStatic
    fun <T> fromJson(json: String, type: TypeReference<T>): T? = try {
        objectMapper.readValue(json, type)
    } catch (_: Exception) {
        null
    }

    @JvmStatic
    fun <T> fromJson(json: String, type: Class<T>): T? = try {
        objectMapper.readValue(json, type)
    } catch (_: Exception) {
        null
    }

    @JvmStatic
    fun objectToStringMap(value: Any?): Map<String, String>? =
        objectMapper.convertValue(value, object : TypeReference<Map<String, String>>() {})

    @JvmStatic
    fun <T> stringMapToObject(clazz: Class<T>, data: Map<String, String>): T =
        objectMapper.convertValue(data, clazz)

    @JvmStatic
    fun <T> stringMapToValidObject(clazz: Class<T>, data: Map<String, String>): T {
        val value = stringMapToObject(clazz, data)
        if (BeanValidation.validator.validate(value).isNotEmpty()) {
            throw IllegalArgumentException("Created object was not valid")
        }
        return value
    }

    @JvmStatic
    fun getDaemonThreadfactory(prefixName: String): ThreadFactory = ThreadFactory { runnable ->
        Executors.defaultThreadFactory().newThread(runnable).apply {
            isDaemon = true
            name = "$prefixName-$name"
        }
    }

    private object BeanValidation {
        val validator: Validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(ParameterMessageInterpolator())
            .buildValidatorFactory().use { it.validator }
    }
}
