package org.airsonic.player.ajax;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class ArtistInfoTest {

    @Test
    public void kotlinDtoRetainsJavaBeanAccessorsAndNullability() {
        ArtistInfo artistInfo = new ArtistInfo(null, null, null);

        assertNull(artistInfo.getSimilarArtists());
        assertNull(artistInfo.getArtistBio());
        assertNull(artistInfo.getTopSongs());
    }
}
