package su.nnedition.kord.dao

import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.nio.file.Path

/** Converts domain values and binds them to JDBC statements. */
internal object JdbcParameterBinder {
    @Throws(SQLException::class)
    fun bind(statement: PreparedStatement, arguments: Array<out Any?>?) {
        if (arguments == null) return
        arguments.forEachIndexed { index, argument ->
            val value = JdbcHelper.convertToDBType(argument)
            when (value) {
                null -> statement.setNull(index + 1, Types.NULL)
                is Timestamp -> statement.setTimestamp(index + 1, value)
                is java.sql.Date -> statement.setDate(index + 1, value)
                else -> statement.setObject(index + 1, value)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> convert(raw: Any?, type: Class<T>): T? {
        if (raw == null) return null
        if (type.isInstance(raw)) return raw as T
        if (type == String::class.java) return raw.toString() as T
        if (type == Int::class.javaObjectType || type == Int::class.javaPrimitiveType) {
            return (if (raw is Number) raw.toInt() else raw.toString().trim().toInt()) as T
        }
        if (type == Long::class.javaObjectType || type == Long::class.javaPrimitiveType) {
            return (if (raw is Number) raw.toLong() else raw.toString().trim().toLong()) as T
        }
        if (type == Double::class.javaObjectType || type == Double::class.javaPrimitiveType) {
            return (if (raw is Number) raw.toDouble() else raw.toString().trim().toDouble()) as T
        }
        if (type == Timestamp::class.java && raw is java.util.Date) return Timestamp(raw.time) as T
        if (type == Instant::class.java) {
            if (raw is Timestamp) return raw.toInstant() as T
            if (raw is java.util.Date) return raw.toInstant() as T
        }
        throw IllegalArgumentException("Cannot convert value of type ${raw.javaClass} to $type")
    }
}
