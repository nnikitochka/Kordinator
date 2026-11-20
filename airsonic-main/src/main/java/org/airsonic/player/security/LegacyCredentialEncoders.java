package org.airsonic.player.security;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider.SALT_TOKEN_MECHANISM_SPECIALIZATION;

/** Legacy credential codecs and their adapter for Spring's old authentication boundary. */
public final class LegacyCredentialEncoders {
    public static final Map<String, org.airsonic.player.security.crypto.PasswordEncoder> CODECS =
            LegacyPasswordEncoders.CODECS;
    public static final Map<String, PasswordEncoder> ENCODERS = springAdapters();

    public static final Set<String> OPENTEXT_ENCODERS = ImmutableSet.of("noop", "hex", "legacynoop", "legacyhex");
    public static final Set<String> DECODABLE_ENCODERS = ImmutableSet.<String>builder()
            .addAll(OPENTEXT_ENCODERS).add("encrypted-AES-GCM").build();
    public static final Set<String> NONLEGACY_ENCODERS = CODECS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, "legacy", SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = Sets.intersection(DECODABLE_ENCODERS, NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = Sets.difference(NONLEGACY_ENCODERS, DECODABLE_ENCODERS);

    private LegacyCredentialEncoders() {
    }

    public static void configureEncryption(SettingsService settingsService) {
        LegacyPasswordEncoders.configureEncryption(settingsService);
        ENCODERS.put("encrypted-AES-GCM", new SpringPasswordEncoderAdapter(CODECS.get("encrypted-AES-GCM")));
        ENCODERS.put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION,
                new SpringPasswordEncoderAdapter(CODECS.get("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION)));
    }

    public static DelegatingPasswordEncoder delegatingEncoder(SettingsService settingsService) {
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(
                settingsService.getNonDecodablePasswordEncoder(), ENCODERS) {
            @Override
            public boolean upgradeEncoding(String encodedPassword) {
                PasswordEncoder delegate = ENCODERS.get(StringUtils.substringBetween(encodedPassword, "{", "}"));
                return delegate != null && delegate.upgradeEncoding(StringUtils.substringAfter(encodedPassword, "}"));
            }
        };
        encoder.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }

            @Override
            public String encode(CharSequence rawPassword) {
                return null;
            }
        });
        return encoder;
    }

    private static Map<String, PasswordEncoder> springAdapters() {
        Map<String, PasswordEncoder> adapters = new HashMap<>();
        CODECS.forEach((name, codec) -> adapters.put(name, new SpringPasswordEncoderAdapter(codec)));
        return adapters;
    }
}
