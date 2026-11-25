package su.nnedition.kord.security

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** In-process per-source throttle for password verification. */
class LoginRateLimiter(
    private val maxFailures: Int = 10,
    private val window: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val failures = mutableMapOf<String, FailureWindow>()

    init {
        require(maxFailures > 0) { "Maximum login failures must be positive" }
        require(!window.isNegative && !window.isZero) { "Login throttle window must be positive" }
    }

    @Synchronized
    fun isLimited(source: String): Boolean {
        val current = failures[source] ?: return false
        if (!clock.instant().isBefore(current.startedAt.plus(window))) {
            failures.remove(source)
            return false
        }
        return current.count >= maxFailures
    }

    @Synchronized
    fun recordFailure(source: String) {
        val now = clock.instant()
        val current = failures[source]
        if (current == null || !now.isBefore(current.startedAt.plus(window))) {
            if (current == null && failures.size >= MAX_TRACKED_SOURCES) {
                failures.entries.removeIf { !now.isBefore(it.value.startedAt.plus(window)) }
                if (failures.size >= MAX_TRACKED_SOURCES) failures.remove(failures.keys.first())
            }
            failures[source] = FailureWindow(now, 1)
        } else {
            failures[source] = current.copy(count = current.count + 1)
        }
    }

    @Synchronized
    fun clear(source: String) {
        failures.remove(source)
    }

    private data class FailureWindow(val startedAt: Instant, val count: Int)

    private companion object {
        const val MAX_TRACKED_SOURCES = 10_000
    }
}
