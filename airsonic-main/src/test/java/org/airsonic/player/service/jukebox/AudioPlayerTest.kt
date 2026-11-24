package org.airsonic.player.service.jukebox

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine

class AudioPlayerTest {
    private val format = AudioFormat(44100f, 16, 2, true, true)

    @Test
    fun playsCompleteAndPartialFramesThenSignalsEndAndClosesResources() {
        val line = mock(SourceDataLine::class.java)
        `when`(line.bufferSize).thenReturn(4)
        `when`(line.isOpen).thenReturn(true)
        val input = spy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)))
        val states = CopyOnWriteArrayList<AudioPlayer.State>()
        val ended = CountDownLatch(1)
        val player = AudioPlayer(input, AudioPlayer.Listener { _, state ->
            states.add(state)
            if (state == AudioPlayer.State.EOM) ended.countDown()
        }, line, format)
        assertEquals(AudioPlayer.State.PAUSED, player.getState())
        player.play()
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        verify(line, timeout(5000)).write(any(ByteArray::class.java), eq(0), eq(4))
        verify(line, timeout(5000)).write(any(ByteArray::class.java), eq(0), eq(2))
        verify(line, timeout(5000)).close()
        verify(input, timeout(5000)).close()
        assertEquals(listOf(AudioPlayer.State.PLAYING, AudioPlayer.State.EOM), states)
        assertEquals(AudioPlayer.State.EOM, player.getState())
    }

    @Test
    fun closeWhilePausedSignalsClosedAndDoesNotStartTheLine() {
        val line = mock(SourceDataLine::class.java)
        `when`(line.bufferSize).thenReturn(4)
        val states = CopyOnWriteArrayList<AudioPlayer.State>()
        val player = AudioPlayer(ByteArrayInputStream(byteArrayOf()), AudioPlayer.Listener { _, state -> states.add(state) }, line, format)
        player.close()
        assertEquals(AudioPlayer.State.CLOSED, player.getState())
        assertEquals(listOf(AudioPlayer.State.CLOSED), states)
        verify(line, never()).start()
    }
}
