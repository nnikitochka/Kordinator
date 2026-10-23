package org.airsonic.player.domain;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the version number of Airsonic.
 */
public class Version implements Comparable<Version> {
    private final DefaultArtifactVersion internalVersion;
    private String commit;
    private Boolean preview;
    private String url;
    private Instant publishDate;
    private Instant releaseCreateDate;
    private List<Map<String,Object>> artifacts;

    /**
     * Creates a new version instance by parsing the given string.
     * @param version A string of the format "1.27", "1.27.2" or "1.27.beta3".
     */
    public Version(String version) {
        this.internalVersion = new DefaultArtifactVersion(version);
    }

    public Version(String version, String commit, Boolean preview, String url,
            Instant publishDate, Instant releaseCreateDate, List<Map<String, Object>> artifacts) {
        this(version);
        this.commit = commit;
        this.preview = preview;
        this.url = url;
        this.publishDate = publishDate;
        this.releaseCreateDate = releaseCreateDate;
        this.artifacts = artifacts;
    }

    public int getMajor() {
        return internalVersion.getMajorVersion();
    }

    public int getMinor() {
        return internalVersion.getMinorVersion();
    }

    /**
     * Return whether this object is equal to another.
     * @param o Object to compare to.
     * @return Whether this object is equals to another.
     */
    public boolean equals(Object o) {
        if (o instanceof Version) {
            return internalVersion.equals(((Version)o).internalVersion);
        } else {
            return false;
        }
    }

    /**
     * Returns a hash code for this object.
     * @return A hash code for this object.
     */
    public int hashCode() {
        return internalVersion.hashCode();
    }

    /**
     * Returns a string representation of the form "1.27", "1.27.2" or "1.27.beta3".
     * @return A string representation of the form "1.27", "1.27.2" or "1.27.beta3".
     */
    public String toString() {
        return internalVersion.toString();
    }

    public String getCommit() {
        return commit;
    }

    public Boolean getPreview() {
        return preview;
    }

    public String getUrl() {
        return url;
    }

    public Instant getPublishDate() {
        return publishDate;
    }

    public Instant getReleaseCreateDate() {
        return releaseCreateDate;
    }

    public List<Map<String, Object>> getArtifacts() {
        return artifacts;
    }

    /**
     * Compares this object with the specified object for order.
     * @param version The object to compare to.
     * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or
     * greater than the specified object.
     */
    public int compareTo(Version version) {
        return internalVersion.compareTo(version.internalVersion);
    }

    public boolean isPreview() {
        return StringUtils.isNotBlank(internalVersion.getQualifier()) &&
                !StringUtils.equalsIgnoreCase(internalVersion.getQualifier(), Artifact.RELEASE_VERSION);
    }
}
