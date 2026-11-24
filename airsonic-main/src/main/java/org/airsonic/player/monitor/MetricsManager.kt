package org.airsonic.player.monitor

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter
import org.airsonic.player.runtime.RuntimeConfiguration
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

open class MetricsManager : AutoCloseable {
    private val propertyChecker: Predicate<String>

    constructor() : this(Predicate { false })

    constructor(config: RuntimeConfiguration?) : this(
        Predicate { property -> config?.asMap()?.containsKey(property) == true || System.getProperty(property) != null },
    )

    constructor(propertyChecker: Predicate<String>?) {
        this.propertyChecker = propertyChecker ?: Predicate { false }
    }

    private fun configureMetricsActivation() {
        if (propertyChecker.test("Metrics")) {
            synchronized(lock) {
                metricsActivatedByConfiguration = true
                reporter = JmxReporter.forRegistry(metrics)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build()
                reporter?.start()
            }
        } else {
            metricsActivatedByConfiguration = false
        }
    }

    private fun metricsActivatedByConfiguration(): Boolean {
        metricsActivatedByConfiguration?.let { return it }
        synchronized(lock) {
            metricsActivatedByConfiguration?.let { return it }
            configureMetricsActivation()
        }
        return metricsActivatedByConfiguration == true
    }

    open fun timer(clazz: Class<*>, name: String): Timer =
        if (metricsActivatedByConfiguration()) TimerBuilder().timer(clazz, name) else nullTimer

    open fun timer(ref: Any, name: String): Timer = timer(ref.javaClass, name)

    open fun condition(ifTrue: Boolean): TimerBuilder = when {
        !metricsActivatedByConfiguration() -> nullTimerBuilder
        !ifTrue -> conditionFalseTimerBuilder
        else -> TimerBuilder()
    }

    open class TimerBuilder {
        open fun timer(clazz: Class<*>, name: String): Timer {
            val timer = metrics.timer(MetricRegistry.name(clazz, name))
            return Timer.from(timer.time())
        }

        open fun timer(ref: Any, name: String): Timer = timer(ref.javaClass, name)
    }

    open class Timer protected constructor(private val timerContext: com.codahale.metrics.Timer.Context?) : AutoCloseable {
        open override fun close() {
            timerContext?.stop()
        }

        companion object {
            internal fun from(timerContext: com.codahale.metrics.Timer.Context): Timer = Timer(timerContext)
        }
    }

    open override fun close() {
        synchronized(lock) {
            reporter?.let {
                it.stop()
                it.close()
                reporter = null
            }
            metricsActivatedByConfiguration = null
        }
    }

    private class NullTimer : Timer(null)

    private class NullTimerBuilder : TimerBuilder() {
        override fun timer(clazz: Class<*>, name: String): Timer = nullTimer
    }

    companion object {
        private val metrics = MetricRegistry()
        private val lock = Any()
        @Volatile
        private var metricsActivatedByConfiguration: Boolean? = null
        private var reporter: JmxReporter? = null
        private val nullTimer = NullTimer()
        private val conditionFalseTimerBuilder = NullTimerBuilder()
        private val nullTimerBuilder = NullTimerBuilder()
    }
}
