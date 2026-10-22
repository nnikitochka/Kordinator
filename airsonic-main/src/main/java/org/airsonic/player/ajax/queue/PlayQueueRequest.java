package org.airsonic.player.ajax.queue;

import java.util.List;

public class PlayQueueRequest {
    private int id;
    private Integer index;
    private long offset;
    private int count;

    // used for playShuffle()
    private String albumListType;
    private String genre;
    private String decade;

    // used for add()
    private List<Integer> ids;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getAlbumListType() {
        return albumListType;
    }

    public void setAlbumListType(String albumListType) {
        this.albumListType = albumListType;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getDecade() {
        return decade;
    }

    public void setDecade(String decade) {
        this.decade = decade;
    }

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }
}
