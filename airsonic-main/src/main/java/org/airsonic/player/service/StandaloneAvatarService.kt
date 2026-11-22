package org.airsonic.player.service

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

data class StandaloneAvatar(val bytes: ByteArray, val width: Int, val height: Int)

/** Stores each local account's bounded, normalized avatar outside the legacy settings database. */
class StandaloneAvatarService(private val directory: Path) {
    fun get(userId: Long): StandaloneAvatar? {
        require(userId >= 0) { "User ID must be non-negative" }
        val file = fileFor(userId)
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null
        val bytes = Files.readAllBytes(file)
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        return StandaloneAvatar(bytes, image.width, image.height)
    }

    fun set(userId: Long, bytes: ByteArray): StandaloneAvatar {
        require(userId >= 0) { "User ID must be non-negative" }
        require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) { "Avatar must be between 1 byte and 5 MiB" }
        val image = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "Unsupported or invalid image" }
            val reader = readers.next()
            try {
                reader.input = input
                val imageWidth = reader.getWidth(0)
                val imageHeight = reader.getHeight(0)
                require(imageWidth > 0 && imageHeight > 0 && imageWidth.toLong() * imageHeight <= MAX_PIXELS) {
                    "Image exceeds 16 megapixels"
                }
                reader.read(0)
            } finally {
                reader.dispose()
            }
        }
        val scale = minOf(1.0, MAX_SIDE.toDouble() / maxOf(image.width, image.height))
        val width = maxOf(1, (image.width * scale).toInt())
        val height = maxOf(1, (image.height * scale).toInt())
        val normalized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            val sourceY = ((y + 0.5) * image.height / height - 0.5).coerceIn(0.0, (image.height - 1).toDouble())
            val y0 = sourceY.toInt()
            val y1 = minOf(y0 + 1, image.height - 1)
            val fy = sourceY - y0
            for (x in 0 until width) {
                val sourceX = ((x + 0.5) * image.width / width - 0.5).coerceIn(0.0, (image.width - 1).toDouble())
                val x0 = sourceX.toInt()
                val x1 = minOf(x0 + 1, image.width - 1)
                val fx = sourceX - x0
                val topLeft = image.getRGB(x0, y0)
                val topRight = image.getRGB(x1, y0)
                val bottomLeft = image.getRGB(x0, y1)
                val bottomRight = image.getRGB(x1, y1)
                var pixel = 0
                for (shift in intArrayOf(24, 16, 8, 0)) {
                    val top = ((topLeft ushr shift and 0xff) * (1.0 - fx) + (topRight ushr shift and 0xff) * fx)
                    val bottom = ((bottomLeft ushr shift and 0xff) * (1.0 - fx) + (bottomRight ushr shift and 0xff) * fx)
                    val channel = (top * (1.0 - fy) + bottom * fy).toInt().coerceIn(0, 255)
                    pixel = pixel or (channel shl shift)
                }
                normalized.setRGB(x, y, pixel)
            }
        }
        val output = ByteArrayOutputStream()
        check(ImageIO.write(normalized, "png", output)) { "PNG encoder unavailable" }
        val normalizedBytes = output.toByteArray()
        Files.createDirectories(directory)
        val root = directory.toRealPath()
        val target = root.resolve("$userId.png")
        require(target.parent == root && !Files.isSymbolicLink(target)) { "Avatar destination is not safe" }
        val temporary = Files.createTempFile(root, ".avatar-", ".tmp")
        try {
            Files.write(temporary, normalizedBytes)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return StandaloneAvatar(normalizedBytes, width, height)
    }

    fun delete(userId: Long): Boolean {
        require(userId >= 0) { "User ID must be non-negative" }
        return Files.deleteIfExists(fileFor(userId))
    }

    private fun fileFor(userId: Long): Path = directory.resolve("$userId.png")

    private companion object {
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        const val MAX_PIXELS = 16_000_000L
        const val MAX_SIDE = 64
    }
}
