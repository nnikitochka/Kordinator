package org.airsonic.player.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthFrameworkTest {

    @Test
    void statusEqualityAndComparisons() {
        Status s1 = new Status("up");
        Status s2 = Status.UP;
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertEquals("UP", s1.getCode());
        assertEquals("UP", s1.toString());
        assertNotEquals(Status.UP, Status.DOWN);
        assertTrue(Status.DOWN.compareTo(Status.UP) < 0);
    }

    @Test
    void healthBuilderPatterns() {
        Health up = Health.up().withDetail("k1", "v1").withDetail("k2", 123).build();
        assertEquals(Status.UP, up.getStatus());
        assertEquals("v1", up.getDetails().get("k1"));
        assertEquals(123, up.getDetails().get("k2"));

        Health down = Health.down(new IllegalArgumentException("invalid argument")).build();
        assertEquals(Status.DOWN, down.getStatus());
        assertTrue(down.getDetails().get("error").toString().contains("invalid argument"));

        Health outOfService = Health.outOfService().withDetails(Map.of("reason", "maintenance")).build();
        assertEquals(Status.OUT_OF_SERVICE, outOfService.getStatus());
        assertEquals("maintenance", outOfService.getDetails().get("reason"));

        Health unknown = Health.unknown().build();
        assertEquals(Status.UNKNOWN, unknown.getStatus());
    }

    @Test
    void statusAggregatorUsesConfiguredOrder() {
        StatusAggregator aggregator = new SimpleStatusAggregator("FATAL", "DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN");

        assertEquals(new Status("FATAL"), aggregator.getAggregateStatus(Set.of(Status.UP, Status.DOWN, new Status("FATAL"))));
        assertEquals(Status.DOWN, aggregator.getAggregateStatus(Set.of(Status.UP, Status.DOWN)));
        assertEquals(Status.OUT_OF_SERVICE, aggregator.getAggregateStatus(Set.of(Status.UP, Status.OUT_OF_SERVICE)));
        assertEquals(Status.UP, aggregator.getAggregateStatus(Set.of(Status.UP)));
        assertEquals(Status.UNKNOWN, aggregator.getAggregateStatus(Set.of()));
    }

    @Test
    void registryAndCompositeOperations() {
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        HealthIndicator ping = new PingHealthIndicator();
        registry.registerContributor("ping", ping);

        assertSame(ping, registry.getContributor("ping"));
        assertEquals(Status.UP, ping.health().getStatus());

        CompositeHealthContributor composite = CompositeHealthContributor.fromMap(Map.of(
                "sub1", (HealthIndicator) () -> Health.up().build(),
                "sub2", (HealthIndicator) () -> Health.down().build()
        ));
        assertNotNull(composite.getContributor("sub1"));
        assertNotNull(composite.getContributor("sub2"));
        assertNull(composite.getContributor("missing"));

        registry.registerContributor("composite", composite);
        assertNotNull(registry.getContributor("composite"));

        HealthContributor removed = registry.unregisterContributor("ping");
        assertSame(ping, removed);
        assertNull(registry.getContributor("ping"));
    }

    @Test
    void databaseHealthIndicatorChecksConnectionSuccess() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(anyInt())).thenReturn(true);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("MockDB 1.0");

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(ds, 2);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("MockDB 1.0", health.getDetails().get("database"));
        assertEquals("isValid()", health.getDetails().get("validationQuery"));
    }

    @Test
    void databaseHealthIndicatorHandlesConnectionFailure() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection timeout"));

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(ds, 2);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("connection timeout"));
    }

    @Test
    void databaseHealthIndicatorHandlesInvalidConnection() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.isValid(anyInt())).thenReturn(false);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(ds, 2);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("error").toString().contains("Database connection validation failed"));
    }

    @Test
    void diskSpaceHealthIndicator(@TempDir Path tempDir) {
        File folder = tempDir.toFile();
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(folder, 1024); // 1KB threshold
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("exists"));
        assertNotNull(health.getDetails().get("free"));
        assertNotNull(health.getDetails().get("total"));

        File nonExistent = new File(folder, "non-existent-dir-xyz");
        DiskSpaceHealthIndicator failingIndicator = new DiskSpaceHealthIndicator(nonExistent);
        Health downHealth = failingIndicator.health();

        assertEquals(Status.DOWN, downHealth.getStatus());
        assertEquals(false, downHealth.getDetails().get("exists"));
    }
}
