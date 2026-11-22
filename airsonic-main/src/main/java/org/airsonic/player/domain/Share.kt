package org.airsonic.player.domain

import java.time.Instant

/**
 * A collection of media files that is shared with someone, and accessible via a direct URL.
 */
class Share {
    var id: Int = 0
    var name: String? = null
    var description: String? = null
    var username: String? = null
    var created: Instant? = null
    var expires: Instant? = null
    var lastVisited: Instant? = null
    var visitCount: Int = 0

    constructor()

    constructor(
        id: Int,
        name: String?,
        description: String?,
        username: String?,
        created: Instant?,
        expires: Instant?,
        lastVisited: Instant?,
        visitCount: Int
    ) : this() {
        this.id = id
        this.name = name
        this.description = description
        this.username = username
        this.created = created
        this.expires = expires
        this.lastVisited = lastVisited
        this.visitCount = visitCount
    }
}
