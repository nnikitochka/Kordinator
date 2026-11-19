package org.airsonic.player.security

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginRateLimiterTest {
    @Test
    fun blocksAfterConfiguredFailuresAndResetsAfterWindow() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val limiter = LoginRateLimiter(maxFailures = 2, window = Duration.ofMinutes(5), clock = clock)

        assertFalse(limiter.isLimited("127.0.0.1"))
        limiter.recordFailure("127.0.0.1")
        assertFalse(limiter.isLimited("127.0.0.1"))
        limiter.recordFailure("127.0.0.1")
        assertTrue(limiter.isLimited("127.0.0.1"))

        clock.current = clock.current.plus(Duration.ofMinutes(5))
        assertFalse(limiter.isLimited("127.0.0.1"))
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = MutableClock(current.atZone(zone).toInstant())
        override fun instant(): Instant = current
    }
}
