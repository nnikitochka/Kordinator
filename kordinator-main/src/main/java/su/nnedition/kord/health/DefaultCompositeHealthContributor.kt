package su.nnedition.kord.health

import java.util.concurrent.ConcurrentHashMap

/** Default implementation of [CompositeHealthContributor]. */
class DefaultCompositeHealthContributor(map: Map<String, HealthContributor>?) : CompositeHealthContributor {
    private val contributors = ConcurrentHashMap<String, HealthContributor>().apply {
        map?.let(::putAll)
    }

    override fun getContributor(name: String): HealthContributor? = contributors[name]

    override fun iterator(): Iterator<NamedContributor<HealthContributor>> =
        contributors.entries.map { NamedContributor.of(it.key, it.value) }.iterator()
}
