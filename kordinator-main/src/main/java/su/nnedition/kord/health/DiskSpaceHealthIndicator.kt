package su.nnedition.kord.health

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

/** Health indicator verifying available disk space for the application home directory. */
class DiskSpaceHealthIndicator : HealthIndicator {
    private val path: File
    private val thresholdBytes: Long

    constructor(path: Path) : this(path.toFile())

    constructor(path: Path, thresholdBytes: Long) : this(path.toFile(), thresholdBytes)

    constructor(path: File) : this(path, DEFAULT_THRESHOLD_BYTES)

    constructor(path: File, thresholdBytes: Long) {
        this.path = path
        this.thresholdBytes = thresholdBytes
    }

    override fun health(): Health = try {
        if (!path.exists()) {
            Health.down()
                .withDetail("path", path.absolutePath)
                .withDetail("exists", false)
                .build()
        } else {
            val free = path.usableSpace
            val total = path.totalSpace
            val builder = if (free >= thresholdBytes) Health.up() else Health.down()
            builder
                .withDetail("total", total)
                .withDetail("free", free)
                .withDetail("threshold", thresholdBytes)
                .withDetail("exists", true)
                .build()
        }
    } catch (failure: Exception) {
        log.warn("Disk space health check failed for path: {}", path, failure)
        Health.down(failure).build()
    }

    companion object {
        private const val DEFAULT_THRESHOLD_BYTES = 10L * 1024 * 1024
        private val log = LoggerFactory.getLogger(DiskSpaceHealthIndicator::class.java)
    }
}
