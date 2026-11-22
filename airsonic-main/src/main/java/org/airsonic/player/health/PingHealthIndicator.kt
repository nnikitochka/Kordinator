package org.airsonic.player.health

/** Health indicator providing a basic responsiveness ping. */
class PingHealthIndicator : HealthIndicator {
    override fun health(): Health = Health.up().build()
}
