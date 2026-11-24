package org.airsonic.player.io

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.PlayQueue
import java.io.IOException
import java.io.InputStream
import java.util.function.Consumer
import java.util.function.Function

open class PlayQueueInputStream(
    private val queue: PlayQueue,
    private val fileStartListener: Consumer<MediaFile>,
    private val fileEndListener: Consumer<MediaFile>,
    private val streamGenerator: Function<MediaFile, InputStream>,
) : InputStream() {
    private var currentStream: InputStream? = null
    private var currentFile: MediaFile? = null

    @Throws(IOException::class)
    override fun read(): Int {
        val bytes = ByteArray(1)
        val count = read(bytes)
        return if (count == -1) -1 else bytes[0].toInt()
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        prepare()
        val stream = currentStream
        if (stream == null || queue.status == PlayQueue.Status.STOPPED) return -1

        val count = stream.read(bytes, off, len)
        if (count == -1) {
            queue.next()
            closeStream()
            return read(bytes, off, len)
        }
        return count
    }

    @Throws(IOException::class)
    private fun prepare() {
        val file = queue.getCurrentFile()
        if (file == null) {
            closeStream()
        } else if (file != currentFile) {
            closeStream()
            currentFile = file
            fileStartListener.accept(file)
            currentStream = streamGenerator.apply(file)
        }
    }

    @Throws(IOException::class)
    open fun closeStream() {
        currentStream?.let {
            it.close()
            currentStream = null
        }
        currentFile?.let {
            fileEndListener.accept(it)
            currentFile = null
        }
    }

    @Throws(IOException::class)
    override fun close() {
        closeStream()
        super.close()
    }
}
