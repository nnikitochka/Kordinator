package org.airsonic.player.ajax.playlists;

import java.util.List;

public class PlaylistFilesModificationRequest {
    private int id;
    private List<Integer> modifierIds;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<Integer> getModifierIds() {
        return modifierIds;
    }

    public void setModifierIds(List<Integer> modifierIds) {
        this.modifierIds = modifierIds;
    }
}
