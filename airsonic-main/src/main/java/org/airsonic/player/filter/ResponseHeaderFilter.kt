package org.airsonic.player.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import java.util.LinkedHashMap

/**
 * Configurable filter for setting HTTP response headers. Can be used, for instance, to
 * set cache control directives for certain resources.
 * 
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2005/08/14 13:14:47 $
 */
class ResponseHeaderFilter(initialHeaders: Map<String, String> = emptyMap()) : Filter {
    private var headers = StaticResponseHeaders(initialHeaders)

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(req: ServletRequest?, res: ServletResponse?, chain: FilterChain) {
        val response = res as HttpServletResponse

        headers.apply(response::setHeader)

        // pass the request/response on
        chain.doFilter(req, response)
    }

    override fun init(filterConfig: FilterConfig?) {
        val values = LinkedHashMap<String, String>()
        val enumeration = filterConfig!!.initParameterNames
        while (enumeration.hasMoreElements()) {
            val headerName = enumeration.nextElement()
            values[headerName] = filterConfig.getInitParameter(headerName)
        }
        headers = StaticResponseHeaders(values)
    }

    override fun destroy() {
        headers = StaticResponseHeaders(emptyMap())
    }
}
