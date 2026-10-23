package org.airsonic.player.domain;

import java.time.Instant;

/**
 * A collection of media files that is shared with someone, and accessible via a direct URL.
 */
public class Share {
    private int id;
    private String name;
    private String description;
    private String username;
    private Instant created;
    private Instant expires;
    private Instant lastVisited;
    private int visitCount;

    public Share() {
    }

    public Share(int id, String name, String description, String username, Instant created,
            Instant expires, Instant lastVisited, int visitCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.username = username;
        this.created = created;
        this.expires = expires;
        this.lastVisited = lastVisited;
        this.visitCount = visitCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getExpires() {
        return expires;
    }

    public void setExpires(Instant expires) {
        this.expires = expires;
    }

    public Instant getLastVisited() {
        return lastVisited;
    }

    public void setLastVisited(Instant lastVisited) {
        this.lastVisited = lastVisited;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }
}
