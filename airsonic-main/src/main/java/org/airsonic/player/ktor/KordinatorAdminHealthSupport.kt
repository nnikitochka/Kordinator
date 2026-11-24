package org.airsonic.player.ktor

import org.airsonic.player.health.CompositeHealthContributor
import org.airsonic.player.health.Health
import org.airsonic.player.health.HealthContributor
import org.airsonic.player.health.HealthIndicator
import org.airsonic.player.health.Status
import org.airsonic.player.health.StatusAggregator
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.health.NamedContributor

internal fun MusicFolder.toLibraryResponse(scanId: String? = null) = LibraryResponse(
    id = requireNotNull(id) { "Music folder has not been persisted" }.toLong(),
    name = name,
    path = path.toString(),
    enabled = isEnabled(),
    createdAt = changed.toString(),
    scanId = scanId,
)

internal fun NamedContributor<HealthContributor>.toHealthComponent(
    aggregator: StatusAggregator,
): HealthComponentResponse = contributor.toHealthComponent(name, aggregator)

internal fun HealthContributor.toHealthComponent(name: String, aggregator: StatusAggregator): HealthComponentResponse = when (this) {
    is HealthIndicator -> {
        val health = try {
            health()
        } catch (failure: Exception) {
            Health.down(failure).build()
        }
        HealthComponentResponse(name, health.status.code, health.details)
    }
    is CompositeHealthContributor -> {
        val children = toList().sortedBy { it.name }.map { it.toHealthComponent(aggregator) }
        HealthComponentResponse(name, aggregator.getAggregateStatus(children.map { Status(it.status) }.toSet()).code, components = children)
    }
    else -> HealthComponentResponse(name, Status.UNKNOWN.code)
}

