package su.nnedition.kord.service

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory

data class StandaloneAvatar(val bytes: ByteArray, val width: Int, val height: Int)

/** Stores each local account's bounded, normalized avatar outside the legacy settings database. */
class StandaloneAvatarService(private val directory: Path) {
    fun migrateLegacyAvatars(coreDataSource: DataSource, accountDataSource: DataSource, legacyAvatarRoot: Path) {
        val migrationId = "legacy-custom-avatars-1"
        accountDataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val completed = connection.prepareStatement(
                    "SELECT 1 FROM standalone_data_migration WHERE migration_id = ?",
                ).use { statement ->
                    statement.setString(1, migrationId)
                    statement.executeQuery().use { it.next() }
                }
                if (completed) {
                    connection.commit()
                    return
                }
                if (!hasTable(coreDataSource, "custom_avatar")) {
                    recordMigration(connection, migrationId)
                    connection.commit()
                    return
                }
                val accounts = connection.prepareStatement("SELECT id, username FROM local_user").use { statement ->
                    statement.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString("username"), rows.getLong("id")) } }
                }
                val legacyAvatars = coreDataSource.connection.use { coreConnection ->
                    coreConnection.prepareStatement("SELECT username, name, mime_type FROM custom_avatar").use { statement ->
                        statement.executeQuery().use { rows ->
                            buildList {
                                while (rows.next()) add(LegacyAvatar(rows.getString("username"), rows.getString("name"), rows.getString("mime_type")))
                            }
                        }
                    }
                }
                var pendingAccountMatch = false
                val avatarRoot = legacyAvatarRoot.toAbsolutePath().normalize()
                legacyAvatars.forEach { legacy ->
                    val accountId = accounts[legacy.username]
                    if (accountId == null) {
                        pendingAccountMatch = true
                        return@forEach
                    }
                    if (get(accountId) != null) return@forEach
                    val extension = legacy.mimeType.substringAfter('/', "").substringBefore(';')
                    if (extension.isBlank()) {
                        LOG.warn("Skipping legacy avatar with missing image subtype for account {}", legacy.username)
                        return@forEach
                    }
                    val candidate = avatarRoot.resolve(legacy.username).resolve("${legacy.name}.$extension").normalize()
                    if (!candidate.startsWith(avatarRoot) || candidate.parent != avatarRoot.resolve(legacy.username) ||
                        Files.isSymbolicLink(candidate.parent) ||
                        Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    ) {
                        LOG.warn("Skipping missing or unsafe legacy avatar for account {}", legacy.username)
                        return@forEach
                    }
                    val realRoot = if (Files.exists(avatarRoot, LinkOption.NOFOLLOW_LINKS)) avatarRoot.toRealPath() else return@forEach
                    val realFile = candidate.toRealPath()
                    if (!realFile.startsWith(realRoot) || Files.size(realFile) > MAX_UPLOAD_BYTES) {
                        LOG.warn("Skipping out-of-root or oversized legacy avatar for account {}", legacy.username)
                        return@forEach
                    }
                    try {
                        set(accountId, Files.readAllBytes(realFile))
                    } catch (failure: IllegalArgumentException) {
                        LOG.warn("Skipping unsupported legacy avatar for account {}", legacy.username, failure)
                    }
                }
                if (!pendingAccountMatch) recordMigration(connection, migrationId)
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

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

    private fun hasTable(dataSource: DataSource, tableName: String): Boolean = dataSource.connection.use { connection ->
        connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { rows ->
            buildList { while (rows.next()) add(rows.getString("TABLE_NAME")) }.any { it.equals(tableName, ignoreCase = true) }
        }
    }

    private fun recordMigration(connection: Connection, migrationId: String) {
        connection.prepareStatement(
            "INSERT INTO standalone_data_migration (migration_id, completed_at) VALUES (?, ?)",
        ).use { statement ->
            statement.setString(1, migrationId)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private data class LegacyAvatar(val username: String, val name: String, val mimeType: String)

    private companion object {
        val LOG = LoggerFactory.getLogger(StandaloneAvatarService::class.java)
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        const val MAX_PIXELS = 16_000_000L
        const val MAX_SIDE = 64
    }
}
