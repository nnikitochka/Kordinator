package org.airsonic.player.spring

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

/** Spring bootstrap adapter for the explicit Ktor bean factories. */
@Configuration
class KtorSpringConfigurationAdapter : KtorConfiguration() {
    @Bean
    fun jCacheCacheManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): javax.cache.CacheManager =
        runtime.createCacheManager()

    @Bean(destroyMethod = "close")
    fun metricsManager(runtime: org.airsonic.player.runtime.RuntimeCompositionRoot): org.airsonic.player.monitor.MetricsManager =
        runtime.createMetricsManager()

    @Bean
    fun serverHealthContributors(
        runtime: org.airsonic.player.runtime.RuntimeCompositionRoot,
        dataSource: javax.sql.DataSource,
    ): org.airsonic.player.health.HealthContributorRegistry = runtime.createHealthContributorRegistry(dataSource)
}
