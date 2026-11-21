package org.airsonic.player.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.airsonic.player.health.DatabaseHealthIndicator
import org.airsonic.player.health.DefaultHealthContributorRegistry
import org.airsonic.player.health.DiskSpaceHealthIndicator
import org.airsonic.player.health.HealthContributorRegistry
import org.airsonic.player.health.PingHealthIndicator
import org.airsonic.player.health.SimpleStatusAggregator
import org.airsonic.player.health.StatusAggregator
import org.airsonic.player.monitor.MetricsManager
import org.airsonic.player.service.SettingsService
import javax.sql.DataSource

/** Creates health, metrics and server-status infrastructure. */
object RuntimeHealthFactory {
    @JvmStatic
    fun createMetricsManager(configuration: RuntimeConfiguration) = MetricsManager(configuration)

    @JvmStatic
    fun createHealthContributorRegistry(dataSource: DataSource): HealthContributorRegistry =
        DefaultHealthContributorRegistry().apply {
            registerContributor("db", DatabaseHealthIndicator(dataSource))
            registerContributor("diskSpace", DiskSpaceHealthIndicator(SettingsService.getAirsonicHome()))
            registerContributor("ping", PingHealthIndicator())
        }

    @JvmStatic
    fun createStatusAggregator(configuration: RuntimeConfiguration): StatusAggregator =
        SimpleStatusAggregator(configuration.getHealthStatusOrder())

    @JvmStatic
    fun createMeterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
