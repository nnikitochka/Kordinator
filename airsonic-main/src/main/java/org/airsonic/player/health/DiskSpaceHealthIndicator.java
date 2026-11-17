package org.airsonic.player.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Health indicator verifying available disk space for Airsonic home directory.
 */
public class DiskSpaceHealthIndicator implements HealthIndicator {
    private static final Logger LOG = LoggerFactory.getLogger(DiskSpaceHealthIndicator.class);

    private final File path;
    private final long thresholdBytes;

    public DiskSpaceHealthIndicator(Path path) {
        this(Objects.requireNonNull(path, "path").toFile());
    }

    public DiskSpaceHealthIndicator(Path path, long thresholdBytes) {
        this(Objects.requireNonNull(path, "path").toFile(), thresholdBytes);
    }

    public DiskSpaceHealthIndicator(File path) {
        this(path, 10L * 1024 * 1024); // 10 MB default threshold
    }

    public DiskSpaceHealthIndicator(File path, long thresholdBytes) {
        this.path = Objects.requireNonNull(path, "path");
        this.thresholdBytes = thresholdBytes;
    }

    @Override
    public Health health() {
        try {
            if (!path.exists()) {
                return Health.down()
                        .withDetail("path", path.getAbsolutePath())
                        .withDetail("exists", false)
                        .build();
            }
            long free = path.getUsableSpace();
            long total = path.getTotalSpace();
            if (free >= thresholdBytes) {
                return Health.up()
                        .withDetail("total", total)
                        .withDetail("free", free)
                        .withDetail("threshold", thresholdBytes)
                        .withDetail("exists", true)
                        .build();
            } else {
                return Health.down()
                        .withDetail("total", total)
                        .withDetail("free", free)
                        .withDetail("threshold", thresholdBytes)
                        .withDetail("exists", true)
                        .build();
            }
        } catch (Exception e) {
            LOG.warn("Disk space health check failed for path: {}", path, e);
            return Health.down(e).build();
        }
    }
}
