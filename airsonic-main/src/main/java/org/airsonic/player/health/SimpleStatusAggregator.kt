package org.airsonic.player.health

/** Standard status aggregator ordering statuses by severity priority. */
class SimpleStatusAggregator : StatusAggregator {
    private val order: List<String>

    constructor() : this(DEFAULT_ORDER)

    constructor(vararg order: String) : this(order.toList())

    constructor(order: List<String>) {
        this.order = order.map { it.trim().uppercase() }
    }

    override fun getAggregateStatus(statuses: Set<Status>?): Status {
        if (statuses.isNullOrEmpty()) return Status.UNKNOWN

        for (priorityCode in order) {
            statuses.firstOrNull { priorityCode.equals(it.code, ignoreCase = true) }?.let { return it }
        }
        return statuses.first()
    }

    companion object {
        @JvmField
        val DEFAULT_ORDER: List<String> = listOf("DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN")
    }
}
