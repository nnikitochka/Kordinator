package org.airsonic.player.service.hls

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.io.TranscodeInputStream
import org.airsonic.player.service.TranscodingService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

class HlsSessionTest {
    @TempDir lateinit var home: Path

    @Test
    fun returnsSegmentFromShortLivedProcessAndDeletesCacheOnShutdown() {
        val oldHome = System.getProperty("airsonic.home")
        System.setProperty("airsonic.home", home.toString())
        try {
            val key = HlsSession.Key(7, "player", 800, "640x360", 10, null)
            assertEquals(key, HlsSession.Key(7, "player", 800, "640x360", 10, null))
            assertEquals(key.id(), HlsSession.Key(7, "player", 800, "640x360", 10, null).id())
            val media = MediaFile()
            val transcoding = mock(TranscodingService::class.java)
            val parameters = TranscodingService.Parameters(media, null)
            `when`(transcoding.getParameters(eq(media) ?: media, isNull(), eq(800), eq("ts") ?: "ts", any())).thenReturn(parameters)
            `when`(transcoding.getTranscodedInputStream(parameters)).thenAnswer {
                val directory = home.resolve("hls").resolve(key.id())
                Files.createDirectories(directory)
                Files.writeString(directory.resolve("3.ts"), "segment")
                val stream = TranscodeInputStream(ProcessBuilder("/bin/sh", "-c", "exit 0"), null, null)
                stream.process!!.waitFor()
                stream
            }
            val session = HlsSession(key, media, transcoding)
            val segment = session.waitForSegment(3, 1000)!!
            assertEquals("segment", Files.readString(segment))
            assertEquals(segment, session.waitForSegment(3, 1000))
            verify(transcoding, times(1)).getTranscodedInputStream(parameters)
            session.destroySession()
            assertFalse(Files.exists(segment.parent))
        } finally {
            if (oldHome == null) System.clearProperty("airsonic.home") else System.setProperty("airsonic.home", oldHome)
        }
    }
}
