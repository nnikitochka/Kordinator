package su.nnedition.kord.health

import java.util.Objects

/** A named health contributor entry. */
interface NamedContributor<C : HealthContributor> {
    val name: String
    val contributor: C

    companion object {
        @JvmStatic
        fun <C : HealthContributor> of(name: String, contributor: C): NamedContributor<C> {
            Objects.requireNonNull(name, "name")
            Objects.requireNonNull(contributor, "contributor")
            return object : NamedContributor<C> {
                override val name: String = name
                override val contributor: C = contributor
                override fun toString(): String = "$name=$contributor"
            }
        }
    }
}
