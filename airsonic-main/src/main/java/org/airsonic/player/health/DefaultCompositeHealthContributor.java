package org.airsonic.player.health;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link CompositeHealthContributor}.
 */
public class DefaultCompositeHealthContributor implements CompositeHealthContributor {
    private final Map<String, HealthContributor> contributors = new ConcurrentHashMap<>();

    public DefaultCompositeHealthContributor(Map<String, ? extends HealthContributor> map) {
        if (map != null) {
            this.contributors.putAll(map);
        }
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
