package org.airsonic.player.filter;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Intercepts exceptions thrown by RESTController.
 */
public class RESTFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(RESTFilter.class);

    private final JAXBWriter jaxbWriter = new JAXBWriter();
    private final RestErrorPolicy errorPolicy = new RestErrorPolicy();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        try {
            chain.doFilter(req, res);
        } catch (Throwable x) {
            handleException(x, (HttpServletRequest) req, (HttpServletResponse) res);
        }
    }

    private void handleException(Throwable err, HttpServletRequest request, HttpServletResponse response) {
        if (err instanceof ServletException && err.getCause() != null) {
            err = err.getCause();
        }

        RestErrorPolicy.Decision decision = errorPolicy.classify(err);

        LOG.warn("Error in REST API", err);

        try {
            jaxbWriter.writeErrorResponse(request, response, decision.errorCode(), decision.message());
        } catch (Exception e) {
            LOG.error("Failed to write error response.", e);
        }

    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
