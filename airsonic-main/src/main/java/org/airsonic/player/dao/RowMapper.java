package org.airsonic.player.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Framework-neutral row mapper interface for JDBC result set mapping.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Implementations must implement this method to map each row of data in the
     * ResultSet to an object.
     *
     * @param rs     the ResultSet to map (pre-initialized for the current row)
     * @param rowNum the number of the current row (0-based)
     * @return the result object for the current row
     * @throws SQLException if a SQLException is encountered getting column values
     */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
