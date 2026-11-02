package org.airsonic.player.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Configurable filter for setting HTTP response headers. Can be used, for instance, to
 * set cache control directives for certain resources.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2005/08/14 13:14:47 $
 */
public class ResponseHeaderFilter implements Filter {
    private FilterConfig filterConfig;

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;

        // Sets the provided HTTP response parameters
        for (var enumeration = filterConfig.getInitParameterNames(); enumeration.hasMoreElements();) {
            String headerName = enumeration.nextElement();
            response.setHeader(headerName, filterConfig.getInitParameter(headerName));
        }

        // pass the request/response on
        chain.doFilter(req, response);
    }

    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public void destroy() {
        this.filterConfig = null;
    }
}