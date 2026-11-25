package su.nnedition.kord.dao

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JdbcHelperNamedSqlTest {
    @Test
    fun `named parameters expand collections and primitive arrays`() {
        val parsed = JdbcHelper.parseNamedSql(
            "select value::text from item where id in (:ids) and state in (:states)",
            mapOf("ids" to intArrayOf(4, 9), "states" to listOf("ready", "queued"))
        )

        assertEquals("select value::text from item where id in (?, ?) and state in (?, ?)", parsed.sql)
        assertEquals(listOf(4, 9, "ready", "queued"), parsed.parameters)
    }

    @Test
    fun `null SQL maps to empty query and parameters`() {
        val parsed = JdbcHelper.parseNamedSql(null, null)

        assertEquals("", parsed.sql)
        assertEquals(emptyList(), parsed.parameters)
    }
}
