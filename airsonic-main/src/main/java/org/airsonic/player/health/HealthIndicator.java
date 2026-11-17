package org.airsonic.player.health;

/**
 * Functional interface for a health indicator that provides a {@link Health} snapshot.
 */
@FunctionalInterface
public interface HealthIndicator extends HealthContributor {
    Health health();
}
