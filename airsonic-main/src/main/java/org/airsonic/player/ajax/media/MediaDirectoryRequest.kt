package org.airsonic.player.ajax.media;

import java.util.List;
import java.util.Map;

public class MediaDirectoryRequest {
    List<Integer> ids;
    Map<Integer, List<String>> paths;

    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(List<Integer> ids) {
        this.ids = ids;
    }

    public Map<Integer, List<String>> getPaths() {
        return paths;
    }

    public void setPaths(Map<Integer, List<String>> paths) {
        this.paths = paths;
    }
}
