package org.airsonic.player.security;

import org.airsonic.player.service.JWTSecurityService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class JWTRequestParameterProcessingFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(JWTRequestParameterProcessingFilter.class);
    private final String jwtKey;
    private final AuthenticationFailureHandler failureHandler;
    private final JwtTokenVerifier verifier = new JwtTokenVerifier();

    protected JWTRequestParameterProcessingFilter(String jwtKey, String failureUrl) {
        this.jwtKey = jwtKey;
        failureHandler = new SimpleUrlAuthenticationFailureHandler(failureUrl);
    }

    public Authentication attemptAuthentication(Optional<JWTAuthenticationToken> token) throws AuthenticationException {
        if (token.isEmpty() || !(token.get().getCredentials() instanceof String)) {
            throw new BadCredentialsException("JWT credentials are missing");
        }
        JWTAuthenticationToken request = token.get();
        String rawToken = (String) request.getCredentials();
        JwtTokenVerifier.Result result = verifier.verifyRequest(jwtKey, rawToken, request.getRequestedPath());
        if (result.getFailure() == JwtTokenVerifier.Failure.EXPIRED) {
            throw new CredentialsExpiredException("Credentials have expired");
        }
        if (result.getFailure() == JwtTokenVerifier.Failure.PATH_MISMATCH) {
            throw new InsufficientAuthenticationException("Credentials not valid for requested path " + request.getRequestedPath());
        }
        if (!result.isVerified()) {
            throw new BadCredentialsException("Error verifying JWT");
        }
        DecodedJWT decoded = result.getToken();
        return new JWTAuthenticationToken(decoded.getSubject(), rawToken, request.getRequestedPath(), JWT_AUTHORITIES, decoded);
    }

    private static Optional<JWTAuthenticationToken> findToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getParameter(JWTSecurityService.JWT_PARAM_NAME))
                .filter(StringUtils::isNotEmpty)
                .map(t -> new JWTAuthenticationToken(null, t, request.getRequestURI().substring(request.getContextPath().length() + 1) + "?" + request.getQueryString()));
    }

    private static final List<GrantedAuthority> JWT_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("IS_AUTHENTICATED_FULLY"),
            new SimpleGrantedAuthority("ROLE_TEMP"));

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        Optional<JWTAuthenticationToken> token = findToken(request);
        if (token.isEmpty()) {
            chain.doFilter(req, resp);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Request is to process authentication");
        }

        Authentication authResult;

        try {
            authResult = attemptAuthentication(token);
            if (authResult == null) {
                // return immediately as subclass has indicated that it hasn't completed
                // authentication
                return;
            }
        } catch (InternalAuthenticationServiceException failed) {
            LOG.error(
                    "An internal error occurred while trying to authenticate the user.",
                    failed);
            unsuccessfulAuthentication(request, response, failed);

            return;
        } catch (AuthenticationException e) {
            unsuccessfulAuthentication(request, response, e);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication success. Updating SecurityContextHolder to contain: "
                    + authResult);
        }

        SecurityContextHolder.getContext().setAuthentication(authResult);

        chain.doFilter(request, response);
    }

    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response, AuthenticationException failed)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication request failed: " + failed.toString(), failed);
            LOG.debug("Updated SecurityContextHolder to contain null Authentication");
            LOG.debug("Delegating to authentication failure handler " + failureHandler);
        }

        failureHandler.onAuthenticationFailure(request, response, failed);
    }

    @Override
    public void destroy() {

    }

}
