package org.airsonic.player.health

import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

/** Default implementation of [HealthContributorRegistry]. */
class DefaultHealthContributorRegistry @JvmOverloads constructor(
    map: Map<String, HealthContributor>? = null
) : HealthContributorRegistry {
    private val contributors = ConcurrentHashMap<String, HealthContributor>().apply {
        map?.let(::putAll)
    }

    override fun registerContributor(name: String, contributor: HealthContributor) {
        Objects.requireNonNull(name, "name")
        Objects.requireNonNull(contributor, "contributor")
        contributors[name] = contributor
    }

    override fun unregisterContributor(name: String): HealthContributor? = contributors.remove(name)

    override fun getContributor(name: String): HealthContributor? = contributors[name]

    override fun iterator(): Iterator<NamedContributor<HealthContributor>> =
        contributors.entries.map { NamedContributor.of(it.key, it.value) }.iterator()
}
