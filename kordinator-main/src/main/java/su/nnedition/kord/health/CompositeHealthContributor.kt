package su.nnedition.kord.health

/** A composite health contributor containing named child health contributors. */
interface CompositeHealthContributor : HealthContributor, Iterable<NamedContributor<HealthContributor>> {
    fun getContributor(name: String): HealthContributor?

    companion object {
        @JvmStatic
        fun fromMap(map: Map<String, HealthContributor>): CompositeHealthContributor =
            DefaultCompositeHealthContributor(map)
    }
}
