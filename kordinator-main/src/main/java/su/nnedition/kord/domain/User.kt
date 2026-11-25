package su.nnedition.kord.domain

import su.nnedition.kord.util.Util
import java.util.Collections

/**
 * Represent a user.
 */
open class User(
    val username: String,
    var email: String?,
    var isLdapAuthenticated: Boolean,
    var bytesStreamed: Long,
    var bytesDownloaded: Long,
    var bytesUploaded: Long,
    open var roles: Set<Role>
) {
    constructor(username: String, email: String?) : this(
        username, email, false, 0, 0, 0, Collections.emptySet()
    )

    val isAdminRole: Boolean get() = roles.contains(Role.ADMIN)
    val isSettingsRole: Boolean get() = roles.contains(Role.SETTINGS)
    val isCommentRole: Boolean get() = roles.contains(Role.COMMENT)
    val isDownloadRole: Boolean get() = roles.contains(Role.DOWNLOAD)
    val isUploadRole: Boolean get() = roles.contains(Role.UPLOAD)
    val isPlaylistRole: Boolean get() = roles.contains(Role.PLAYLIST)
    val isCoverArtRole: Boolean get() = roles.contains(Role.COVERART)
    val isStreamRole: Boolean get() = roles.contains(Role.STREAM)
    val isJukeboxRole: Boolean get() = roles.contains(Role.JUKEBOX)
    val isShareRole: Boolean get() = roles.contains(Role.SHARE)

    override fun toString(): String = "$username ${Util.toJson(roles)}"

    enum class Role {
        ADMIN, SETTINGS, DOWNLOAD, UPLOAD, PLAYLIST, COVERART, COMMENT, STREAM, JUKEBOX, SHARE
    }

    companion object {
        const val USERNAME_ADMIN = "admin"
        const val USERNAME_GUEST = "guest"
        const val USERNAME_ANONYMOUS = "anonymous"
    }
}
