package org.airsonic.player.health;

import java.util.Set;

/**
 * Aggregates multiple health statuses into a single representative {@link Status}.
 */
public interface StatusAggregator {
    Status getAggregateStatus(Set<Status> statuses);
}
