package org.airsonic.player.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Transitional adapter for the old Spring Security authentication graph.
 * Keep this class at the framework boundary; crypto implementations must use
 * {@link org.airsonic.player.security.crypto.PasswordEncoder} directly.
 */
public final class SpringPasswordEncoderAdapter implements PasswordEncoder, PasswordDecoder {
    private final org.airsonic.player.security.crypto.PasswordEncoder delegate;
    private final PasswordDecoder decoder;

    public SpringPasswordEncoderAdapter(org.airsonic.player.security.crypto.PasswordEncoder delegate) {
        this(delegate, delegate instanceof PasswordDecoder ? (PasswordDecoder) delegate : null);
    }

    public SpringPasswordEncoderAdapter(
            org.airsonic.player.security.crypto.PasswordEncoder delegate,
            PasswordDecoder decoder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.decoder = decoder;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    @Override
    public String decode(String encoded) throws Exception {
        if (decoder == null) {
            throw new UnsupportedOperationException("This password encoder is not reversible");
        }
        return decoder.decode(encoded);
    }
}
