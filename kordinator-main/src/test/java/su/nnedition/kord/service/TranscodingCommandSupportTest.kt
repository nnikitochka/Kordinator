package su.nnedition.kord.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranscodingCommandSupportTest {
    @Test
    fun substitutesCommandVariablesAndLeavesUnprovidedVariablesIntact() {
        val command = TranscodingService.transformTranscodingVariables(
            "transcoder -i %s -title %t -rate %b",
            mapOf("%s" to "/music/track.flac", "%t" to "First Track", "%b" to null),
        )

        assertEquals(
            listOf("transcoder", "-i", "/music/track.flac", "-title", "First Track", "-rate", "%b"),
            command.command(),
        )
    }

    @Test
    fun computesVideoDimensionsWithoutUpscalingAndPreservesAspectRatio() {
        assertEquals(640 to 360, TranscodingService.getSuitableVideoSize(1920, 1080, 800).let { it.width to it.height })
        assertEquals(320 to 240, TranscodingService.getSuitableVideoSize(320, 240, 800).let { it.width to it.height })
        assertEquals(1280 to 720, TranscodingService.getSuitableVideoSize(null, null, 5000).let { it.width to it.height })
    }

    @Test
    fun mapsVideoLimitsToAudioAndAverageBitRates() {
        assertEquals(64, TranscodingService.getSuitableAudioBitRate(1199))
        assertEquals(96, TranscodingService.getSuitableAudioBitRate(1200))
        assertEquals(128, TranscodingService.getSuitableAudioBitRate(5000))
        assertEquals(365, TranscodingService.getAverageVideoBitRate(400))
        assertEquals(900, TranscodingService.getAverageVideoBitRate(1000))
    }
}
