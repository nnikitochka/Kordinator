package org.airsonic.player.dao

import java.sql.ResultSet
import java.sql.SQLException

/** Maps a JDBC result row to a result object. */
fun interface RowMapper<T> {
    @Throws(SQLException::class)
    fun mapRow(rs: ResultSet, rowNum: Int): T
}
