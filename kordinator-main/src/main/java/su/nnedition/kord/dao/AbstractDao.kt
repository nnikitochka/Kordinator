package su.nnedition.kord.dao

import com.google.common.base.CaseFormat
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.HashMap
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Function

/** Abstract superclass for all DAO implementations, backed by [JdbcHelper]. */
open class AbstractDao {
    private var jdbcHelper: JdbcHelper? = null
    private val insertSqls = ConcurrentHashMap<String, String>()
    private val insertGeneratedKeys = ConcurrentHashMap<String, String>()
    private val insertColumnLists = ConcurrentHashMap<String, List<String>>()
    private val methods = ConcurrentHashMap<String, Map<String, MethodHandle>>()

    protected constructor()

    protected constructor(jdbcHelper: JdbcHelper) {
        this.jdbcHelper = Objects.requireNonNull(jdbcHelper, "jdbcHelper")
    }

    open fun getJdbcHelper(): JdbcHelper = jdbcHelper
        ?: throw IllegalStateException("JdbcHelper is not initialized for ${javaClass.name}")

    protected open fun <T> inTransaction(action: TransactionManager.TransactionCallback<T>): T =
        getJdbcHelper().getTransactionManager().execute(action)

    protected open fun <T> inTransaction(
        isolationLevel: Int,
        action: TransactionManager.TransactionCallback<T>
    ): T = getJdbcHelper().getTransactionManager().execute(
        TransactionManager.TransactionDefinition(TransactionManager.PROPAGATION_REQUIRED, isolationLevel), action
    )

    protected open fun update(sql: String, vararg args: Any?): Int {
        val startedAt = System.nanoTime()
        LOG.trace("Executing query: [{}]", sql)
        val updated = getJdbcHelper().update(sql, *convertToDBTypes(args)!!)
        LOG.trace("Updated {} rows", updated)
        log(sql, startedAt)
        return updated
    }

    protected open fun namedUpdate(sql: String, args: Map<String, Any?>): Int {
        val startedAt = System.nanoTime()
        LOG.trace("Executing query: [{}]", sql)
        val updated = getJdbcHelper().namedUpdate(sql, convertToDBTypes(args))
        LOG.trace("Updated {} rows", updated)
        log(sql, startedAt)
        return updated
    }

    protected open fun batchedUpdate(sql: String, batchArgs: Collection<Array<Any?>>): Int {
        val startedAt = System.nanoTime()
        val batchSize = 30000 / (batchArgs.stream().findAny().map { it.size }.orElse(1))
        LOG.trace("Executing query: [{}]", sql)
        val converted = batchArgs.map { convertToDBTypes(it)!! }
        val tally = getJdbcHelper().batchedUpdate(sql, converted, batchSize)
        LOG.trace("Updated {} rows", tally)
        log(sql, startedAt)
        return tally
    }

