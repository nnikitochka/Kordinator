package su.nnedition.kord.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import su.nnedition.kord.health.DatabaseHealthIndicator
import su.nnedition.kord.health.DefaultHealthContributorRegistry
import su.nnedition.kord.health.DiskSpaceHealthIndicator
import su.nnedition.kord.health.HealthContributorRegistry
import su.nnedition.kord.health.PingHealthIndicator
import su.nnedition.kord.health.SimpleStatusAggregator
import su.nnedition.kord.health.StatusAggregator
import su.nnedition.kord.monitor.MetricsManager
import su.nnedition.kord.service.SettingsService
import javax.sql.DataSource

/** Creates health, metrics and server-status infrastructure. */
object RuntimeHealthFactory {
    @JvmStatic
    fun createMetricsManager(configuration: RuntimeConfiguration) = MetricsManager(configuration)

    @JvmStatic
    fun createHealthContributorRegistry(dataSource: DataSource): HealthContributorRegistry =
        DefaultHealthContributorRegistry().apply {
            registerContributor("db", DatabaseHealthIndicator(dataSource))
            registerContributor("diskSpace", DiskSpaceHealthIndicator(SettingsService.getKordinatorHome()))
            registerContributor("ping", PingHealthIndicator())
        }

    @JvmStatic
    fun createStatusAggregator(configuration: RuntimeConfiguration): StatusAggregator =
        SimpleStatusAggregator(configuration.getHealthStatusOrder())

    @JvmStatic
    fun createMeterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
