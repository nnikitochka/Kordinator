package org.airsonic.player.security;

import org.airsonic.player.domain.UserCredential;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** Spring Security boundary representation of a neutral {@link UserIdentity}. */
public final class SpringUserDetailsAdapter extends org.springframework.security.core.userdetails.User {
    private final String identityUsername;
    private List<UserCredential> credentials;

    public SpringUserDetailsAdapter(UserIdentity identity, Collection<? extends GrantedAuthority> authorities) {
        super(identity.getUsername(), passwordDigest(identity.getCredentials()), true, true, true, true, authorities);
        this.identityUsername = identity.getUsername();
        this.credentials = new ArrayList<>(identity.getCredentials());
    }

    public List<UserCredential> getCredentials() {
        return credentials;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = List.of();
    }

    private static String passwordDigest(List<UserCredential> credentials) {
        return org.apache.commons.codec.digest.DigestUtils.md5Hex(credentials.stream()
                .map(x -> x.getEncoder() + "/" + x.getCredential() + "/" + x.getExpiration())
                .collect(java.util.stream.Collectors.joining()));
    }
}
