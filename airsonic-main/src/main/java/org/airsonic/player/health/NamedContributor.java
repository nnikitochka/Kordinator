package org.airsonic.player.health;

import java.util.Objects;

/**
 * A named health contributor entry.
 *
 * @param <C> type of contributor
 */
public interface NamedContributor<C extends HealthContributor> {
    String getName();
    C getContributor();

    static <C extends HealthContributor> NamedContributor<C> of(String name, C contributor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contributor, "contributor");
        return new NamedContributor<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public C getContributor() {
                return contributor;
            }

            @Override
            public String toString() {
                return name + "=" + contributor;
            }
        };
    }
}
