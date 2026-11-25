package su.nnedition.kord.health

/** Registry for named health contributors. */
interface HealthContributorRegistry : Iterable<NamedContributor<HealthContributor>> {
    fun registerContributor(name: String, contributor: HealthContributor)
    fun unregisterContributor(name: String): HealthContributor?
    fun getContributor(name: String): HealthContributor?
}
