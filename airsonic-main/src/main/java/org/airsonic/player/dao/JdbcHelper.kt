package org.airsonic.player.dao

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.Objects
import javax.sql.DataSource

/** Framework-neutral JDBC facade for queries, updates, batches, and generated keys. */
class JdbcHelper(dataSource: DataSource, transactionManager: TransactionManager) {
    private val dataSource: DataSource = Objects.requireNonNull(dataSource, "dataSource")
    private val transactionManager: TransactionManager = Objects.requireNonNull(transactionManager, "transactionManager")

    constructor(dataSource: DataSource) : this(dataSource, TransactionManager(dataSource))

    fun getDataSource(): DataSource = dataSource

    fun getTransactionManager(): TransactionManager = transactionManager

    fun update(sql: String, vararg arguments: Any?): Int = withStatement(sql, "update") { connection ->
        val statement = connection.prepareStatement(sql)
        try {
            JdbcParameterBinder.bind(statement, arguments)
            statement.executeUpdate()
        } finally {
            closeStatement(statement)
        }
    }

    fun namedUpdate(sql: String, arguments: Map<String, Any?>?): Int {
        val parsed = parseNamedSql(sql, arguments)
        return update(parsed.sql, *parsed.parameters.toTypedArray())
    }

    fun batchedUpdate(sql: String, batchArguments: Collection<out Array<out Any?>>?, batchSize: Int): Int {
        val batches = batchArguments ?: return 0
        if (batches.isEmpty()) return 0
        return withStatement(sql, "batch update") { connection ->
            val statement = connection.prepareStatement(sql)
            try {
                var count = 0
                var total = 0
                for (arguments in batches) {
                    JdbcParameterBinder.bind(statement, arguments)
                    statement.addBatch()
                    count++
                    if (count % batchSize == 0) total += executeBatch(statement)
                }
                if (count % batchSize != 0) total += executeBatch(statement)
                total
            } finally {
                closeStatement(statement)
            }
        }
    }

    fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg arguments: Any?): List<T> =
        withStatement(sql, "query") { connection ->
            val statement = connection.prepareStatement(sql)
            var resultSet: ResultSet? = null
            try {
                JdbcParameterBinder.bind(statement, arguments)
                resultSet = statement.executeQuery()
                val results = ArrayList<T>()
                var rowNumber = 0
                while (resultSet.next()) results.add(rowMapper.mapRow(resultSet, rowNumber++))
                results
            } finally {
                closeResultSet(resultSet)
                closeStatement(statement)
            }
        }

    fun <T> namedQuery(sql: String, rowMapper: RowMapper<T>, arguments: Map<String, Any?>?): List<T> {
        val parsed = parseNamedSql(sql, arguments)
        return query(parsed.sql, rowMapper, *parsed.parameters.toTypedArray())
    }

    fun <T> queryForList(sql: String, elementType: Class<T>, vararg arguments: Any?): List<T> =
        query(sql, RowMapper { resultSet, _ -> convertRowValue(resultSet.getObject(1), elementType) }, *arguments)

    fun <T> namedQueryForList(sql: String, elementType: Class<T>, arguments: Map<String, Any?>?): List<T> {
        val parsed = parseNamedSql(sql, arguments)
        return queryForList(parsed.sql, elementType, *parsed.parameters.toTypedArray())
    }

    fun insertAndReturnKey(sql: String, keyColumn: String?, vararg arguments: Any?): Int? =
        withStatement(sql, "insert") { connection ->
            val statement = if (!keyColumn.isNullOrBlank()) {
                connection.prepareStatement(sql, arrayOf(keyColumn))
            } else {
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
            }
            var keys: ResultSet? = null
            try {
                JdbcParameterBinder.bind(statement, arguments)
                statement.executeUpdate()
                keys = statement.generatedKeys
                if (keys != null && keys.next()) {
                    val key = keys.getObject(1)
                    when (key) {
                        is Number -> key.toInt()
                        null -> null
                        else -> key.toString().toInt()
                    }
                } else null
            } finally {
                closeResultSet(keys)
                closeStatement(statement)
            }
        }

    private fun executeBatch(statement: PreparedStatement): Int = statement.executeBatch().sumOf { result ->
        when {
            result >= 0 -> result
            result == Statement.SUCCESS_NO_INFO -> 1
            else -> 0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> convertRowValue(value: Any?, type: Class<T>): T = JdbcParameterBinder.convert(value, type) as T

    private inline fun <T> withStatement(sql: String, operation: String, action: (Connection) -> T): T {
        var connection: Connection? = null
        try {
            connection = transactionManager.getConnection()
            return action(connection)
        } catch (failure: SQLException) {
            throw RuntimeException("Failed to execute $operation: $sql", failure)
        } finally {
            transactionManager.releaseConnection(connection)
        }
    }

    private fun closeStatement(statement: Statement?) {
        try {
            statement?.close()
        } catch (failure: SQLException) {
            LOG.warn("Failed to close statement", failure)
        }
    }

    private fun closeResultSet(resultSet: ResultSet?) {
        try {
            resultSet?.close()
        } catch (failure: SQLException) {
            LOG.warn("Failed to close result set", failure)
        }
    }

    class ParsedSql(@JvmField val sql: String, @JvmField val parameters: List<Any?>)

    companion object {
        private val LOG = LoggerFactory.getLogger(JdbcHelper::class.java)

        @JvmStatic
        fun convertToDBType(value: Any?): Any? = when (value) {
            is Instant -> Timestamp.from(value)
            is Enum<*> -> value.name
            is Path -> value.toString()
            else -> value
        }

        @JvmStatic
        fun parseNamedSql(sql: String?, arguments: Map<String, Any?>?): ParsedSql =
            NamedSqlParser.parse(sql, arguments)
    }
}
