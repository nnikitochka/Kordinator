package org.airsonic.player.service

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class StandaloneLibrary(val id: Long, val name: String, val rootPath: String, val createdAt: Instant)

/** Owns music library roots for the new standalone API. */
class StandaloneLibraryService(
    private val dataSource: DataSource,
) {
    fun create(name: String, root: Path): StandaloneLibrary {
        val normalizedName = name.trim()
        require(normalizedName.length in 1..120) { "Library name must contain between 1 and 120 characters" }
        val canonicalPath = root.toRealPath()
        require(Files.isDirectory(canonicalPath) && Files.isReadable(canonicalPath)) {
            "Library path must be a readable directory"
        }
        val now = Instant.now()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO music_library (name, root_path, created_at) VALUES (?, ?, ?)",
                arrayOf("id"),
            ).use { statement ->
                statement.setString(1, normalizedName)
                statement.setString(2, canonicalPath.toString())
                statement.setTimestamp(3, Timestamp.from(now))
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Database did not return a library ID" }
                    return StandaloneLibrary(keys.getLong(1), normalizedName, canonicalPath.toString(), now)
                }
            }
        }
    }

    fun list(): List<StandaloneLibrary> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT id, name, root_path, created_at FROM music_library ORDER BY name, id",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            StandaloneLibrary(
                                rows.getLong("id"),
                                rows.getString("name"),
                                rows.getString("root_path"),
                                rows.getTimestamp("created_at").toInstant(),
                            ),
                        )
                    }
                }
            }
        }
    }
}
