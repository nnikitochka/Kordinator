package org.airsonic.player.health;

/**
 * Registry for named health contributors.
 */
public interface HealthContributorRegistry extends Iterable<NamedContributor<HealthContributor>> {
    void registerContributor(String name, HealthContributor contributor);
    HealthContributor unregisterContributor(String name);
    HealthContributor getContributor(String name);
}
