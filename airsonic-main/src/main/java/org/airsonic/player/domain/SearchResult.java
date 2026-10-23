package org.airsonic.player.domain;

import org.airsonic.player.service.SearchService;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of a search.
 *
 * @see SearchService#search
 */
public class SearchResult {

    private final List<MediaFile> mediaFiles = new ArrayList<>();
    private final List<Artist> artists = new ArrayList<>();
    private final List<Album> albums = new ArrayList<>();

    private int offset;
    private int totalHits;

    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }

    public List<Artist> getArtists() {
        return artists;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }
}
