package org.airsonic.player.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import org.airsonic.player.monitor.MetricsManager
import org.springframework.beans.factory.annotation.Autowired
import java.io.IOException

class MetricsFilter : Filter {
    @Autowired
    private lateinit var metricsManager: MetricsManager

    override fun init(filterConfig: FilterConfig) {
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpServletRequest = request as HttpServletRequest

        val timerName = httpServletRequest.requestURI

        metricsManager.condition(timerName.contains("main.view")).timer(this, timerName).use {
            chain.doFilter(request, response)
        }
    }

    override fun destroy() {
    }
}
