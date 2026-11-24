package org.airsonic.player.controller.cover

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints

class AutoCover(
    private val graphics: Graphics2D,
    key: String?,
    private val artist: String?,
    private val album: String?,
    private val width: Int,
    private val height: Int,
) {
    private val color = Color(COLORS[Math.abs(requireNotNull(key).hashCode()) % COLORS.size])

    fun paintCover() {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        graphics.paint = color
        graphics.fillRect(0, 0, width, height)

        val y = height * 2 / 3
        graphics.paint = GradientPaint(0f, y.toFloat(), Color(82, 82, 82), 0f, height.toFloat(), Color.BLACK)
        graphics.fillRect(0, y, width, height / 3)

        graphics.paint = Color.WHITE
        val fontSize = 3.0f + height * 0.07f
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize.toInt())

        album?.let { graphics.drawString(it, width * 0.05f, height * 0.6f) }
        artist?.let { graphics.drawString(it, width * 0.05f, height * 0.8f) }

        val borderWidth = height / 50
        graphics.fillRect(0, 0, borderWidth, height)
        graphics.fillRect(width - borderWidth, 0, height - borderWidth, height)
        graphics.fillRect(0, 0, width, borderWidth)
        graphics.fillRect(0, height - borderWidth, width, height)
    }

    private companion object {
        val COLORS = intArrayOf(0x33B5E5, 0xAA66CC, 0x99CC00, 0xFFBB33, 0xFF4444)
    }
}
