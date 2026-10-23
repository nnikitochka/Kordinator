package org.airsonic.player.domain;

import java.util.List;
import java.util.SortedMap;

public class MusicFolderContent {
    private final SortedMap<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> indexedArtists;
    private final List<MediaFile> singleSongs;

    public MusicFolderContent(SortedMap<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> indexedArtists, List<MediaFile> singleSongs) {
        this.indexedArtists = indexedArtists;
        this.singleSongs = singleSongs;
    }

    public SortedMap<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> getIndexedArtists() {
        return indexedArtists;
    }

    public List<MediaFile> getSingleSongs() {
        return singleSongs;
    }
}
