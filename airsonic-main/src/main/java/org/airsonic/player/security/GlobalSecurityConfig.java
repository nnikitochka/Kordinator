package org.airsonic.player.security;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import org.airsonic.player.service.JWTSecurityService;
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
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.DispatcherType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider.SALT_TOKEN_MECHANISM_SPECIALIZATION;

@Configuration
@EnableWebSecurity
public class GlobalSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalSecurityConfig.class);

    static final String FAILURE_URL = "/login?error";

    @SuppressWarnings("deprecation")
    public static final Map<String, PasswordEncoder> ENCODERS = new HashMap<>(ImmutableMap
            .<String, PasswordEncoder>builderWithExpectedSize(19)
            .put("bcrypt", new BCryptPasswordEncoder())
            .put("ldap", new org.springframework.security.crypto.password.LdapShaPasswordEncoder())
            .put("MD4", new org.springframework.security.crypto.password.Md4PasswordEncoder())
            .put("MD5", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("MD5"))
            .put("pbkdf2", new Pbkdf2PasswordEncoder("", 8, 185000, 256)) // legacy Security 5 no-arg defaults (existing hashes must keep matching)
            .put("scrypt", new SCryptPasswordEncoder(16384, 8, 1, 32, 64)) // legacy Security 5 no-arg defaults
            .put("SHA-1", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-1"))
            .put("SHA-256", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256"))
            .put("sha256", new org.springframework.security.crypto.password.StandardPasswordEncoder())
            .put("argon2", new Argon2PasswordEncoder(16, 32, 1, 4096, 1)) // legacy Security 5 no-arg defaults

            // base decodable encoders
             .put("noop", spring(new PasswordEncoderDecoderWrapper(noopEncoder(), p -> p)))
             .put("hex", spring(HexPasswordEncoder.getInstance()))
             .put("encrypted-AES-GCM", spring(new AesGcmPasswordEncoder())) // placeholder (real instance created below)

            // base decodable encoders that rely on salt+token being passed in (not stored in db with this type)
             .put("noop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(p -> p)))
             .put("hex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(HexPasswordEncoder.getInstance())))
             .put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(new AesGcmPasswordEncoder()))) // placeholder (real instance created below)

            // TODO: legacy marked base encoders, to be upgraded to one-way formats at breaking version change
             .put("legacynoop", spring(new PasswordEncoderDecoderWrapper(noopEncoder(), p -> p)))
             .put("legacyhex", spring(HexPasswordEncoder.getInstance()))

             .put("legacynoop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(p -> p)))
             .put("legacyhex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(HexPasswordEncoder.getInstance())))
             .build());

    private static PasswordEncoder spring(org.airsonic.player.security.crypto.PasswordEncoder encoder) {
        return new SpringPasswordEncoderAdapter(encoder);
    }

    private static org.airsonic.player.security.crypto.PasswordEncoder noopEncoder() {
        return new org.airsonic.player.security.crypto.PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return StringUtils.equals(rawPassword, encodedPassword);
            }
        };
    }

    public static final Set<String> OPENTEXT_ENCODERS = ImmutableSet.of("noop", "hex", "legacynoop", "legacyhex");
    public static final Set<String> DECODABLE_ENCODERS = ImmutableSet.<String>builder().addAll(OPENTEXT_ENCODERS).add("encrypted-AES-GCM").build();
    public static final Set<String> NONLEGACY_ENCODERS = ENCODERS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, "legacy", SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = Sets.intersection(DECODABLE_ENCODERS, NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = Sets.difference(NONLEGACY_ENCODERS, DECODABLE_ENCODERS);

    private final CsrfPolicy csrfPolicy = new CsrfPolicy();

    private final SecurityService securityService;

    private final SpringUserDetailsServiceAdapter userDetailsService;

    final SettingsService settingsService;

    final MultipleCredsMatchingAuthenticationProvider multipleCredsProvider;

    public GlobalSecurityConfig(SecurityService securityService,
            SpringUserDetailsServiceAdapter userDetailsService, SettingsService settingsService,
            MultipleCredsMatchingAuthenticationProvider multipleCredsProvider) {
        this.securityService = securityService;
        this.userDetailsService = userDetailsService;
        this.settingsService = settingsService;
        this.multipleCredsProvider = multipleCredsProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        boolean generatedKeys = false;

        String encryptionKeyPass = settingsService.getEncryptionPassword();
        if (StringUtils.isBlank(encryptionKeyPass)) {
            LOG.warn("Generating new encryption key password");
            encryptionKeyPass = JWTSecurityService.generateKey();
            settingsService.setEncryptionPassword(encryptionKeyPass);
            generatedKeys = true;
        }

        String encryptionKeySalt = settingsService.getEncryptionSalt();
        if (StringUtils.isBlank(encryptionKeySalt)) {
            LOG.warn("Generating new encryption key salt");
            encryptionKeySalt = BaseEncoding.base16().encode(KeyGenerators.secureRandom(16).generateKey());
            settingsService.setEncryptionSalt(encryptionKeySalt);
            generatedKeys = true;
        }

        if (generatedKeys) {
            settingsService.save();
        }

        AesGcmPasswordEncoder encoder = new AesGcmPasswordEncoder(encryptionKeyPass, encryptionKeySalt);
        ENCODERS.put("encrypted-AES-GCM", spring(encoder));
        ENCODERS.put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(encoder)));

        DelegatingPasswordEncoder pEncoder = new DelegatingPasswordEncoder(settingsService.getNonDecodablePasswordEncoder(), ENCODERS) {
            @Override
            public boolean upgradeEncoding(String prefixEncodedPassword) {
                PasswordEncoder encoder = ENCODERS.get(StringUtils.substringBetween(prefixEncodedPassword, "{", "}"));
                if (encoder != null) {
                    return encoder.upgradeEncoding(StringUtils.substringAfter(prefixEncodedPassword, "}"));
                }

                return false;
            }
        };

        pEncoder.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }

            @Override
            public String encode(CharSequence rawPassword) {
                return null;
            }
        });

        return pEncoder;
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
        RequestMatcher ktorExternalRoutes = new org.airsonic.player.spring.KtorExternalRouteRequestMatcher();
        http
                .securityMatcher(request -> {
                    String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
                    return SecurityRoutePolicy.matchesPathPattern("/ext/**", path)
                            && !ktorExternalRoutes.matches(request);
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

        // Try to load the 'remember me' key.
        //
        // Note that using a fixed key compromises security as perfect
        // forward secrecy is not guaranteed anymore.
        //
        // An external entity can then re-use our authentication cookies before
        // the expiration time, or even, given enough time, recover the password
        // from the MD5 hash.
        //
        // A null key means an ephemeral key is autogenerated
        String rememberMeKey = settingsService.getRememberMeKey();
        if (rememberMeKey != null) {
            LOG.info("Using a fixed 'remember me' key from properties, this is insecure.");
        }

        http
                // Registered external URLs are secured at the Ktor servlet boundary.
                .securityMatcher(request -> {
                    return !new org.airsonic.player.spring.KtorExternalRouteRequestMatcher().matches(request);
                })
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(Customizer.withDefaults())
                .addFilterAfter(restAuthenticationFilter, BasicAuthenticationFilter.class)
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> csrfPolicy.requiresProtection(
                        request.getMethod(), request.getServletPath(), request.getPathInfo(), request.getQueryString())))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(authorize -> {
                    // Views are forwarded internally to /WEB-INF/jsp/*.jsp after the
                    // public controller endpoint has already been authorized.
                    authorize.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers(SecurityRoutePolicy.WEB_PUBLIC_PATHS.toArray(String[]::new)).permitAll();
                    for (SecurityRoutePolicy.RoleRule rule : SecurityRoutePolicy.WEB_ROLE_RULES) {
                        authorize.requestMatchers(rule.getPaths().toArray(String[]::new)).hasRole(rule.getRole());
                    }
                    authorize.requestMatchers("/**").hasRole("USER");
                    authorize.anyRequest().authenticated();
                })
                .formLogin(login -> login
                        .loginPage("/login")
                        .permitAll()
                        .defaultSuccessUrl("/index", true)
                        .failureUrl(FAILURE_URL)
                        .usernameParameter("j_username")
                        .passwordParameter("j_password"))
                .logout(logout -> logout
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .logoutSuccessUrl("/login?logout"))
                .rememberMe(rememberMe -> rememberMe
                        .key(rememberMeKey)
                        .userDetailsService(userDetailsService));
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
