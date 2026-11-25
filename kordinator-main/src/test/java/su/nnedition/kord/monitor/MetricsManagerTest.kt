package su.nnedition.kord.monitor

import org.junit.Assert.assertSame
import org.junit.Test
import java.util.function.Predicate

class MetricsManagerTest {
    @Test
    fun disabledMetricsUseReusableNoOpTimers() {
        val manager = MetricsManager(Predicate { false })
        try {
            val first = manager.timer(this, "first")
            val second = manager.timer(this, "second")
            assertSame(first, second)

            val conditionedTimer = manager.condition(false).timer(this, "conditional")
            assertSame(first, conditionedTimer)
            first.close()
            conditionedTimer.close()
        } finally {
            manager.close()
        }
    }
}
