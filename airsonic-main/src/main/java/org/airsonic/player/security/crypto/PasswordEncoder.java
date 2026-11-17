package org.airsonic.player.security.crypto;

/**
 * Framework-neutral password encoding contract.
 *
 * <p>The contract deliberately mirrors the small part of the former Spring
 * contract used by Airsonic. Implementations must not expose framework types
 * so that credential verification can be reused by Ktor and non-Spring
 * runtimes.</p>
 */
public interface PasswordEncoder {
    String encode(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String encodedPassword);

    default boolean upgradeEncoding(String encodedPassword) {
        return false;
    }
}
