package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TransferStatusTest {
    @Test
    fun countersResetOnReactivationAndHistoryIsReturnedAsCopy() {
        val status = TransferStatus(null)
        status.addBytesTransferred(12)
        status.addBytesSkipped(3)
        status.bytesTotal = 30

        status.isActive = false
        val historySnapshot = status.history
        assertFalse(status.isActive)
        assertEquals(12, status.bytesTransferred)
        assertEquals(3, status.bytesSkipped)
        assertEquals(30, status.bytesTotal)
        assertEquals(2, historySnapshot.size)

        status.isActive = true
        assertTrue(status.isActive)
        assertEquals(0, status.bytesTransferred)
        assertEquals(0, status.bytesSkipped)
        assertEquals(0, status.bytesTotal)
        assertNotSame(historySnapshot, status.history)
        assertEquals(2, historySnapshot.size)
        assertEquals(995_000, status.historyLengthMillis)
    }

    @Test
    fun terminationIsConsumedByTheFirstRead() {
        val status = TransferStatus(null)

        status.terminate()

        assertTrue(status.terminated())
        assertFalse(status.terminated())
    }

    @Test
    fun sampleHistoryKeepsOnlyTheMostRecentTwoHundredSamples() {
        val history = TransferStatus.SampleHistory()
        repeat(201) { index -> history.add(TransferStatus.Sample(index.toLong(), index.toLong())) }

        assertEquals(200, history.size)
        assertEquals(1, history.first().bytesTransferred)
        assertEquals(200, history.getLast().bytesTransferred)
    }
}
