package org.airsonic.player.service;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test of {@link SecurityService}.
 *
 * @author Sindre Mehus
 */
public class SecurityServiceTestCase {

    @Test
    public void testIsFileInFolder() {
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo.mp3"), Paths.get("/")));

        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo.mp3"), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo.mp3"), Paths.get("/music/")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("\\music\\foo.mp3"), Paths.get("\\music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("\\music\\foo.mp3"), Paths.get("\\music\\")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("music/foo.mp3"), Paths.get("music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("music\\foo.mp3"), Paths.get("music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("music/foo.mp3"), Paths.get("music/")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("music\\foo.mp3"), Paths.get("music\\")));

        assertFalse(SecurityService.isFileInFolder(Paths.get(""), Paths.get("/tmp")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("foo.mp3"), Paths.get("/tmp")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/foo.mp3"), Paths.get("/tmp")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/foo.mp3"), Paths.get("/tmp/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("music/foo.mp3"), Paths.get("/music")));

        // identity tests
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/a"), Paths.get("/music/a")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/a"), Paths.get("/music/a/")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/a/"), Paths.get("/music/a/")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/a/"), Paths.get("/music/a")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/a2"), Paths.get("/music/a")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/a"), Paths.get("/music/a2")));

        // Test with redundant references
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo..mp3"), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo.."), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo.../"), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music/foo/.."), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("../music/foo"), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/../music/foo"), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/../foo"), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/../bar/../foo"), Paths.get("/music")));
        assertTrue(SecurityService.isFileInFolder(Paths.get("/music\\foo\\.."), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("..\\music/foo"), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music\\../foo"), Paths.get("/music")));
        assertFalse(SecurityService.isFileInFolder(Paths.get("/music/..\\bar/../foo"), Paths.get("/music")));
    }
}
