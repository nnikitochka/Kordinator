package org.airsonic.player.domain

import org.airsonic.player.dao.AbstractDao.Column
import java.time.Instant

open class Playlist {
    var id: Int = 0
    var username: String? = null
    @field:Column("is_public")
    var shared: Boolean = false
    var name: String? = null
    var comment: String? = null
    var fileCount: Int = 0
    var duration: Double = 0.0
    var created: Instant? = null
    var changed: Instant? = null
    var importedFrom: String? = null

    constructor()

    constructor(
        id: Int,
        username: String?,
        shared: Boolean,
        name: String?,
        comment: String?,
        fileCount: Int,
        duration: Double,
        created: Instant?,
        changed: Instant?,
        importedFrom: String?
    ) : this() {
        this.id = id
        this.username = username
        this.shared = shared
        this.name = name
        this.comment = comment
        this.fileCount = fileCount
        this.duration = duration
        this.created = created
        this.changed = changed
        this.importedFrom = importedFrom
    }

    constructor(playlist: Playlist) : this(
        playlist.id,
        playlist.username,
        playlist.shared,
        playlist.name,
        playlist.comment,
        playlist.fileCount,
        playlist.duration,
        playlist.created,
        playlist.changed,
        playlist.importedFrom
    )
}
