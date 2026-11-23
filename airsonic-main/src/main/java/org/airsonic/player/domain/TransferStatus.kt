package org.airsonic.player.domain

import org.apache.commons.collections4.queue.CircularFifoQueue
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Status for a single stream, download, or upload. */
class TransferStatus(player: Player?) {
    companion object {
        private const val HISTORY_LENGTH = 200
        private const val SAMPLE_INTERVAL_MILLIS = 5000L
    }

    val id: UUID = UUID.randomUUID()
    val player: Player? = player
    var externalFile: Path? = null
    var mediaFile: MediaFile? = null
    private val bytesTransferredCounter = AtomicLong()
    private val bytesSkippedCounter = AtomicLong()
    private val bytesTotalCounter = AtomicLong()
    private val historyValue = SampleHistory()

    var bytesTransferred: Long
        get() = bytesTransferredCounter.get()
        set(value) {
            bytesTransferredCounter.set(value)
            createSample(false)
        }

    var bytesSkipped: Long
        get() = bytesSkippedCounter.get()
        set(value) {
            bytesSkippedCounter.set(value)
        }

    var bytesTotal: Long
        get() = bytesTotalCounter.get()
        set(value) {
            bytesTotalCounter.set(value)
        }

    val history: SampleHistory
        get() = SampleHistory(historyValue)

    val millisSinceLastUpdate: Long
        get() = if (historyValue.isEmpty()) 0L else System.currentTimeMillis() - historyValue.getLast().timestamp

    val historyLengthMillis: Long
        get() = SAMPLE_INTERVAL_MILLIS * (HISTORY_LENGTH - 1)

    @Volatile
    private var terminated = false

    @Volatile
    var isActive: Boolean = true
        @Synchronized set(value) {
            field = value
            if (value) {
                bytesSkipped = 0L
                bytesTotal = 0L
                bytesTransferred = 0L
            } else {
                createSample(true)
            }
        }

    fun addBytesTransferred(byteCount: Long) {
        bytesTransferredCounter.addAndGet(byteCount)
        createSample(false)
    }

    @Synchronized
    private fun createSample(force: Boolean) {
        val now = System.currentTimeMillis()
        if (historyValue.isEmpty()) {
            historyValue.add(Sample(bytesTransferredCounter.get(), now))
        } else {
            val lastSample = historyValue.getLast()
            if (force || now - lastSample.timestamp > SAMPLE_INTERVAL_MILLIS) {
                historyValue.add(Sample(bytesTransferredCounter.get(), now))
            }
        }
    }

    fun addBytesSkipped(byteCount: Long) {
        bytesSkippedCounter.addAndGet(byteCount)
    }

    fun terminate() {
        terminated = true
    }

    fun terminated(): Boolean {
        val result = terminated
        terminated = false
        return result
    }

    class Sample(val bytesTransferred: Long, val timestamp: Long)

    class SampleHistory : CircularFifoQueue<Sample> {
        constructor() : super(HISTORY_LENGTH)

        constructor(other: SampleHistory) : this() {
            addAll(other)
        }

        fun getLast(): Sample = get(size - 1)
    }

    override fun toString(): String =
        "TransferStatus-${hashCode()} [player: ${player!!.id}, file: $externalFile, " +
            "mediaFile: ${if (mediaFile == null) "" else mediaFile!!.id.toString()}, " +
            "terminated: $terminated, active: $isActive]"
}
