package org.airsonic.player.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestMetricsPolicyTest {
    private val policy = RequestMetricsPolicy()

    @Test
    fun preservesLegacyMainPageMeasurementRuleAndMetricName() {
        assertTrue(policy.shouldMeasure("/airsonic/main.view"))
        assertFalse(policy.shouldMeasure("/airsonic/rest/getSong"))
        assertEquals("/airsonic/main.view", policy.metricName("/airsonic/main.view"))
    }
}
