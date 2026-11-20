package org.airsonic.player.security;

import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class GlobalSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalSecurityConfig.class);

    private final CsrfPolicy csrfPolicy = new CsrfPolicy();

    private final SecurityService securityService;

    final SettingsService settingsService;

    final MultipleCredsMatchingAuthenticationProvider multipleCredsProvider;

    public GlobalSecurityConfig(SecurityService securityService,
            SettingsService settingsService, MultipleCredsMatchingAuthenticationProvider multipleCredsProvider) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.multipleCredsProvider = multipleCredsProvider;
    }

    public void loginFailureListener(AbstractAuthenticationFailureEvent event) {
        if (event.getSource() instanceof AbstractAuthenticationToken) {
            AbstractAuthenticationToken token = (AbstractAuthenticationToken) event.getSource();
            Object details = token.getDetails();
            if (details instanceof WebAuthenticationDetails) {
                LOG.info("Login failed from [{}]", ((WebAuthenticationDetails) details).getRemoteAddress());
            }
        }
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        List<AuthenticationProvider> providers = new ArrayList<>();
        if (settingsService.isLdapEnabled()) {
            LdapDirectoryAuthenticator directory = new JndiLdapDirectoryAuthenticator(settingsService);
            LdapAuthenticator ldap = new LdapAuthenticator(securityService, settingsService, directory);
            providers.add(new SpringLdapAuthenticationProvider(ldap));
        }
        providers.add(multipleCredsProvider);
        return new ProviderManager(providers);
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain unmappedExternalCorsFallback(HttpSecurity http) throws Exception {
        http
                .securityMatcher(request -> {
                    String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
                    return SecurityRoutePolicy.matchesPathPattern("/ext/**", path)
                            && !KtorExternalRoutePolicy.matches(path);
                })
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        RESTRequestParameterProcessingFilter restAuthenticationFilter = new RESTRequestParameterProcessingFilter();
        restAuthenticationFilter.setAuthenticationManager(authenticationManager);

        http
                // Registered external URLs are secured at the Ktor servlet boundary.
                .securityMatcher(request -> {
                    String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
                    return !KtorExternalRoutePolicy.matches(path);
                })
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(Customizer.withDefaults())
                .addFilterAfter(restAuthenticationFilter, BasicAuthenticationFilter.class)
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> csrfPolicy.requiresProtection(
                        request.getMethod(), request.getServletPath(), request.getPathInfo(), request.getQueryString())))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(authorize -> {
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers(SecurityRoutePolicy.WEB_PUBLIC_PATHS.toArray(String[]::new)).permitAll();
                    for (SecurityRoutePolicy.RoleRule rule : SecurityRoutePolicy.WEB_ROLE_RULES) {
                        authorize.requestMatchers(rule.getPaths().toArray(String[]::new)).hasRole(rule.getRole());
                    }
                    authorize.requestMatchers("/**").hasRole("USER");
                    authorize.anyRequest().authenticated();
                })
                .formLogin(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .logout(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .rememberMe(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsPolicy policy = CorsPolicy.defaults();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(policy.allowedOrigins());
        configuration.setAllowedMethods(policy.allowedMethods());
        return request -> {
            String contextPath = request.getContextPath();
            String requestUri = request.getRequestURI();
            String applicationPath = policy.applicationPath(requestUri, contextPath);
            return policy.appliesTo(applicationPath) ? configuration : null;
        };
    }
}
