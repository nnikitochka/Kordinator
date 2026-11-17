package org.airsonic.player.spring

import jakarta.servlet.DispatcherType
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.filter.BootstrapVerificationFilter
import org.airsonic.player.filter.MetricsFilter
import org.airsonic.player.filter.ParameterDecodingFilter
import org.airsonic.player.filter.RESTFilter
import org.airsonic.player.filter.RequestEncodingFilter
import org.airsonic.player.filter.RequestEncodingPolicy
import org.airsonic.player.filter.ResponseHeaderFilter
import org.airsonic.player.monitor.MetricsManager

/** Runs the legacy servlet policies at the Ktor servlet boundary in their original order. */
internal class KtorServletFilterPipeline(metricsManager: MetricsManager) {
    private data class Registration(val filter: Filter, val matches: (String) -> Boolean)

    private val registrations = listOf(
        Registration(BootstrapVerificationFilter()) { true },
        Registration(ParameterDecodingFilter()) { true },
        Registration(RESTFilter()) { it == "/rest" || it.startsWith("/rest/") },
        Registration(RequestEncodingFilter(RequestEncodingPolicy.DEFAULT_ENCODING)) { true },
        Registration(ResponseHeaderFilter(mapOf("Cache-Control" to "max-age=36000"))) {
            pathMatches(it, "/icons") || pathMatches(it, "/style") || pathMatches(it, "/script") ||
                it == "/coverArt.view" || it == "/avatar.view"
        },
        Registration(ResponseHeaderFilter(mapOf(
            "Cache-Control" to "no-cache, post-check=0, pre-check=0",
            "Pragma" to "no-cache",
            "Expires" to "Thu, 01 Dec 1994 16:00:00 GMT",
        ))) {
            it in setOf("/statusChart.view", "/userChart.view", "/playQueue.view", "/help.view", "/top.view", "/home.view")
        },
        Registration(MetricsFilter(metricsManager)) { true },
    )

    fun service(request: HttpServletRequest, response: HttpServletResponse, terminal: FilterChain) {
        if (request.dispatcherType != DispatcherType.REQUEST) {
            terminal.doFilter(request, response)
            return
        }

        val applicable = registrations.filter { it.matches(request.servletPath) }
        invoke(0, applicable, request, response, terminal)
    }

    private fun invoke(
        index: Int,
        filters: List<Registration>,
        request: ServletRequest,
        response: ServletResponse,
        terminal: FilterChain,
    ) {
        if (index == filters.size) {
            terminal.doFilter(request, response)
            return
        }
        filters[index].filter.doFilter(request, response, FilterChain { nextRequest, nextResponse ->
            invoke(index + 1, filters, nextRequest, nextResponse, terminal)
        })
    }

    private companion object {
        fun pathMatches(path: String, prefix: String): Boolean = path == prefix || path.startsWith("$prefix/")
    }
}
