package su.nnedition.kord.filter

/** Framework-neutral request naming and inclusion rules for the legacy HTTP timer. */
class RequestMetricsPolicy {
    fun metricName(requestUri: String): String = requestUri

    fun shouldMeasure(requestUri: String): Boolean = requestUri.contains(MAIN_PAGE_MARKER)

    private companion object {
        const val MAIN_PAGE_MARKER = "main.view"
    }
}
