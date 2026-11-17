package org.airsonic.player.health;

/**
 * Health indicator providing a basic responsiveness ping.
 */
public class PingHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        return Health.up().build();
    }
}
