package su.nnedition.kord.domain

import org.apache.commons.lang3.StringUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import java.time.Instant

/** Represents the version number of Kordinator. */
class Version : Comparable<Version> {
    private val internalVersion: DefaultArtifactVersion
    var commit: String? = null
    var preview: Boolean? = null
    var url: String? = null
    var publishDate: Instant? = null
    var releaseCreateDate: Instant? = null
    var artifacts: List<Map<String, Any>>? = null

    /** Creates a new version instance by parsing a version such as "1.27", "1.27.2" or "1.27.beta3". */
    constructor(version: String) {
        internalVersion = DefaultArtifactVersion(version)
    }

    constructor(
        version: String,
        commit: String?,
        preview: Boolean?,
        url: String?,
        publishDate: Instant?,
        releaseCreateDate: Instant?,
        artifacts: List<Map<String, Any>>?
    ) : this(version) {
        this.commit = commit
        this.preview = preview
        this.url = url
        this.publishDate = publishDate
        this.releaseCreateDate = releaseCreateDate
        this.artifacts = artifacts
    }

    fun getMajor(): Int = internalVersion.majorVersion

    fun getMinor(): Int = internalVersion.minorVersion

    override fun equals(other: Any?): Boolean =
        other is Version && internalVersion == other.internalVersion

    override fun hashCode(): Int = internalVersion.hashCode()

    override fun toString(): String = internalVersion.toString()

    override fun compareTo(other: Version): Int = internalVersion.compareTo(other.internalVersion)

    fun isPreview(): Boolean = StringUtils.isNotBlank(internalVersion.qualifier) &&
        !StringUtils.equalsIgnoreCase(internalVersion.qualifier, Artifact.RELEASE_VERSION)
}
