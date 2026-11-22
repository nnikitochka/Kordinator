package org.airsonic.player.io

import org.airsonic.player.util.FileUtil
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Subclass of [InputStream] which provides on-the-fly transcoding.
 * Instances of `TranscodeInputStream` can be chained together, for instance to convert
 * from OGG to WAV to MP3.
 *
 * @param processBuilder Used to create the external process.
 * @param input Data to feed to the process.  May be `null`.
 * @param tmpFile Temporary file to delete when this stream is closed.  May be `null`.
 */
class TranscodeInputStream(
    processBuilder: ProcessBuilder,
    private val input: InputStream?,
    private val tmpFile: Path?
) : InputStream() {
    init {
        LOG.info(
            "Starting transcoder: {}",
            processBuilder.command().stream().collect(Collectors.joining("][", "[", "]"))
        )
    }
    val process: Process? = processBuilder.start()
    private val closed = AtomicBoolean(false)
    private val processInputStream: InputStream = process!!.inputStream
    private val processOutputStream: OutputStream = process!!.outputStream

    /**
     * Creates a transcoded input stream by executing an external process. If `input` is not null,
     * data from it is copied to the command.
     *
     * @throws IOException If an I/O error occurs.
     */
    init {
        // Must read stderr from the process, otherwise it may block.
        val name: String = processBuilder.command().first()
        InputStreamReaderThread(process!!.errorStream, name, true).start()

        // Copy data in a separate thread
        if (input != null) {
            object : Thread("$name TranscodedInputStream copy thread") {
                override fun run() {
                    try {
                        IOUtils.copy(input, processOutputStream)
                    } catch (_: IOException) {
                        // Intentionally ignored. Will happen if the remote player closes the stream.
                    } finally {
                        FileUtil.closeQuietly(input)
                        FileUtil.closeQuietly(processOutputStream)
                    }
                }
            }.start()
        }
    }

    /**
     * @see InputStream.read
     */
    @Throws(IOException::class)
    override fun read(): Int {
        return processInputStream.read()
    }

    /**
     * @see InputStream.read
     */
    @Throws(IOException::class)
    override fun read(bytes: ByteArray): Int {
        return processInputStream.read(bytes)
    }

    /**
     * @see InputStream.read
     */
    @Throws(IOException::class)
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        return processInputStream.read(bytes, off, len)
    }

    /**
     * @see InputStream.close
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process?.destroy()
        FileUtil.closeQuietly(input)
        FileUtil.closeQuietly(processInputStream)
        FileUtil.closeQuietly(processOutputStream)

        if (tmpFile != null) {
            try {
                Files.deleteIfExists(tmpFile)
            } catch (_: IOException) {
                LOG.warn("Failed to delete tmp file: $tmpFile")
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TranscodeInputStream::class.java)
    }
}
