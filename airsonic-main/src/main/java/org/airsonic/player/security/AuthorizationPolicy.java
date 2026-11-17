package org.airsonic.player.security;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;

/** Pure authorization decisions over an immutable request caller context. */
public final class AuthorizationPolicy {
    public boolean isAuthenticated(CallerContext caller) {
        return caller != null && caller.isAuthenticated();
    }

    public boolean hasRole(CallerContext caller, User.Role role) {
        return isAuthenticated(caller) && caller.hasRole(role);
    }

    /** Preserves the legacy special case where the reserved admin username is administrative. */
    public boolean isAdmin(CallerContext caller) {
        return isAuthenticated(caller)
                && (User.USERNAME_ADMIN.equals(caller.getUsername()) || caller.hasRole(User.Role.ADMIN));
    }

    public boolean hasFolderAccess(CallerContext caller, int folderId) {
        return isAuthenticated(caller) && caller.hasFolderAccess(folderId);
    }

    public boolean hasFolderAccess(CallerContext caller, MediaFile file) {
        return file != null && caller != null && hasFolderAccess(caller, file.getFolderId());
    }
}
