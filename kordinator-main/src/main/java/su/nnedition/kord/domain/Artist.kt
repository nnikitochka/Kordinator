package su.nnedition.kord.domain

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class Artist @JvmOverloads constructor(
    var id: Int = 0,
    var name: String? = null,
    albumCount: Int = 0,
    var lastScanned: Instant? = null,
    var isPresent: Boolean = false,
    var folderId: Int? = null,
    var art: CoverArt? = null
) {
    private val albumCountValue = AtomicInteger(albumCount)

    var albumCount: Int
        get() = albumCountValue.get()
        set(value) {
            albumCountValue.set(value)
        }
}
