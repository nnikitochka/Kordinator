package org.airsonic.player.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import java.io.IOException

/**
 * Configurable filter for setting the character encoding to use for the HTTP request.
 * Typically used to set UTF-8 encoding when reading request parameters with non-Latin
 * content.
 * 
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2006/03/01 16:58:08 $
 */
class RequestEncodingFilter(private var encoding: String? = RequestEncodingPolicy.DEFAULT_ENCODING) : Filter {

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        request.setCharacterEncoding(encoding)

        // Pass the request/response on
        chain.doFilter(req, res)
    }

    override fun init(filterConfig: FilterConfig) {
        encoding = filterConfig.getInitParameter("encoding") ?: encoding
    }

    override fun destroy() {
        encoding = null
    }
}
