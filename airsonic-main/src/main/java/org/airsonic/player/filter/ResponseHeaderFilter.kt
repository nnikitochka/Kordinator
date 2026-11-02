package org.airsonic.player.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

/**
 * Configurable filter for setting HTTP response headers. Can be used, for instance, to
 * set cache control directives for certain resources.
 * 
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2005/08/14 13:14:47 $
 */
class ResponseHeaderFilter : Filter {
    private var filterConfig: FilterConfig? = null

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(req: ServletRequest?, res: ServletResponse?, chain: FilterChain) {
        val response = res as HttpServletResponse

        // Sets the provided HTTP response parameters
        val enumeration = filterConfig!!.initParameterNames
        while (enumeration.hasMoreElements()) {
            val headerName = enumeration.nextElement()
            response.setHeader(headerName, filterConfig!!.getInitParameter(headerName))
        }

        // pass the request/response on
        chain.doFilter(req, response)
    }

    override fun init(filterConfig: FilterConfig?) {
        this.filterConfig = filterConfig
    }

    override fun destroy() {
        this.filterConfig = null
    }
}