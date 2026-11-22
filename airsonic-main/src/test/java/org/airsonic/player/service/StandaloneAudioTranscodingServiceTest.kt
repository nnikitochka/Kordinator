package org.airsonic.player.service

import org.airsonic.player.dao.TranscodingDao
import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.Transcoding
import org.airsonic.player.io.TranscodeInputStream
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import kotlin.test.*

class StandaloneAudioTranscodingServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun configuredProfileProducesActualMp3AndStartupFailureDoesNotReturnOriginal() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")), "ffmpeg is required for audio encoding verification")
        val pcm = ByteArray(16_000)
        val source = root.resolve("track with spaces.wav")
        AudioInputStream(ByteArrayInputStream(pcm), AudioFormat(8000f, 16, 1, true, false), 8000).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, source.toFile())
        }
        val file = MediaFile().apply {
            id = 1; folderId = 1; path = source.fileName.toString()
            format = "wav"; mediaType = MediaFile.MediaType.MUSIC
        }
        val dao = mock(TranscodingDao::class.java)
        val folders = mock(MediaFolderService::class.java)
        `when`(folders.getMusicFolderById(1)).thenReturn(MusicFolder(1, root, "Music", MusicFolder.Type.MEDIA, true, Instant.now()))
        val settings = mock(SettingsService::class.java)
        `when`(settings.downsamplingCommand).thenReturn("/usr/bin/ffmpeg -nostdin -v error -i %s -b:a %bk -f mp3 -")
        val engine = TranscodingService(dao, settings, folders) { mock(PlayerService::class.java) }
        val service = StandaloneAudioTranscodingService(engine)
        val profile = Transcoding(1, "MP3", "wav flac", "mp3",
            "/usr/bin/ffmpeg -nostdin -v error -i %s -b:a %bk -f mp3 -", null, null, true)
        `when`(dao.allTranscodings).thenReturn(listOf(profile))
        assertNull(service.prepare(file, "opus", 128))
        assertFailsWith<IllegalArgumentException> { service.prepare(file, "mp3", 1000) }
        val plan = assertNotNull(service.prepare(file, "MP3", 128))
        val bytes = service.open(plan).use { it.readAllBytes() }
        assertTrue(bytes.size > 1000)
        assertEquals("ID3", bytes.take(3).map { it.toInt().toChar() }.joinToString(""))
        val output = root.resolve("encoded.mp3")
        Files.write(output, bytes)
        val decoder = ProcessBuilder("/usr/bin/ffmpeg", "-nostdin", "-v", "error", "-i", output.toString(), "-f", "null", "-")
            .redirectError(ProcessBuilder.Redirect.INHERIT).start()
        assertTrue(decoder.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, decoder.exitValue())
        profile.step1 = root.resolve("missing-transcoder").toString() + " %s"
        assertFailsWith<java.io.IOException> { service.open(plan) }
        file.format = "mp3"
        file.path = output.fileName.toString()
        val downsample = assertNotNull(service.prepare(file, "mp3", 64))
        assertTrue(downsample.parameters.isDownsample)
        assertTrue(service.open(downsample).use { it.readAllBytes() }.size > 500)
    }

    @Test
    fun closingFinalPipelineStreamTerminatesUpstreamProcess() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/yes")))
        val upstream = TranscodeInputStream(ProcessBuilder("/usr/bin/yes"), null, null)
        val downstream = TranscodeInputStream(ProcessBuilder("/usr/bin/cat"), upstream, null)
        try {
            assertTrue(downstream.read() >= 0)
            downstream.close()
            assertTrue(upstream.process!!.waitFor(5, TimeUnit.SECONDS))
            assertTrue(downstream.process!!.waitFor(5, TimeUnit.SECONDS))
        } finally {
            downstream.close()
            upstream.close()
        }
    }
}
