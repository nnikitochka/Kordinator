package org.airsonic.player.service;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.springframework.stereotype.Service;

/**
 * @author Rémi Cocula
 */
@Service
public class JukeboxService {
    private final JukeboxLegacySubsonicService jukeboxLegacySubsonicService;

    public JukeboxService(JukeboxLegacySubsonicService jukeboxLegacySubsonicService) {
        this.jukeboxLegacySubsonicService = jukeboxLegacySubsonicService;
    }

    public void setGain(Player airsonicPlayer, float gain) {
        if (airsonicPlayer.getTechnology() != PlayerTechnology.JUKEBOX) return;

        jukeboxLegacySubsonicService.setGain(gain);
    }

    public void setPosition(Player airsonicPlayer, int positionInSeconds) {
        if (airsonicPlayer.getTechnology() != PlayerTechnology.JUKEBOX) return;

        throw new UnsupportedOperationException();
    }

    public float getGain(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                return jukeboxLegacySubsonicService.getGain();
        }
        return 0;
    }

    /**
     * This method should be removed when the jukebox is controlled only through rest api.
     */
    @Deprecated
    public void updateJukebox(Player airsonicPlayer, int offset) {
        if (airsonicPlayer.getTechnology().equals(PlayerTechnology.JUKEBOX)) {
            jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer,offset);
        }
    }

    public int getPosition(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                return jukeboxLegacySubsonicService.getPosition();
        }
        return 0;
    }

    /**
     * Plays the playQueue of a jukebox player starting at the first item of the queue.
     */
    public void play(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer,0);
                break;
        }
    }

    public void start(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer,0);
                break;
        }
    }

    public void stop(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer,0);
                break;
        }
    }

    public void skip(Player airsonicPlayer,int index,int offset) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                jukeboxLegacySubsonicService.updateJukebox(airsonicPlayer,offset);
                break;
        }
    }

    /**
     * This method is only here due to legacy considerations and should be removed
     * if the jukeboxLegacySubsonicService is removed.
     */
    @Deprecated
    public boolean canControl(Player airsonicPlayer) {
        switch (airsonicPlayer.getTechnology()) {
            case JUKEBOX:
                if (jukeboxLegacySubsonicService.getPlayer() == null) {
                    return false;
                } else {
                    return jukeboxLegacySubsonicService.getPlayer().getId().equals(airsonicPlayer.getId());
                }
        }
        return false;
    }
}
