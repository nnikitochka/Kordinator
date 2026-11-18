package org.airsonic.player.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class NetworkService {
    private static final String X_FORWARDED_SERVER = "X-Forwarded-Server";
    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FORWARDED_SCHEME = "X-Forwarded-Scheme";

    private final static Logger LOG = LoggerFactory.getLogger(NetworkService.class);

    public static String getBaseUrl(HttpServletRequest request) {
        try {
            URI uri;
            try {
                uri = calculateProxyUri(request);
            } catch (Exception e) {
                LOG.debug("Could not calculate proxy uri: {}", e.getMessage());
                uri = calculateNonProxyUri(request);
            }

            String baseUrl = uri + "/";
            LOG.debug("Calculated base url to {}", baseUrl);
            return baseUrl;
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Could not calculate base url: " + e.getMessage());
        }
    }

    private static URI calculateProxyUri(HttpServletRequest request) throws URISyntaxException {
        String xForwardedHost = request.getHeader(X_FORWARDED_HOST);
        // If the request has been through multiple reverse proxies,
        // We need to return the original Host that the client used
        if (xForwardedHost != null) {
            xForwardedHost = xForwardedHost.split(",")[0];
        }

        if (!isValidXForwardedHost(xForwardedHost)) {
            xForwardedHost = request.getHeader(X_FORWARDED_SERVER);

            // If the request has been through multiple reverse proxies,
            // We need to return the original Host that the client used
            if (xForwardedHost != null) {
                xForwardedHost = xForwardedHost.split(",")[0];
            }

            if (!isValidXForwardedHost(xForwardedHost)) {
                xForwardedHost = request.getHeader(X_FORWARDED_FOR);

                // If the request has been through multiple reverse proxies,
                // We need to return the original Host that the client used
                if (xForwardedHost != null) {
                    xForwardedHost = xForwardedHost.split(",")[0];
                }

                if (!isValidXForwardedHost(xForwardedHost)) {
                    throw new RuntimeException("Cannot calculate proxy uri without HTTP headers: " + X_FORWARDED_HOST + ", " + X_FORWARDED_SERVER + ", " + X_FORWARDED_FOR);
                }
            }
        }

        URI proxyHost = new URI("ignored://" + xForwardedHost);
        String host = proxyHost.getHost();
        int port = proxyHost.getPort();
        String scheme = request.getHeader(X_FORWARDED_PROTO);
        if (StringUtils.isBlank(scheme)) {
            scheme = request.getHeader(X_FORWARDED_SCHEME);
            if (StringUtils.isBlank(scheme)) {
                throw new RuntimeException("Scheme not provided");
            }
        }

        return new URI(scheme, null, host, port, getContextPath(request), null, null);
    }

    private static boolean isValidXForwardedHost(String xForwardedHost) {
        return StringUtils.isNotBlank(xForwardedHost) && !StringUtils.equals("null", xForwardedHost);
    }

    private static URI calculateNonProxyUri(HttpServletRequest request) throws MalformedURLException, URISyntaxException {
        URL url = new URL(request.getRequestURL().toString());
        String host = url.getHost();
        String scheme = url.getProtocol();
        int port = url.getPort();
        String userInfo = url.getUserInfo();
        return new URI(scheme, userInfo, host, port, getContextPath(request), null, null);
    }

    private static String getContextPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (contextPath == null || "/".equals(contextPath)) {
            return "";
        }
        return contextPath;
    }
}
