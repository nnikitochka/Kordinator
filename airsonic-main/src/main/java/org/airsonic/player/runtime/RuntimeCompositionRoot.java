package org.airsonic.player.runtime;

/** Minimal framework-neutral composition root. Runtime services are added here as they are migrated. */
public final class RuntimeCompositionRoot {

    private final RuntimeConfiguration configuration;

    public RuntimeCompositionRoot() {
        this(RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER);
    }

    public RuntimeCompositionRoot(String healthStatusOrder) {
        this.configuration = new RuntimeConfiguration(healthStatusOrder);
    }

    public RuntimeConfiguration getConfiguration() {
        return configuration;
    }
}
