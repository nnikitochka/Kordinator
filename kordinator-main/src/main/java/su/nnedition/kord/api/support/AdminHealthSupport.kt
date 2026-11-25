package su.nnedition.kord.api.support

import su.nnedition.kord.domain.MusicFolder
import su.nnedition.kord.health.CompositeHealthContributor
import su.nnedition.kord.health.Health
import su.nnedition.kord.health.HealthContributor
import su.nnedition.kord.health.HealthIndicator
import su.nnedition.kord.health.NamedContributor
import su.nnedition.kord.health.Status
import su.nnedition.kord.health.StatusAggregator
import su.nnedition.kord.api.module.admin.library.LibraryResponse
import su.nnedition.kord.api.module.admin.status.HealthComponentResponse

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
