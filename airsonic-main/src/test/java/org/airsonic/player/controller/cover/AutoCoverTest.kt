package org.airsonic.player.controller.cover

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class AutoCoverTest {
    @Test
    fun `paintCover fills background with deterministic key color`() {
        val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        try {
            AutoCover(graphics, "album-1", "Artist", "Album", image.width, image.height).paintCover()

            val palette = intArrayOf(0x33B5E5, 0xAA66CC, 0x99CC00, 0xFFBB33, 0xFF4444)
            val color = Color(palette[Math.abs("album-1".hashCode()) % palette.size])
            assertEquals(color.rgb, image.getRGB(100, 40))
        } finally {
            graphics.dispose()
        }
    }
}
