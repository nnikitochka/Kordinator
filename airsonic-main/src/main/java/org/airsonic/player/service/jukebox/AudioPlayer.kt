package org.airsonic.player.service.jukebox

import org.airsonic.player.util.FileUtil
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.exp
import kotlin.math.ln

/** Plays PCM audio with pause, resume and end-of-stream notifications. */
class AudioPlayer internal constructor(
    private val input: InputStream,
    private val listener: Listener?,
    private val line: SourceDataLine,
    format: AudioFormat,
) : AutoCloseable {
    private val state = AtomicReference(State.PAUSED)
    private var gainControl: FloatControl? = null

    @Throws(Exception::class)
    constructor(input: InputStream, listener: Listener?) : this(input, listener, openLine(), FORMAT)

    init {
        line.open(format)
        LOG.debug("Opened line {}", line)
        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            setGain(DEFAULT_GAIN)
        }
        Thread { writeAudio() }.start()
    }

    @Synchronized
    fun play() {
        if (state.get() == State.PAUSED) {
            line.start()
            setState(State.PLAYING)
        }
    }

    @Synchronized
    fun pause() {
        if (state.get() == State.PLAYING) {
            setState(State.PAUSED)
            line.stop()
            line.flush()
        }
    }

    @Synchronized
    override fun close() {
        if (state.get() != State.CLOSED && state.get() != State.EOM) setState(State.CLOSED)
        try { line.stop() } catch (failure: Throwable) { LOG.warn("Failed to stop player: $failure", failure) }
        try {
            if (line.isOpen) {
                line.close()
                LOG.debug("Closed line {}", line)
            }
        } catch (failure: Throwable) { LOG.warn("Failed to close player: $failure", failure) }
        FileUtil.closeQuietly(input)
    }

    fun getState(): State = state.get()

    fun setGain(gain: Float) {
        gainControl?.let { control ->
            val minimum = control.minimum.toDouble()
            val maximum = minOf(0.0, control.maximum.toDouble())
            val amplitude = 0.5f * maximum - minimum
            val constant = ln(10.0) / 20
            val value = minimum + (1 / constant) * ln(1 + (exp(constant * amplitude) - 1) * gain)
            control.value = value.coerceAtMost(maximum).coerceAtLeast(minimum).toFloat()
        }
    }

    fun getPosition(): Int = (line.microsecondPosition / 1_000_000L).toInt()

    private fun setState(next: State) {
        if (state.getAndSet(next) != next) listener?.stateChanged(this, next)
    }

    private fun writeAudio() {
        try {
            val buffer = ByteArray(line.bufferSize)
            while (true) {
                when (state.get()) {
                    State.CLOSED, State.EOM -> return
                    State.PAUSED -> Thread.sleep(250)
                    State.PLAYING -> {
                        val count = fill(buffer)
                        if (count == -1) {
                            setState(State.EOM)
                            return
                        }
                        line.write(buffer, 0, count)
                    }
                }
            }
        } catch (failure: Throwable) {
            LOG.warn("Error when copying audio data: {}", failure, failure)
        } finally {
            close()
        }
    }

    @Throws(IOException::class)
    private fun fill(buffer: ByteArray): Int {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val count = input.read(buffer, bytesRead, buffer.size - bytesRead)
            if (count == -1) return if (bytesRead == 0) -1 else bytesRead
            bytesRead += count
        }
        return bytesRead
    }

    fun interface Listener { fun stateChanged(player: AudioPlayer, state: State) }
    enum class State { PAUSED, PLAYING, CLOSED, EOM }

    companion object {
        const val DEFAULT_GAIN = 0.75f
        private val LOG = LoggerFactory.getLogger(AudioPlayer::class.java)
        private val FORMAT = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, true)
        private fun openLine(): SourceDataLine = AudioSystem.getSourceDataLine(FORMAT)
    }
}
