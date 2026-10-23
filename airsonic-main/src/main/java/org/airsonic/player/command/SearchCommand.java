package org.airsonic.player.command;

import org.airsonic.player.controller.SearchController;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command used in {@link SearchController}.
 *
 * @author Sindre Mehus
 */
public class SearchCommand {

    private String query;
    private Map<String, Set<Integer>> artists;
    private Map<Pair<String, String>, Set<Integer>> albums;
    private List<MediaFile> songs;
    private boolean isIndexBeingCreated;
    private User user;
    private boolean partyModeEnabled;
    private Player player;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isIndexBeingCreated() {
        return isIndexBeingCreated;
    }

    public void setIndexBeingCreated(boolean indexBeingCreated) {
        isIndexBeingCreated = indexBeingCreated;
    }

    public Map<String, Set<Integer>> getArtists() {
        return artists;
    }

    public void setArtists(Map<String, Set<Integer>> artists) {
        this.artists = artists;
    }

    public Map<Pair<String, String>, Set<Integer>> getAlbums() {
        return albums;
    }

    public void setAlbums(Map<Pair<String, String>, Set<Integer>> albums) {
        this.albums = albums;
    }

    public List<MediaFile> getSongs() {
        return songs;
    }

    public void setSongs(List<MediaFile> songs) {
        this.songs = songs;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isPartyModeEnabled() {
        return partyModeEnabled;
    }

    public void setPartyModeEnabled(boolean partyModeEnabled) {
        this.partyModeEnabled = partyModeEnabled;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public static class Match {
        private MediaFile mediaFile;
        private String title;
        private String album;
        private String artist;

        public Match(MediaFile mediaFile, String title, String album, String artist) {
            this.mediaFile = mediaFile;
            this.title = title;
            this.album = album;
            this.artist = artist;
        }

        public MediaFile getMediaFile() {
            return mediaFile;
        }

        public String getTitle() {
            return title;
        }

        public String getAlbum() {
            return album;
        }

        public String getArtist() {
            return artist;
        }
    }
}
