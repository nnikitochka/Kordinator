package su.nnedition.kord.health

import java.util.Collections
import java.util.LinkedHashMap
import java.util.Objects

/** Framework-neutral health snapshot with status and diagnostic details. */
class Health private constructor(builder: Builder) {
    val status: Status = Objects.requireNonNull(builder.statusValue, "status") as Status
    val details: Map<String, Any?> = Collections.unmodifiableMap(LinkedHashMap(builder.details))

    /** Java-compatible builder for health snapshots. */
    class Builder {
        internal var statusValue: Status? = Status.UNKNOWN
        internal val details: MutableMap<String, Any?> = LinkedHashMap()

        constructor()

        constructor(status: Status?) {
            statusValue = status
        }

        fun status(status: Status?): Builder = apply { statusValue = status }

        fun status(statusCode: String): Builder = apply { statusValue = Status(statusCode) }

        fun up(): Builder = status(Status.UP)

        fun down(): Builder = status(Status.DOWN)

        fun outOfService(): Builder = status(Status.OUT_OF_SERVICE)

        fun withDetail(key: String, value: Any?): Builder = apply {
            details[Objects.requireNonNull(key, "key")] = value
        }

        fun withDetails(details: Map<String, *>?): Builder = apply {
            details?.let(this.details::putAll)
        }

        fun withException(ex: Throwable?): Builder = apply {
            if (ex != null) {
                val message = ex.message
                val error = if (!message.isNullOrBlank()) {
                    "${ex.javaClass.name}: $message"
                } else {
                    ex.javaClass.name
                }
                details["error"] = error
            }
        }

        fun build(): Health = Health(this)
    }

    companion object {
        @JvmStatic
        fun status(status: Status?): Builder = Builder(status)

        @JvmStatic
        fun status(statusCode: String): Builder = Builder(Status(statusCode))

        @JvmStatic
        fun up(): Builder = status(Status.UP)

        @JvmStatic
        fun down(): Builder = status(Status.DOWN)

        @JvmStatic
        fun down(ex: Throwable?): Builder = down().withException(ex)

        @JvmStatic
        fun outOfService(): Builder = status(Status.OUT_OF_SERVICE)

        @JvmStatic
        fun unknown(): Builder = status(Status.UNKNOWN)
    }
}
