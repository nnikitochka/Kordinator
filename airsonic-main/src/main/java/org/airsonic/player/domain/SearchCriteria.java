package org.airsonic.player.domain;

import com.google.common.base.MoreObjects;
import org.airsonic.player.service.SearchService;

/**
 * Defines criteria used when searching.
 *
 * @see SearchService#search
 */
public class SearchCriteria {
    private String query;
    private int offset;
    private int count;

    public void setQuery(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("query", query)
                .add("offset", offset)
                .add("count", count)
                .toString();
    }
}