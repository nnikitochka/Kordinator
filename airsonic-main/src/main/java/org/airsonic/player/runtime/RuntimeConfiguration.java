package org.airsonic.player.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Framework-neutral configuration used by the application runtime. */
public final class RuntimeConfiguration {

    public static final String DEFAULT_HEALTH_STATUS_ORDER = "DOWN,OUT_OF_SERVICE,UP,UNKNOWN";

    private final List<String> healthStatusOrder;

    public RuntimeConfiguration(String healthStatusOrder) {
        Objects.requireNonNull(healthStatusOrder, "healthStatusOrder");
        this.healthStatusOrder = Collections.unmodifiableList(Arrays.stream(healthStatusOrder.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList()));
    }

    public List<String> getHealthStatusOrder() {
        return healthStatusOrder;
    }
}
