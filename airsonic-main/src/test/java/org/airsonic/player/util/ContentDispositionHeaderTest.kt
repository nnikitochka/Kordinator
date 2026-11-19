package org.airsonic.player.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContentDispositionHeaderTest {
    @Test
    fun writesAsciiFilenameAsFallbackAndExtendedParameter() {
        assertEquals(
            "attachment; filename=\"mix tape.m3u\"; filename*=UTF-8''mix%20tape.m3u",
            ContentDispositionHeader.attachment("mix tape.m3u"),
        )
    }

    @Test
    fun preservesUnicodeFilenameUsingUtf8PercentEncoding() {
        assertEquals(
            "attachment; filename=\"____.mp3\"; filename*=UTF-8''%D0%9C%D1%83%D0%B7%D1%8B.mp3",
            ContentDispositionHeader.attachment("Музы.mp3"),
        )
    }

    @Test
    fun escapesQuotesBackslashesAndControlCharactersFromFallback() {
        assertEquals(
            "attachment; filename=\"a___b\"; filename*=UTF-8''a%22%5C%0Ab",
            ContentDispositionHeader.attachment("a\"\\\nb"),
        )
    }
}
