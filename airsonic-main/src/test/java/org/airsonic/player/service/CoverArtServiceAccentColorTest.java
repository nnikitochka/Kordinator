package org.airsonic.player.service;

import org.airsonic.player.dao.CoverArtDao;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies that the dominant (accent) color is extracted from a cover art
 * image at upsert time and persisted to the database.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CoverArtServiceAccentColorTest {

    @ClassRule
    public static final HomeRule airsonicRule = new HomeRule();

    private static final int ENTITY_ID = 987654;

    @Autowired
    private CoverArtService coverArtService;

    @Autowired
    private CoverArtDao coverArtDao;

    private static String coverPath() throws Exception {
        return java.nio.file.Paths.get(CoverArtServiceAccentColorTest.class.getResource(
                "/MEDIAS/Music/_DIR_ Sixteen Horsepower/_DIR_ Sackcloth 'n' Ashes/cover.jpg").toURI()).toString();
    }

    @Test
    public void testAccentColorComputedAndPersisted() throws Exception {
        String coverPath = coverPath();

        CoverArt art = new CoverArt(ENTITY_ID, EntityType.MEDIA_FILE, coverPath, null, false);
        coverArtService.upsert(art);

        // The dominant color of the cover is known/deterministic for this image.
        assertEquals("#A58866", art.getAccentColor());

        // Color is readable back from the service and the database.
        assertEquals("#A58866", coverArtService.getAccentColor(EntityType.MEDIA_FILE, ENTITY_ID));
        CoverArt stored = coverArtDao.get(EntityType.MEDIA_FILE, ENTITY_ID);
        assertNotNull(stored);
        assertEquals("#A58866", stored.getAccentColor());
    }

    @Test
    public void testAccentColorReusedWhenImageUnchanged() throws Exception {
        String coverPath = coverPath();

        CoverArt art = new CoverArt(ENTITY_ID, EntityType.MEDIA_FILE, coverPath, null, false);
        coverArtService.upsert(art);
        assertEquals("#A58866", art.getAccentColor());

        // Second upsert of the same image must keep the stored color.
        CoverArt art2 = new CoverArt(ENTITY_ID, EntityType.MEDIA_FILE, coverPath, null, false);
        coverArtService.upsert(art2);
        assertEquals("#A58866", art2.getAccentColor());
    }
}