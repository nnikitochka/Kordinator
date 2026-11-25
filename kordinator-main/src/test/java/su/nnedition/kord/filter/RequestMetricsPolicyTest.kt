package su.nnedition.kord.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestMetricsPolicyTest {
    private val policy = RequestMetricsPolicy()

    @Test
    fun preservesLegacyMainPageMeasurementRuleAndMetricName() {
        assertTrue(policy.shouldMeasure("/kordinator/main.view"))
        assertFalse(policy.shouldMeasure("/kordinator/rest/getSong"))
        assertEquals("/kordinator/main.view", policy.metricName("/kordinator/main.view"))
    }
}
