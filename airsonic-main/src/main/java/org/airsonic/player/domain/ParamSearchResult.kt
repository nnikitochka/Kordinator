package org.airsonic.player.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of a search.
 */
public class ParamSearchResult<T> {
    private final List<T> items = new ArrayList<>();

    private int offset;
    private int totalHits;

    public List<T> getItems() {
        return items;
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
