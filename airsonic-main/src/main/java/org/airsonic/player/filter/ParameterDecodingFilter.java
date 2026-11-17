package org.airsonic.player.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Servlet filter which decodes HTTP request parameters.  If a parameter name ends with
 * "Utf8Hex" ({@link #PARAM_SUFFIX}) , the corresponding parameter value is assumed to be the
 * hexadecimal representation of the UTF-8 bytes of the value.
 * <p/>
 * Used to support request parameter values of any character encoding.
 *
 * @author Sindre Mehus
 */
public class ParameterDecodingFilter implements Filter {

    public static final String PARAM_SUFFIX = ParameterDecodingPolicy.PARAM_SUFFIX;
    private static final Logger LOG = LoggerFactory.getLogger(ParameterDecodingFilter.class);
    private static final ParameterDecodingPolicy POLICY = new ParameterDecodingPolicy();

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Wrap request in decoder.
        ServletRequest decodedRequest = new DecodingServletRequestWrapper((HttpServletRequest) request);

        // Pass the request/response on
        chain.doFilter(decodedRequest, response);
    }

    public void init(FilterConfig filterConfig) {
    }

    public void destroy() {
    }

    private static class DecodingServletRequestWrapper extends HttpServletRequestWrapper {

        public DecodingServletRequestWrapper(HttpServletRequest servletRequest) {
            super(servletRequest);
        }

        @Override
        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            if (values == null || values.length == 0) {
                return null;
            }
            return values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            var map = super.getParameterMap();
            Map<String, String[]> result = new HashMap<>();

            for (var entry : map.entrySet()) {
                String name = entry.getKey();
                String[] values = entry.getValue();

                if (POLICY.isEncodedParameter(name)) {
                    result.put(POLICY.decodedParameterName(name), decode(values));
                } else {
                    result.put(name, values);
                }
            }
            return result;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            var names = super.getParameterNames();
            Vector<String> v = new Vector<>();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (POLICY.isEncodedParameter(name)) {
                    name = POLICY.decodedParameterName(name);
                }
                v.add(name);
            }

            return v.elements();
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values != null) {
                return values;
            }

            values = super.getParameterValues(name + PARAM_SUFFIX);
            if (values != null) {
                return decode(values);
            }

            return null;
        }

        private String[] decode(String[] values) {
            return POLICY.decodeValues(values,
                    value -> LOG.error("Failed to decode parameter value '{}'", value));
        }

    }

}
