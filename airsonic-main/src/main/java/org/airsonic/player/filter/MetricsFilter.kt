package org.airsonic.player.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.monitor.MetricsManager
import java.io.IOException

class MetricsFilter(private val metricsManager: MetricsManager) : Filter {
    private val metricsPolicy = RequestMetricsPolicy()

    override fun init(filterConfig: FilterConfig) {
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpServletRequest = request as HttpServletRequest

        val requestUri = httpServletRequest.requestURI

        metricsManager.condition(metricsPolicy.shouldMeasure(requestUri))
            .timer(this, metricsPolicy.metricName(requestUri)).use {
                chain.doFilter(request, response)
            }
    }

    override fun destroy() {
    }
}
