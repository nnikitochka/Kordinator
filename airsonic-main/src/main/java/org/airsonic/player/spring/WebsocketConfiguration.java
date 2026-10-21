package org.airsonic.player.spring;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    public static final String UNDERLYING_SERVLET_REQUEST = "servletRequest";

    private TaskScheduler messageBrokerTaskScheduler;
    private String contextPath;

    @Autowired
    public void setMessageBrokerTaskScheduler(TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Autowired
    @Value("${server.servlet.context-path:/}")
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(messageBrokerTaskScheduler)
                .setHeartbeatValue(new long[] { 20000, 20000 });
        config.setApplicationDestinationPrefixes("/app");

        // this ensures publish order is serial at the cost of no parallelization and
        // performance - if performance is bad, this should be turned off
        config.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/websocket")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ServletRequestCaptureHandshakeInterceptor(contextPath))
                .withSockJS()
                .setClientLibraryUrl("../../script/sockjs-1.5.0.min.js");
    }

    public static class ServletRequestCaptureHandshakeInterceptor implements HandshakeInterceptor {
        private final String contextPath;

        public ServletRequestCaptureHandshakeInterceptor(String contextPath) {
            this.contextPath = contextPath;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

            // Set servlet request attribute to WebSocket session
            if (request instanceof ServletServerHttpRequest) {
                attributes.put(UNDERLYING_SERVLET_REQUEST,
                        new WebsocketInterceptedServletRequest((ServletServerHttpRequest) request, contextPath));
            }

            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                Exception exception) {
        }
    }

    public static class WebsocketInterceptedServletRequest implements HttpServletRequest {
        private final Map<String, Object> attributes;
        private final Map<String, String[]> parameters;
        private final Map<String, List<String>> headers;
        private final String contextPath;
        private final String characterEncoding;
        private final String contentType;
        private final String protocol;
        private final String scheme;
        private final String serverName;
        private final int serverPort;
        private final String remoteAddr;
        private final String remoteHost;
        private final int remotePort;
        private final String localName;
        private final String localAddr;
        private final int localPort;
        private final boolean secure;
        private final ServletContext servletContext;
        private final Cookie[] cookies;
        private final String authType;
        private final String method;
        private final String queryString;
        private final String remoteUser;
        private final Principal userPrincipal;
        private final String requestedSessionId;
        private final String requestUri;
        private final String requestUrl;
        private final String servletPath;
        private final HttpSession session;
        private final boolean requestedSessionIdValid;
        private final boolean requestedSessionIdFromCookie;
        private final boolean requestedSessionIdFromUrl;

        public WebsocketInterceptedServletRequest(ServletServerHttpRequest originalRequest, String contextPath) {
            HttpServletRequest request = originalRequest.getServletRequest();
            this.attributes = copyAttributes(request);
            this.parameters = copyParameters(request);
            this.headers = copyHeaders(originalRequest);
            this.contextPath = StringUtils.defaultIfBlank(request.getContextPath(), contextPath);
            this.characterEncoding = request.getCharacterEncoding();
            this.contentType = request.getContentType();
            this.protocol = request.getProtocol();
            this.scheme = request.getScheme();
            this.serverName = request.getServerName();
            this.serverPort = request.getServerPort();
            this.remoteAddr = request.getRemoteAddr();
            this.remoteHost = request.getRemoteHost();
            this.remotePort = request.getRemotePort();
            this.localName = request.getLocalName();
            this.localAddr = request.getLocalAddr();
            this.localPort = request.getLocalPort();
            this.secure = request.isSecure();
            this.servletContext = request.getServletContext();
            Cookie[] requestCookies = request.getCookies();
            this.cookies = requestCookies == null ? null : requestCookies.clone();
            this.authType = request.getAuthType();
            this.method = request.getMethod();
            this.queryString = request.getQueryString();
            this.remoteUser = request.getRemoteUser();
            this.userPrincipal = request.getUserPrincipal();
            this.requestedSessionId = request.getRequestedSessionId();
            this.requestUri = request.getRequestURI();
            this.requestUrl = request.getRequestURL().toString();
            this.servletPath = request.getServletPath();
            // WebSocket commands still use the HTTP session to retain the selected player.
            // Capture the session facade now; unlike the request facade it remains valid after
            // the handshake completes.
            this.session = request.getSession();
            this.requestedSessionIdValid = request.isRequestedSessionIdValid();
            this.requestedSessionIdFromCookie = request.isRequestedSessionIdFromCookie();
            this.requestedSessionIdFromUrl = request.isRequestedSessionIdFromURL();
        }

        private static Map<String, Object> copyAttributes(HttpServletRequest request) {
            Map<String, Object> snapshot = new HashMap<>();
            Enumeration<String> names = request.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                snapshot.put(name, request.getAttribute(name));
            }
            return snapshot;
        }

        private static Map<String, String[]> copyParameters(HttpServletRequest request) {
            Map<String, String[]> snapshot = new LinkedHashMap<>();
            request.getParameterMap().forEach((name, values) -> snapshot.put(name, values.clone()));
            return snapshot;
        }

        private static Map<String, List<String>> copyHeaders(ServletServerHttpRequest request) {
            Map<String, List<String>> snapshot = new LinkedHashMap<>();
            request.getHeaders().forEach((name, values) -> snapshot.put(name, new ArrayList<>(values)));
            return snapshot;
        }

        @Override
        public Object getAttribute(String name) {
            Object attribute = attributes.get(name);
            return attribute == null ? headers.get(name) : attribute;
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            return characterEncoding;
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1L;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            throw new UnsupportedOperationException("WebSocket handshake request body is unavailable");
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? getHeader(name) : values[0];
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = parameters.get(name);
            return values == null ? null : values.clone();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> snapshot = new LinkedHashMap<>();
            parameters.forEach((name, values) -> snapshot.put(name, values.clone()));
            return Collections.unmodifiableMap(snapshot);
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public String getServerName() {
            return serverName;
        }

        @Override
        public int getServerPort() {
            return serverPort;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            return remoteHost;
        }

        @Override
        public void setAttribute(String name, Object o) {
            if (getSession() == null) {
                return;
            }

            getSession().setAttribute(name, o);
        }

        @Override
        public void removeAttribute(String name) {
            if (getSession() == null) {
                return;
            }
            getSession().removeAttribute(name);
        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public jakarta.servlet.ServletConnection getServletConnection() {
            return null;
        }

        @Override
        public String getProtocolRequestId() {
            return null;
        }

        @Override
        public String getRequestId() {
            return null;
        }

        @Override
        public int getRemotePort() {
            return remotePort;
        }

        @Override
        public String getLocalName() {
            return localName;
        }

        @Override
        public String getLocalAddr() {
            return localAddr;
        }

        @Override
        public int getLocalPort() {
            return localPort;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
                throws IllegalStateException {
            return null;
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType() {
            return null;
        }

        @Override
        public String getAuthType() {
            return authType;
        }

        @Override
        public Cookie[] getCookies() {
            return cookies == null ? null : cookies.clone();
        }

        @Override
        public long getDateHeader(String name) {
            return -1L;
        }

        @Override
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            if (values == null) {
                values = headers.entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = headers.get(name);
            if (values == null) {
                values = headers.entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(Collections.emptyList());
            }
            return Collections.enumeration(values);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public int getIntHeader(String name) {
            String value = getHeader(name);
            return value == null ? -1 : Integer.parseInt(value);
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getQueryString() {
            return queryString;
        }

        @Override
        public String getRemoteUser() {
            return remoteUser;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return userPrincipal;
        }

        @Override
        public String getRequestedSessionId() {
            return requestedSessionId;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(requestUrl);
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }

        @Override
        public HttpSession getSession() {
            return session;
        }

        @Override
        public String changeSessionId() {
            throw new UnsupportedOperationException("WebSocket handshake session cannot be changed");
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return requestedSessionIdValid;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return requestedSessionIdFromCookie;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return requestedSessionIdFromUrl;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
            throw new UnsupportedOperationException("WebSocket handshake request cannot authenticate");
        }

        @Override
        public void login(String username, String password) throws ServletException {
        }

        @Override
        public void logout() throws ServletException {
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            return null;
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
            return null;
        }

    }
}
