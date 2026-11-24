package org.airsonic.player.io

import com.google.common.util.concurrent.RateLimiter
import org.airsonic.player.domain.TransferStatus
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

/** Efficient synchronized pipe streams used by downloads and media transfers. */
open class PipeStreams {
    /** Input side of the custom circular-buffer pipe. */
    class PipedInputStream : InputStream {
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var buffer: ByteArray = ByteArray(0)
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var closed = false
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var readLaps = 0
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var readPosition = 0
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var writeLaps = 0
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var writePosition = 0
        @get:JvmSynthetic
        internal val monitor = java.lang.Object()

        @JvmField
        var source: PipedOutputStream? = null

        @Throws(IOException::class)
        constructor() : this(null)

        @Throws(IOException::class)
        constructor(source: PipedOutputStream?) : this(source, 0x10000)

        @Throws(IOException::class)
        constructor(source: PipedOutputStream?, bufferSize: Int) {
            if (source != null) connect(source)
            buffer = ByteArray(bufferSize)
        }

        @Throws(IOException::class)
        override fun available(): Int = when {
                writePosition > readPosition -> writePosition - readPosition
                writePosition < readPosition -> buffer.size - readPosition + 1 + writePosition
                writeLaps > readLaps -> buffer.size
                else -> 0
            }

        @Throws(IOException::class)
        override fun close() {
            if (source == null) throw IOException("Unconnected pipe")
            synchronized(monitor) {
                closed = true
                monitor.notifyAll()
            }
        }

        @Throws(IOException::class)
        fun connect(source: PipedOutputStream) {
            if (this.source != null) throw IOException("Pipe already connected")
            this.source = source
            source.sink = this
        }

        @Throws(IOException::class)
        override fun read(): Int {
            val byte = ByteArray(1)
            val result = read(byte)
            return if (result == -1) -1 else byte[0].toInt()
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray): Int = read(bytes, 0, bytes.size)

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            if (source == null) throw IOException("Unconnected pipe")

            synchronized(monitor) {
                if (writePosition == readPosition && writeLaps == readLaps) {
                    if (closed) return -1
                    try {
                        monitor.notifyAll()
                        monitor.wait()
                    } catch (interrupted: InterruptedException) {
                        throw IOException(interrupted.message)
                    }
                    return read(bytes, off, len)
                }

                val amount = minOf(len, (if (writePosition > readPosition) writePosition else buffer.size) - readPosition)
                System.arraycopy(buffer, readPosition, bytes, off, amount)
                readPosition += amount

                if (readPosition == buffer.size) {
                    readPosition = 0
                    ++readLaps
                }

                if (amount < len) {
                    val second = read(bytes, off + amount, len - amount)
                    return if (second == -1) amount else amount + second
                }

                monitor.notifyAll()
                return amount
            }
        }
    }

    /** Output side of the custom circular-buffer pipe. */
    class PipedOutputStream : OutputStream {
        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var sink: PipedInputStream? = null

        @Throws(IOException::class)
        constructor() : this(null)

        @Throws(IOException::class)
        constructor(sink: PipedInputStream?) {
            if (sink != null) connect(sink)
        }

        @Throws(IOException::class)
        override fun close() {
            val connectedSink = sink ?: throw IOException("Unconnected pipe")
            synchronized(connectedSink.monitor) {
                connectedSink.closed = true
                flush()
            }
        }

        @Throws(IOException::class)
        fun connect(sink: PipedInputStream) {
            if (this.sink != null) throw IOException("Pipe already connected")
            this.sink = sink
            sink.source = this
        }

        @Throws(IOException::class)
        override fun flush() {
            val connectedSink = sink ?: throw NullPointerException("sink")
            synchronized(connectedSink.monitor) { connectedSink.monitor.notifyAll() }
        }

        @Throws(IOException::class)
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        @Throws(IOException::class)
        override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)

        @Throws(IOException::class)
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            val connectedSink = sink ?: throw IOException("Unconnected pipe")
            if (connectedSink.closed) throw IOException("Broken pipe")

            synchronized(connectedSink.monitor) {
                if (connectedSink.writePosition == connectedSink.readPosition &&
                    connectedSink.writeLaps > connectedSink.readLaps
                ) {
                    try {
                        connectedSink.monitor.notifyAll()
                        connectedSink.monitor.wait()
                    } catch (interrupted: InterruptedException) {
                        throw IOException(interrupted.message)
                    }
                    write(bytes, off, len)
                    return
                }

                val amount = minOf(
                    len,
                    (if (connectedSink.writePosition < connectedSink.readPosition) {
                        connectedSink.readPosition
                    } else {
                        connectedSink.buffer.size
                    }) - connectedSink.writePosition,
                )
                System.arraycopy(bytes, off, connectedSink.buffer, connectedSink.writePosition, amount)
                connectedSink.writePosition += amount

                if (connectedSink.writePosition == connectedSink.buffer.size) {
                    connectedSink.writePosition = 0
                    ++connectedSink.writeLaps
                }

                if (amount < len) {
                    write(bytes, off + amount, len - amount)
                } else {
                    connectedSink.monitor.notifyAll()
                }
            }
        }
    }

    /** Input wrapper that updates transfer status and optionally rate-limits reads. */
    class MonitoredInputStream(
        delegate: InputStream,
        private val rateLimiter: RateLimiter?,
        statusSupplier: Supplier<TransferStatus>,
        private val statusCloser: Consumer<TransferStatus>,
        initAction: BiConsumer<InputStream, TransferStatus>,
    ) : FilterInputStream(delegate) {
        private val status = statusSupplier.get()

        init {
            initAction.accept(delegate, status)
        }

        private fun acquire(length: Int) {
            rateLimiter?.acquire(length)
        }

        @Throws(IOException::class)
        override fun read(): Int {
            val read = super.read()
            if (read >= 0) {
                acquire(1)
                status.addBytesTransferred(1)
            }
            return read
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            val read = super.read(bytes, off, len)
            if (read > 0) {
                acquire(read)
                status.addBytesTransferred(read.toLong())
            }
            return read
        }

        @Throws(IOException::class)
        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            status.addBytesSkipped(skipped)
            return skipped
        }

        @Throws(IOException::class)
        override fun close() {
            super.close()
            statusCloser.accept(status)
        }
    }
}
