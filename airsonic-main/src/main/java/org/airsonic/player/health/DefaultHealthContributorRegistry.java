package org.airsonic.player.health;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link HealthContributorRegistry}.
 */
public class DefaultHealthContributorRegistry implements HealthContributorRegistry {
    private final Map<String, HealthContributor> contributors = new ConcurrentHashMap<>();

    public DefaultHealthContributorRegistry() {
    }

    public DefaultHealthContributorRegistry(Map<String, ? extends HealthContributor> map) {
        if (map != null) {
            this.contributors.putAll(map);
        }
    }

    @Override
    public void registerContributor(String name, HealthContributor contributor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contributor, "contributor");
        contributors.put(name, contributor);
    }

    @Override
    public HealthContributor unregisterContributor(String name) {
        return contributors.remove(name);
    }

    @Override
    public HealthContributor getContributor(String name) {
        return contributors.get(name);
    }

    @Override
    public Iterator<NamedContributor<HealthContributor>> iterator() {
        return contributors.entrySet().stream()
                .map(e -> NamedContributor.of(e.getKey(), e.getValue()))
                .iterator();
    }
}
