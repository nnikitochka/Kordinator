package su.nnedition.kord.service.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MetaDataTest {
    @Test
    fun streamViewsPreserveOrderAndTrackListIsReadOnly() {
        val metadata = MetaData()
        val tracks = metadata.tracks
        metadata.addTrack(Track(0, "audio", "eng", "aac"))
        metadata.addTrack(Track(1, "video", null, "mjpeg"))
        metadata.addTrack(Track(2, "video", null, "h264"))
        metadata.addTrack(Track(3, "subtitle", "eng", "srt"))
        assertEquals(listOf(0, 1, 2, 3), tracks.map { it.id })
        assertEquals(listOf(0), metadata.audioTracks.map { it.id })
        assertEquals(listOf(2), metadata.videoTracks.map { it.id })
        assertEquals(listOf(3), metadata.subtitleTracks.map { it.id })
        assertThrows(UnsupportedOperationException::class.java) { (tracks as MutableList).clear() }
        (metadata.audioTracks as MutableList).clear()
        assertEquals(4, metadata.tracks.size)
        assertFalse(metadata.variableBitRate)
    }
}
