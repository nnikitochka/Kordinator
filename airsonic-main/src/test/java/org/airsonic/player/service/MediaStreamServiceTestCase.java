package org.airsonic.player.service;

import junit.framework.TestCase;

import java.awt.Dimension;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class MediaStreamServiceTestCase extends TestCase {

    public void testGetRequestedVideoSize() {
        // Valid spec.
        assertEquals("Wrong size.", new Dimension(123, 456), MediaStreamService.getRequestedVideoSize("123x456"));
        assertEquals("Wrong size.", new Dimension(456, 123), MediaStreamService.getRequestedVideoSize("456x123"));
        assertEquals("Wrong size.", new Dimension(1, 1), MediaStreamService.getRequestedVideoSize("1x1"));

        // Missing spec.
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize(null));

        // Invalid spec.
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("123"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("123x"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("x123"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("x"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("foo123x456bar"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("foo123x456"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("123x456bar"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("fooxbar"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("-1x1"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("1x-1"));

        // Too large.
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("3000x100"));
        assertNull("Wrong size.", MediaStreamService.getRequestedVideoSize("100x3000"));
    }

    public void testGetSuitableVideoSize() {

        // 4:3 aspect rate
        doTestGetSuitableVideoSize(1280, 960, 200, 416, 312);
        doTestGetSuitableVideoSize(1280, 960, 300, 416, 312);
        doTestGetSuitableVideoSize(1280, 960, 400, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 500, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 600, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 700, 480, 360);
        doTestGetSuitableVideoSize(1280, 960, 800, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 900, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1000, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1100, 640, 480);
        doTestGetSuitableVideoSize(1280, 960, 1200, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 1500, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 1800, 768, 576);
        doTestGetSuitableVideoSize(1280, 960, 2000, 768, 576);

        // 16:9 aspect rate
        doTestGetSuitableVideoSize(1280, 720, 200, 416, 234);
        doTestGetSuitableVideoSize(1280, 720, 300, 416, 234);
        doTestGetSuitableVideoSize(1280, 720, 400, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 500, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 600, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 700, 480, 270);
        doTestGetSuitableVideoSize(1280, 720, 800, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 900, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1000, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1100, 640, 360);
        doTestGetSuitableVideoSize(1280, 720, 1200, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 1500, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 1800, 768, 432);
        doTestGetSuitableVideoSize(1280, 720, 2000, 768, 432);

        // Small original size.
        doTestGetSuitableVideoSize(100, 100, 1000, 100, 100);
        doTestGetSuitableVideoSize(100, 1000, 1000, 100, 1000);
        doTestGetSuitableVideoSize(1000, 100, 100, 1000, 100);

        // Unknown original size.
        doTestGetSuitableVideoSize(720, null, 200, 416, 234);
        doTestGetSuitableVideoSize(null, 540, 300, 416, 234);
        doTestGetSuitableVideoSize(null, null, 400, 480, 270);
        doTestGetSuitableVideoSize(720, null, 500, 480, 270);
        doTestGetSuitableVideoSize(null, 540, 600, 480, 270);
        doTestGetSuitableVideoSize(null, null, 700, 480, 270);
        doTestGetSuitableVideoSize(720, null, 1200, 768, 432);
        doTestGetSuitableVideoSize(null, 540, 1500, 768, 432);
        doTestGetSuitableVideoSize(null, null, 2000, 768, 432);

        // Odd original size.
        doTestGetSuitableVideoSize(203, 101, 1500, 204, 102);
        doTestGetSuitableVideoSize(464, 853, 1500, 464, 854);
    }

    private void doTestGetSuitableVideoSize(Integer existingWidth, Integer existingHeight, Integer maxBitRate, int expectedWidth, int expectedHeight) {
        Dimension dimension = TranscodingService.getSuitableVideoSize(existingWidth, existingHeight, maxBitRate);
        assertEquals("Wrong width.", expectedWidth, dimension.width);
        assertEquals("Wrong height.", expectedHeight, dimension.height);
    }
}
