package org.airsonic.player.controller;

import junit.framework.TestCase;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;

/**
 * @author Sindre Mehus
 * @version $Id: StreamControllerTestCase.java 3307 2013-01-04 13:48:49Z sindre_mehus $
 */
public class HLSControllerTestCase extends TestCase {

    public void testParseBitRate() {
        HLSController controller = new HLSController();

        Pair<Integer, Dimension> pair = controller.parseBitRate("1000", null);
        assertEquals(1000, pair.getLeft().intValue());
        assertNull(pair.getRight());

        pair = controller.parseBitRate("1000@400x300", null);
        assertEquals(1000, pair.getLeft().intValue());
        assertEquals(400, pair.getRight().width);
        assertEquals(300, pair.getRight().height);

        pair = controller.parseBitRate("1000@400x300", "300x400");
        assertEquals(1000, pair.getLeft().intValue());
        assertEquals(400, pair.getRight().width);
        assertEquals(300, pair.getRight().height);

        pair = controller.parseBitRate("1000", "300x400");
        assertEquals(1000, pair.getLeft().intValue());
        assertEquals(300, pair.getRight().width);
        assertEquals(400, pair.getRight().height);

        try {
            controller.parseBitRate("asdfl", null);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            controller.parseBitRate("1000@300", null);
            fail();
        } catch (IllegalArgumentException e) {
        }
        try {
            controller.parseBitRate("1000@300x400ZZ", null);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

}
