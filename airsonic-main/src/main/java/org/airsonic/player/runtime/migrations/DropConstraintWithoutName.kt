package org.airsonic.player.runtime.migrations

import java.sql.SQLException
import liquibase.change.ColumnConfig
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.DatabaseException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.DropForeignKeyConstraintStatement
import liquibase.statement.core.DropUniqueConstraintStatement
import org.apache.commons.lang3.StringUtils

class DropConstraintWithoutName : CustomSqlChange {
    private var normalizedTableName: String? = null
    private var normalizedConstraintType: String? = null
    private var selectedColumns: List<String>? = null

    fun setTableName(value: String?) {
        normalizedTableName = StringUtils.lowerCase(value)
    }

    fun setConstraintType(value: String?) {
        normalizedConstraintType = StringUtils.lowerCase(value)
    }

    fun setColumns(value: String?) {
        selectedColumns = StringUtils.split(value, ",")
            ?.mapNotNull { StringUtils.trimToNull(it)?.let(StringUtils::lowerCase) }
    }

    override fun getConfirmationMessage() = "Dropped constraint"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database): ValidationErrors = ValidationErrors()
        .checkRequiredField("tableName", normalizedTableName)
        .checkRequiredField("constraintType", normalizedConstraintType)
        .checkRequiredField("columns", selectedColumns)

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement>? {
        val columns = selectedColumns.orEmpty()
        val table = normalizedTableName
        val type = normalizedConstraintType
        if (columns.isEmpty() || StringUtils.isBlank(type) || StringUtils.isBlank(table)) {
            throw CustomChangeException("One of the parameters is blank")
        }

        var constraintName: String? = null
        val connection = database.connection as? JdbcConnection
        if (connection != null) {
            val placeholders = columns.joinToString(separator = "?") { "?" }
            val sql = "select * from information_schema.table_constraints tc, information_schema.key_column_usage cu " +
                "WHERE cu.constraint_name=tc.constraint_name and lower(tc.table_name)=? " +
                "and lower(tc.constraint_type)=? and lower(cu.column_name) in ($placeholders)"
            try {
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, table)
                    statement.setString(2, type)
                    columns.forEachIndexed { index, column -> statement.setString(index + 3, column) }
                    statement.executeQuery().use { rows ->
                        while (rows.next()) constraintName = rows.getString("constraint_name")
                    }
                }
            } catch (exception: DatabaseException) {
                throw CustomChangeException(exception)
            } catch (exception: SQLException) {
                throw CustomChangeException(exception)
            }
        }

        val foundConstraint = constraintName ?: throw CustomChangeException("No constraint found to drop")
        val columnNames = columns.joinToString(",")
        return when (type) {
            "foreign key" -> arrayOf(DropForeignKeyConstraintStatement(null, null, table, foundConstraint))
            "unique" -> arrayOf(
                DropUniqueConstraintStatement(
                    null,
                    null,
                    table,
                    foundConstraint,
                    ColumnConfig.arrayFromNames(columnNames),
                ),
            )
            else -> null
        }
    }
}