    protected open fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
        val startedAt = System.nanoTime()
        val result = getJdbcHelper().query(sql, rowMapper, *convertToDBTypes(args)!!)
        log(sql, startedAt)
        return result
    }

    protected open fun <T> namedQuery(sql: String, rowMapper: RowMapper<T>, args: Map<String, Any?>): List<T> {
        val startedAt = System.nanoTime()
        val result = getJdbcHelper().namedQuery(sql, rowMapper, convertToDBTypes(args))
        log(sql, startedAt)
        return result
    }

    protected open fun <T> queryForTypes(sql: String, type: Class<T>, vararg args: Any?): List<T> {
        val startedAt = System.nanoTime()
        val result = getJdbcHelper().queryForList(sql, type, *convertToDBTypes(args)!!)
        log(sql, startedAt)
        return result
    }

    protected open fun <T> namedQueryForTypes(sql: String, type: Class<T>, args: Map<String, Any?>): List<T> {
        val startedAt = System.nanoTime()
        val result = getJdbcHelper().namedQueryForList(sql, type, convertToDBTypes(args))
        log(sql, startedAt)
        return result
    }

    open fun queryForStrings(sql: String, vararg args: Any?): List<String> = queryForTypes(sql, String::class.java, *args)

    protected open fun queryForInts(sql: String, vararg args: Any?): List<Int> = queryForTypes(sql, Int::class.javaObjectType, *args)

    protected open fun namedQueryForStrings(sql: String, args: Map<String, Any?>): List<String> =
        namedQueryForTypes(sql, String::class.java, args)

    open fun queryForInt(sql: String, defaultValue: Int?, vararg args: Any?): Int? =
        queryForTypes(sql, Int::class.javaObjectType, *args).filterNotNull().firstOrNull() ?: defaultValue

    protected open fun namedQueryForInt(sql: String, defaultValue: Int?, args: Map<String, Any?>): Int? =
        namedQueryForTypes(sql, Int::class.javaObjectType, args).filterNotNull().firstOrNull() ?: defaultValue

    protected open fun queryForInstant(sql: String, defaultValue: Instant?, vararg args: Any?): Instant? =
        queryForTypes(sql, Timestamp::class.java, *args).filterNotNull().firstOrNull()?.toInstant() ?: defaultValue

    protected open fun queryForLong(sql: String, defaultValue: Long?, vararg args: Any?): Long? =
        queryForTypes(sql, Long::class.javaObjectType, *args).filterNotNull().firstOrNull() ?: defaultValue

    protected open fun queryForDouble(sql: String, defaultValue: Double?, vararg args: Any?): Double? =
        queryForTypes(sql, Double::class.javaObjectType, *args).filterNotNull().firstOrNull() ?: defaultValue

    protected open fun <T> queryOne(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): T? =
        query(sql, rowMapper, *args).firstOrNull()

    protected open fun <T> namedQueryOne(sql: String, rowMapper: RowMapper<T>, args: Map<String, Any?>): T? =
        namedQuery(sql, rowMapper, args).firstOrNull()

    @Throws(Exception::class)
    protected open fun registerInserts(table: String, generatedKey: String?, columns: List<String>, klazz: Class<*>) {
        getJdbcHelper()
        val sql = "insert into $table (${columns.joinToString(", ")}) values (${questionMarks(columns.joinToString(", "))})"
        insertSqls[table] = sql
        insertColumnLists[table] = ArrayList(columns)
        if (generatedKey != null) insertGeneratedKeys[table] = generatedKey

        val fields = HashMap<String, Field>()
        for (field in klazz.declaredFields) {
            fields.putIfAbsent(field.name, field)
            val annotation = field.getAnnotation(Column::class.java)
            if (annotation != null) fields.putIfAbsent(annotation.value, field)
        }

        val privateLookup = MethodHandles.privateLookupIn(klazz, LOOKUP)
        val tableMethods = ConcurrentHashMap<String, MethodHandle>()
        columns.parallelStream().forEach { column ->
            var field: Field? = null
            val alreadyLooked = HashSet<String>()
            for (transform in COLUMN_NAME_TRANSFORMS) {
                val lookupColumn = transform.apply(column)
                if (alreadyLooked.add(lookupColumn)) {
                    field = fields[lookupColumn]
                    if (field != null) {
                        LOG.debug("Found suitable field {} (as {}) in class {} for table {} column {}", field.name, lookupColumn, klazz.name, table, column)
                        break
                    }
                }
            }
            if (field == null) LOG.error("Could not locate a suitable field in class {} for table {} column {}", klazz.name, table, column)
            tableMethods[column] = privateLookup.unreflectGetter(field!!)
        }
        methods[table] = tableMethods
    }

    protected open fun insert(table: String, obj: Any): Int? {
        val columns = insertColumnLists[table]!!
        val sql = insertSqls[table]!!
        val generatedKey = insertGeneratedKeys[table]
        val tableMethods = methods[table]!!
        val values = arrayOfNulls<Any?>(columns.size)
        for (index in columns.indices) {
            val method = tableMethods[columns[index]]!!
            try {
                values[index] = convertToDBType(method.invoke(obj))
            } catch (failure: Throwable) {
                throw RuntimeException("Failed to read property for column ${columns[index]} on table $table", failure)
            }
        }

        val helper = getJdbcHelper()
        return if (generatedKey == null) {
            helper.update(sql, *values)
            null
        } else {
            helper.insertAndReturnKey(sql, generatedKey, *values)
        }
    }

    private fun log(sql: String, startTimeNano: Long) {
        val millis = (System.nanoTime() - startTimeNano) / 1_000_000L
        if (millis > TimeUnit.SECONDS.toMillis(2L)) LOG.debug("{} ms:  {}", millis, sql)
    }

    annotation class Column(val value: String)

    companion object {
        private val LOG = LoggerFactory.getLogger(AbstractDao::class.java)
        private val LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()
        private val COLUMN_NAME_TRANSFORMS: List<Function<String, String>> = listOf(
            Function.identity(),
            Function { CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, it) },
            Function { CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, it).lowercase() }
        )

        @JvmStatic
        protected fun questionMarks(columns: String): String {
            val numberOfColumns = StringUtils.countMatches(columns, ",") + 1
            return StringUtils.repeat("?", ", ", numberOfColumns)
        }

        @JvmStatic
        protected fun prefix(columns: String, prefix: String): String =
            columns.split(", ").joinToString(", ") { "$prefix.$it" }

        @JvmStatic
        protected fun convertToDBTypes(args: Array<out Any?>?): Array<Any?>? = args?.map(::convertToDBType)?.toTypedArray()

        @JvmStatic
        protected fun convertToDBTypes(args: Map<String, Any?>?): Map<String, Any?>? =
            args?.mapValues { convertToDBType(it.value) }

        @JvmStatic
        protected fun convertToDBType(value: Any?): Any? = when (value) {
            is Instant -> Timestamp.from(value)
            is Enum<*> -> value.name
            is Path -> value.toString()
            else -> value
        }
    }
}
