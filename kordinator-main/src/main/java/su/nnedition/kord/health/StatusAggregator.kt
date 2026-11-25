package su.nnedition.kord.health

/** Aggregates multiple health statuses into a single representative status. */
interface StatusAggregator {
    fun getAggregateStatus(statuses: Set<Status>?): Status
}
