package su.nnedition.kord.health

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyInt
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.util.Set
import javax.sql.DataSource

class HealthFrameworkTest {
    @Test
    fun statusEqualityAndComparisons() {
        val first = Status("up")
        val second = Status.UP
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals("UP", first.code)
        assertEquals("UP", first.toString())
        assertNotEquals(Status.UP, Status.DOWN)
        assertTrue(Status.DOWN.compareTo(Status.UP) < 0)
    }

    @Test
    fun healthBuilderPatterns() {
        val up = Health.up().withDetail("k1", "v1").withDetail("k2", 123).build()
        assertEquals(Status.UP, up.status)
        assertEquals("v1", up.details["k1"])
        assertEquals(123, up.details["k2"])

        val down = Health.down(IllegalArgumentException("invalid argument")).build()
        assertEquals(Status.DOWN, down.status)
        assertTrue(down.details["error"].toString().contains("invalid argument"))

        val outOfService = Health.outOfService().withDetails(mapOf("reason" to "maintenance")).build()
        assertEquals(Status.OUT_OF_SERVICE, outOfService.status)
        assertEquals("maintenance", outOfService.details["reason"])
        assertEquals(Status.UNKNOWN, Health.unknown().build().status)
    }

    @Test
    fun statusAggregatorUsesConfiguredOrder() {
        val aggregator = SimpleStatusAggregator("FATAL", "DOWN", "OUT_OF_SERVICE", "UP", "UNKNOWN")
        assertEquals(Status("FATAL"), aggregator.getAggregateStatus(setOf(Status.UP, Status.DOWN, Status("FATAL"))))
        assertEquals(Status.DOWN, aggregator.getAggregateStatus(setOf(Status.UP, Status.DOWN)))
        assertEquals(Status.OUT_OF_SERVICE, aggregator.getAggregateStatus(setOf(Status.UP, Status.OUT_OF_SERVICE)))
        assertEquals(Status.UP, aggregator.getAggregateStatus(setOf(Status.UP)))
        assertEquals(Status.UNKNOWN, aggregator.getAggregateStatus(emptySet()))
        assertEquals(Status.UNKNOWN, aggregator.getAggregateStatus(null))
    }

    @Test
    fun registryAndCompositeOperations() {
        val registry = DefaultHealthContributorRegistry()
        val ping = PingHealthIndicator()
        registry.registerContributor("ping", ping)
        assertSame(ping, registry.getContributor("ping"))
        assertEquals(Status.UP, ping.health().status)

        val composite = CompositeHealthContributor.fromMap(
            mapOf("sub1" to HealthIndicator { Health.up().build() }, "sub2" to HealthIndicator { Health.down().build() }),
        )
        assertNotNull(composite.getContributor("sub1"))
        assertNotNull(composite.getContributor("sub2"))
        assertNull(composite.getContributor("missing"))
        registry.registerContributor("composite", composite)
        assertNotNull(registry.getContributor("composite"))
        assertSame(ping, registry.unregisterContributor("ping"))
        assertNull(registry.getContributor("ping"))
    }

    @Test
    fun databaseHealthIndicatorChecksConnectionSuccess() {
        val source = Mockito.mock(DataSource::class.java)
        val connection = Mockito.mock(Connection::class.java)
        val metadata = Mockito.mock(DatabaseMetaData::class.java)
        `when`(source.connection).thenReturn(connection)
        `when`(connection.isValid(anyInt())).thenReturn(true)
        `when`(connection.metaData).thenReturn(metadata)
        `when`(metadata.databaseProductName).thenReturn("MockDB 1.0")

        val health = DatabaseHealthIndicator(source, 2).health()

        assertEquals(Status.UP, health.status)
        assertEquals("MockDB 1.0", health.details["database"])
        assertEquals("isValid()", health.details["validationQuery"])
    }

    @Test
    fun databaseHealthIndicatorHandlesConnectionFailure() {
        val source = Mockito.mock(DataSource::class.java)
        `when`(source.connection).thenThrow(SQLException("connection timeout"))

        val health = DatabaseHealthIndicator(source, 2).health()

        assertEquals(Status.DOWN, health.status)
        assertTrue(health.details["error"].toString().contains("connection timeout"))
    }

    @Test
    fun databaseHealthIndicatorHandlesInvalidConnection() {
        val source = Mockito.mock(DataSource::class.java)
        val connection = Mockito.mock(Connection::class.java)
        `when`(source.connection).thenReturn(connection)
        `when`(connection.isValid(anyInt())).thenReturn(false)

        val health = DatabaseHealthIndicator(source, 2).health()

        assertEquals(Status.DOWN, health.status)
        assertTrue(health.details["error"].toString().contains("Database connection validation failed"))
    }

    @Test
    fun diskSpaceHealthIndicator(@TempDir tempDir: Path) {
        val indicator = DiskSpaceHealthIndicator(tempDir.toFile(), 1024)
        val health = indicator.health()
        assertEquals(Status.UP, health.status)
        assertEquals(true, health.details["exists"])
        assertNotNull(health.details["free"])
        assertNotNull(health.details["total"])

        val failingHealth = DiskSpaceHealthIndicator(File(tempDir.toFile(), "non-existent-dir-xyz")).health()
        assertEquals(Status.DOWN, failingHealth.status)
        assertEquals(false, failingHealth.details["exists"])
    }
}
