package org.airsonic.player.dao

import java.lang.reflect.Array
import java.util.Collections
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Expands named parameters, including collection and array values, into positional SQL. */
internal object NamedSqlParser {
    private val namedParameter = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)")

    fun parse(sql: String?, arguments: Map<String, Any?>?): JdbcHelper.ParsedSql {
        if (sql == null) return JdbcHelper.ParsedSql("", Collections.emptyList())
        val parameters = arguments ?: emptyMap()
        val matcher = namedParameter.matcher(sql)
        val expandedSql = StringBuilder()
        val values = mutableListOf<Any?>()

        while (matcher.find()) {
            val value = parameters[matcher.group(1)]
            when {
                value is Collection<*> -> appendCollection(matcher, expandedSql, values, value)
                value != null && value.javaClass.isArray -> appendArray(matcher, expandedSql, values, value)
                else -> {
                    values.add(value)
                    matcher.appendReplacement(expandedSql, "?")
                }
            }
        }
        matcher.appendTail(expandedSql)
        return JdbcHelper.ParsedSql(expandedSql.toString(), values)
    }

    private fun appendCollection(
        matcher: Matcher,
        sql: StringBuilder,
        values: MutableList<Any?>,
        collection: Collection<*>
    ) {
        if (collection.isEmpty()) {
            matcher.appendReplacement(sql, "NULL")
            return
        }
        val placeholders = collection.joinToString(", ") { value ->
            values.add(value)
            "?"
        }
        matcher.appendReplacement(sql, Matcher.quoteReplacement(placeholders))
    }

    private fun appendArray(matcher: Matcher, sql: StringBuilder, values: MutableList<Any?>, array: Any) {
        val length = Array.getLength(array)
        if (length == 0) {
            matcher.appendReplacement(sql, "NULL")
            return
        }
        val placeholders = (0 until length).joinToString(", ") { index ->
            values.add(Array.get(array, index))
            "?"
        }
        matcher.appendReplacement(sql, Matcher.quoteReplacement(placeholders))
    }
}
