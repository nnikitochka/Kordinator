package org.airsonic.player.domain

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Objects

class CoverArt(
    var entityId: Int,
    var entityType: EntityType?,
    var path: String?,
    var folderId: Int?,
    var overridden: Boolean
) {
    var accentColor: String? = null
    var created: Instant? = Instant.now()
    var updated: Instant? = created

    constructor(
        entityId: Int,
        entityType: EntityType?,
        path: String?,
        folderId: Int?,
        overridden: Boolean,
        created: Instant?,
        updated: Instant?
    ) : this(entityId, entityType, path, folderId, overridden) {
        this.created = created
        this.updated = updated
    }

    val relativePath: Path
        get() = Paths.get(path!!)

    fun getFullPath(relativeMediaFolderPath: Path): Path = relativeMediaFolderPath.resolve(path!!)

    override fun hashCode(): Int = Objects.hash(entityId, entityType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as CoverArt
        return entityId == other.entityId && entityType == other.entityType
    }

    enum class EntityType {
        MEDIA_FILE, ALBUM, ARTIST, NONE
    }

    companion object {
        @JvmField
        val NULL_ART = CoverArt(-2, EntityType.NONE, null, null, false)
    }
}
