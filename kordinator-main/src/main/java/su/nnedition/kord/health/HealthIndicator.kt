package su.nnedition.kord.health

/** Functional interface for a health indicator that provides a health snapshot. */
fun interface HealthIndicator : HealthContributor {
    fun health(): Health
}
