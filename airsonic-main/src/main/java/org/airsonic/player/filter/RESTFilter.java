package org.airsonic.player.filter;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.util.NestedServletException;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Intercepts exceptions thrown by RESTController.
 */
public class RESTFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(RESTFilter.class);

    private final JAXBWriter jaxbWriter = new JAXBWriter();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        try {
            chain.doFilter(req, res);
        } catch (Throwable x) {
            handleException(x, (HttpServletRequest) req, (HttpServletResponse) res);
        }
    }

    private void handleException(Throwable err, HttpServletRequest request, HttpServletResponse response) {
        if (err instanceof NestedServletException && err.getCause() != null) {
            err = err.getCause();
        }

        ErrorCode code = (err instanceof ServletRequestBindingException) ? ErrorCode.MISSING_PARAMETER : ErrorCode.GENERIC;
        String msg = getErrorMessage(err);

        // This happens often and outside of the control of the server, so
        // we catch Tomcat/Jetty "connection aborted by client" exceptions
        // and display a short error message.
        boolean shouldCatch = Util.isInstanceOfClassName(err, "org.apache.catalina.connector.ClientAbortException");
        if (shouldCatch) {
            LOG.info("{}: Client unexpectedly closed connection while loading {} ({})", request.getRemoteAddr(),
                    Util.getAnonymizedURLForRequest(request), err.getMessage());
        } else {
            LOG.warn("Error in REST API", err);

            try {
                jaxbWriter.writeErrorResponse(request, response, code, msg);
            } catch (Exception e) {
                LOG.error("Failed to write error response.", e);
            }
        }

    }

    private String getErrorMessage(Throwable x) {
        if (x.getMessage() != null) {
            return x.getMessage();
        }
        return x.getClass().getSimpleName();
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
