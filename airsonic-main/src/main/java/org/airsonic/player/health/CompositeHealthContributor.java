package org.airsonic.player.health;

import java.util.Map;

/**
 * A composite health contributor containing named child health contributors.
 */
public interface CompositeHealthContributor extends HealthContributor, Iterable<NamedContributor<HealthContributor>> {
    HealthContributor getContributor(String name);

    static CompositeHealthContributor fromMap(Map<String, ? extends HealthContributor> map) {
        return new DefaultCompositeHealthContributor(map);
    }
}
