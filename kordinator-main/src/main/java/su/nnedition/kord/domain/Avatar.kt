package su.nnedition.kord.domain

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/** An icon representing a user. */
class Avatar(
    val id: Int,
    val name: String,
    val createdDate: Instant?,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val path: Path
) {
    constructor(
        id: Int,
        name: String,
        createdDate: Instant?,
        mimeType: String,
        width: Int,
        height: Int,
        path: String
    ) : this(id, name, createdDate, mimeType, width, height, Paths.get(path))
}
