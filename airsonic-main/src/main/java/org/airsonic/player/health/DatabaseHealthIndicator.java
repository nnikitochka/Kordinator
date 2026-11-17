package org.airsonic.player.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Objects;

/**
 * Health indicator verifying database connectivity and responsiveness.
 */
public class DatabaseHealthIndicator implements HealthIndicator {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final DataSource dataSource;
    private final int timeoutSeconds;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this(dataSource, 5);
    }

    public DatabaseHealthIndicator(DataSource dataSource, int timeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(timeoutSeconds)) {
                return Health.down().withDetail("error", "Database connection validation failed").build();
            }
            DatabaseMetaData metaData = connection.getMetaData();
            return Health.up()
                    .withDetail("database", metaData.getDatabaseProductName())
                    .withDetail("validationQuery", "isValid()")
                    .build();
        } catch (Exception e) {
            LOG.warn("Database health check failed", e);
            return Health.down(e).build();
        }
    }
}
