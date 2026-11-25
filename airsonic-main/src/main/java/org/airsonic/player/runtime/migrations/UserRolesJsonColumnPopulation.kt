package org.airsonic.player.runtime.migrations

import java.sql.SQLException
import java.sql.Statement
import java.util.HashMap
import java.util.HashSet
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.DatabaseException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.UpdateStatement
import org.airsonic.player.domain.User
import org.airsonic.player.util.Util
import org.slf4j.LoggerFactory

class UserRolesJsonColumnPopulation : CustomSqlChange {
    override fun getConfirmationMessage() = "Column user.roles (for json) populated from user_role"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement> {
        val rolesByUser = HashMap<String, MutableSet<User.Role>>()
        val connection = database.connection as? JdbcConnection
        if (connection != null) {
            try {
                connection.createStatement().use { statement: Statement ->
                    statement.executeQuery("select ur.username, r.name from role r, user_role ur WHERE r.id=ur.role_id").use { rows ->
                        while (rows.next()) {
                            val username = rows.getString("username")
                            val roleName = rows.getString("name")
                            try {
                                rolesByUser.getOrPut(username) { HashSet() }
                                    .add(User.Role.valueOf(roleName.uppercase()))
                            } catch (exception: Exception) {
                                LOG.warn(
                                    "Exception while trying to migrate roles for user {}, role {}. Will skip this role and continue",
                                    username,
                                    roleName,
                                    exception,
                                )
                            }
                        }
                    }
                }
            } catch (exception: DatabaseException) {
                throw CustomChangeException(exception)
            } catch (exception: SQLException) {
                throw CustomChangeException(exception)
            }
        }

        val entries = rolesByUser.entries.toList()
        return Array(entries.size) { index ->
            val (username, roles) = entries[index]
                UpdateStatement(database.defaultCatalogName, database.defaultSchemaName, "user")
                    .addNewColumnValue("roles", Util.toJson(roles))
                    .setWhereClause("username=?")
                    .addWhereParameter(username)
        }
    }

    private companion object {
        val LOG = LoggerFactory.getLogger(UserRolesJsonColumnPopulation::class.java)
    }
}
