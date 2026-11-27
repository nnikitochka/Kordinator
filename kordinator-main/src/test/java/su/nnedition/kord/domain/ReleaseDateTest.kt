package su.nnedition.kord.domain

import org.junit.jupiter.api.Test
import su.nnedition.kord.model.ReleaseDate
import kotlin.test.*

class ReleaseDateTest {
    @Test fun acceptsOnlyCalendarDatesWithKnownPrecision() {
        mapOf("2026" to "2026", "2026.02" to "2026-02", "2026.02.27" to "2026-02-27",
            "2024-02-29" to "2024-02-29", "0001-01" to "0001-01").forEach { (raw, expected) ->
            assertEquals(expected, ReleaseDate.parse(raw)?.toString(), raw)
        }
        listOf("", "0000", "2026-02-29", "2026-13", "2026-02-30", "2026-00-01", "2026-02-00",
            "2026.02-27", "2026-2-27", "27-02-2026", "20260227", "2026-02-27T10:00:00").forEach {
            assertNull(ReleaseDate.parse(it), it)
        }
    }

    @Test fun votesRecursivelyAndStopsAtTiesOrMissingComponents() {
        fun date(vararg values: String?) = ReleaseDate.mostCommon(values.map(ReleaseDate::parse))?.toString()
        assertEquals("2026-02-27", date("2025-01-01", "2026-02-27", "2026-02-27", "2026-03-28"))
        assertEquals("2026-02", date("2026-02-27", "2026-02-28", "2025"))
        assertEquals("2026", date("2026-02-27", "2026-03-27", "2025"))
        assertNull(date("2025-02-27", "2026-02-27"))
        assertEquals("2026-02-27", date("2026", "2026-02", "2026-02-27", null))
        assertNull(date(null, "bad"))
    }
}
