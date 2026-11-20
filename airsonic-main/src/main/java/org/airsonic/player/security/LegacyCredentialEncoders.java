package org.airsonic.player.security;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider.SALT_TOKEN_MECHANISM_SPECIALIZATION;

/**
 * Legacy credential codecs shared by the old Spring authentication boundary and Ktor runtime.
 * This class intentionally has no dependency on Spring's application context or configuration.
 */
public final class LegacyCredentialEncoders {
    public static final Map<String, PasswordEncoder> ENCODERS = new HashMap<>(ImmutableMap
            .<String, PasswordEncoder>builderWithExpectedSize(19)
            .put("bcrypt", new BCryptPasswordEncoder())
            .put("ldap", new org.springframework.security.crypto.password.LdapShaPasswordEncoder())
            .put("MD4", new org.springframework.security.crypto.password.Md4PasswordEncoder())
            .put("MD5", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("MD5"))
            .put("pbkdf2", new Pbkdf2PasswordEncoder("", 8, 185000, 256))
            .put("scrypt", new SCryptPasswordEncoder(16384, 8, 1, 32, 64))
            .put("SHA-1", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-1"))
            .put("SHA-256", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256"))
            .put("sha256", new org.springframework.security.crypto.password.StandardPasswordEncoder())
            .put("argon2", new Argon2PasswordEncoder(16, 32, 1, 4096, 1))
            .put("noop", spring(new PasswordEncoderDecoderWrapper(noopEncoder(), p -> p)))
            .put("hex", spring(HexPasswordEncoder.getInstance()))
            .put("encrypted-AES-GCM", spring(new AesGcmPasswordEncoder()))
            .put("noop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(p -> p)))
            .put("hex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(HexPasswordEncoder.getInstance())))
            .put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(new AesGcmPasswordEncoder())))
            .put("legacynoop", spring(new PasswordEncoderDecoderWrapper(noopEncoder(), p -> p)))
            .put("legacyhex", spring(HexPasswordEncoder.getInstance()))
            .put("legacynoop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(p -> p)))
            .put("legacyhex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, spring(new SaltedTokenPasswordEncoder(HexPasswordEncoder.getInstance())))
            .build());

    public static final Set<String> OPENTEXT_ENCODERS = ImmutableSet.of("noop", "hex", "legacynoop", "legacyhex");
    public static final Set<String> DECODABLE_ENCODERS = ImmutableSet.<String>builder()
            .addAll(OPENTEXT_ENCODERS).add("encrypted-AES-GCM").build();
    public static final Set<String> NONLEGACY_ENCODERS = ENCODERS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, "legacy", SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = Sets.intersection(DECODABLE_ENCODERS, NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = Sets.difference(NONLEGACY_ENCODERS, DECODABLE_ENCODERS);

    private LegacyCredentialEncoders() {
    }

    public static void configureEncryption(SettingsService settingsService) {
        boolean generatedKeys = false;
        String encryptionKeyPass = settingsService.getEncryptionPassword();
        if (StringUtils.isBlank(encryptionKeyPass)) {
            encryptionKeyPass = JWTSecurityService.generateKey();
            settingsService.setEncryptionPassword(encryptionKeyPass);
            generatedKeys = true;
        }
        String encryptionKeySalt = settingsService.getEncryptionSalt();
        if (StringUtils.isBlank(encryptionKeySalt)) {
            encryptionKeySalt = BaseEncoding.base16().encode(KeyGenerators.secureRandom(16).generateKey());
            settingsService.setEncryptionSalt(encryptionKeySalt);
            generatedKeys = true;
        }
        if (generatedKeys) {
            settingsService.save();
        }
        AesGcmPasswordEncoder aes = new AesGcmPasswordEncoder(encryptionKeyPass, encryptionKeySalt);
        ENCODERS.put("encrypted-AES-GCM", spring(aes));
        ENCODERS.put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION,
                spring(new SaltedTokenPasswordEncoder(aes)));
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
}
