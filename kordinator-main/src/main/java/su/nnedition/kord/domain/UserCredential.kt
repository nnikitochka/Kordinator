package su.nnedition.kord.domain

import java.time.Instant
import java.util.Objects

open class UserCredential(
    open var username: String?,
    open var appUsername: String?,
    open var credential: String?,
    open var encoder: String?,
    open var app: App?,
    open var comment: String?,
    open var expiration: Instant?,
    open var created: Instant?,
    open var updated: Instant?
) {
    constructor(
        username: String?, appUsername: String?, credential: String?, encoder: String?, app: App?,
        comment: String?, expiration: Instant?
    ) : this(username, appUsername, credential, encoder, app, comment, expiration, null, null) {
        val now = Instant.now()
        created = now
        updated = now
    }

    constructor(
        username: String?, appUsername: String?, credential: String?, encoder: String?, app: App?, comment: String?
    ) : this(username, appUsername, credential, encoder, app, comment, null)

    constructor(username: String?, appUsername: String?, credential: String?, encoder: String?, app: App?) :
        this(username, appUsername, credential, encoder, app, null)

    constructor(other: UserCredential) : this(
        other.username,
        other.appUsername,
        other.credential,
        other.encoder,
        other.app,
        other.comment,
        other.expiration,
        other.created,
        other.updated
    )

    override fun hashCode(): Int = Objects.hash(
        comment, created, credential, expiration, app, appUsername, encoder, updated, username
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as UserCredential
        return comment == other.comment && created == other.created && credential == other.credential &&
            expiration == other.expiration && app == other.app && appUsername == other.appUsername &&
            encoder == other.encoder && updated == other.updated && username == other.username
    }

    enum class App(private val displayName: String, val usernameRequired: Boolean, val nonDecodableEncodersAllowed: Boolean) {
        KORDINATOR("Kordinator", false, true),
        LASTFM("Last.fm", true, false),
        LISTENBRAINZ("Listenbrainz", false, false);

        fun getName(): String = displayName

    }
}
