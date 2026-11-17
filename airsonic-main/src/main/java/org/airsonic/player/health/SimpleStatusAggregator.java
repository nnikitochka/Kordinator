package org.airsonic.player.health;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Standard status aggregator ordering statuses by severity priority.
 */
public class SimpleStatusAggregator implements StatusAggregator {
    public static final List<String> DEFAULT_ORDER = List.of("DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN");

    private final List<String> order;

    public SimpleStatusAggregator() {
        this(DEFAULT_ORDER);
    }

    public SimpleStatusAggregator(String... order) {
        this(Arrays.asList(order));
    }

    public SimpleStatusAggregator(List<String> order) {
        Objects.requireNonNull(order, "order");
        this.order = order.stream().map(s -> s.trim().toUpperCase()).toList();
    }

    @Override
    public Status getAggregateStatus(Set<Status> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Status.UNKNOWN;
        }
        for (String priorityCode : order) {
            for (Status s : statuses) {
                if (priorityCode.equalsIgnoreCase(s.getCode())) {
                    return s;
                }
            }
        }
        return statuses.iterator().next();
    }
}
