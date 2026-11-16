package org.airsonic.player.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Framework-neutral JDBC helper replacing Spring's JdbcTemplate, NamedParameterJdbcTemplate,
 * and SimpleJdbcInsert with clean, zero-dependency JDBC operations.
 */
public class JdbcHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcHelper.class);

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z0-9_]+)");

    private final DataSource dataSource;
    private final TransactionManager transactionManager;

    public JdbcHelper(DataSource dataSource) {
        this(dataSource, new TransactionManager(dataSource));
    }

    public JdbcHelper(DataSource dataSource, TransactionManager transactionManager) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public int update(String sql, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = transactionManager.getConnection();
            ps = conn.prepareStatement(sql);
            setParameters(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute update: " + sql, e);
        } finally {
            closeStatement(ps);
            transactionManager.releaseConnection(conn);
        }
    }

    public int namedUpdate(String sql, Map<String, Object> args) {
        ParsedSql parsed = parseNamedSql(sql, args);
        return update(parsed.sql, parsed.parameters.toArray());
    }

    public int batchedUpdate(String sql, Collection<Object[]> batchArgs, int batchSize) {
        if (batchArgs == null || batchArgs.isEmpty()) {
            return 0;
        }
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = transactionManager.getConnection();
            ps = conn.prepareStatement(sql);
            int count = 0;
            int total = 0;
            for (Object[] args : batchArgs) {
                setParameters(ps, args);
                ps.addBatch();
                count++;
                if (count % batchSize == 0) {
                    int[] batchResults = ps.executeBatch();
                    for (int res : batchResults) {
                        total += (res >= 0 ? res : (res == Statement.SUCCESS_NO_INFO ? 1 : 0));
                    }
                }
            }
            if (count % batchSize != 0) {
                int[] batchResults = ps.executeBatch();
                for (int res : batchResults) {
                    total += (res >= 0 ? res : (res == Statement.SUCCESS_NO_INFO ? 1 : 0));
                }
            }
            return total;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute batch update: " + sql, e);
        } finally {
            closeStatement(ps);
            transactionManager.releaseConnection(conn);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = transactionManager.getConnection();
            ps = conn.prepareStatement(sql);
            setParameters(ps, args);
            rs = ps.executeQuery();
            List<T> results = new ArrayList<>();
            int rowNum = 0;
            while (rs.next()) {
                results.add(rowMapper.mapRow(rs, rowNum++));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query: " + sql, e);
        } finally {
            closeResultSet(rs);
            closeStatement(ps);
            transactionManager.releaseConnection(conn);
        }
    }

    public <T> List<T> namedQuery(String sql, RowMapper<T> rowMapper, Map<String, Object> args) {
        ParsedSql parsed = parseNamedSql(sql, args);
        return query(parsed.sql, rowMapper, parsed.parameters.toArray());
    }

    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
        return query(sql, (rs, rowNum) -> convertValue(rs.getObject(1), elementType), args);
    }

    public <T> List<T> namedQueryForList(String sql, Class<T> elementType, Map<String, Object> args) {
        ParsedSql parsed = parseNamedSql(sql, args);
        return queryForList(parsed.sql, elementType, parsed.parameters.toArray());
    }

    public Integer insertAndReturnKey(String sql, String keyColumn, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = transactionManager.getConnection();
            if (keyColumn != null && !keyColumn.trim().isEmpty()) {
                ps = conn.prepareStatement(sql, new String[]{keyColumn});
            } else {
                ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            setParameters(ps, args);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            if (rs != null && rs.next()) {
                Object keyVal = rs.getObject(1);
                if (keyVal instanceof Number) {
                    return ((Number) keyVal).intValue();
                }
                if (keyVal != null) {
                    return Integer.parseInt(keyVal.toString());
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute insert: " + sql, e);
        } finally {
            closeResultSet(rs);
            closeStatement(ps);
            transactionManager.releaseConnection(conn);
        }
    }

    public static Object convertToDBType(Object x) {
        if (x instanceof Instant) {
            return Timestamp.from((Instant) x);
        }
        if (x instanceof Enum) {
            return ((Enum<?>) x).name();
        }
        if (x instanceof Path) {
            return ((Path) x).toString();
        }
        return x;
    }

    private void setParameters(PreparedStatement ps, Object[] args) throws SQLException {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object val = convertToDBType(args[i]);
                if (val == null) {
                    ps.setNull(i + 1, Types.NULL);
                } else if (val instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) val);
                } else if (val instanceof java.sql.Date) {
                    ps.setDate(i + 1, (java.sql.Date) val);
                } else {
                    ps.setObject(i + 1, val);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertValue(Object raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        if (type.isInstance(raw)) {
            return (T) raw;
        }
        if (type == String.class) {
            return (T) raw.toString();
        }
        if (type == Integer.class || type == int.class) {
            if (raw instanceof Number) {
                return (T) Integer.valueOf(((Number) raw).intValue());
            }
            return (T) Integer.valueOf(raw.toString().trim());
        }
        if (type == Long.class || type == long.class) {
            if (raw instanceof Number) {
                return (T) Long.valueOf(((Number) raw).longValue());
            }
            return (T) Long.valueOf(raw.toString().trim());
        }
        if (type == Double.class || type == double.class) {
            if (raw instanceof Number) {
                return (T) Double.valueOf(((Number) raw).doubleValue());
            }
            return (T) Double.valueOf(raw.toString().trim());
        }
        if (type == Timestamp.class && raw instanceof java.util.Date) {
            return (T) new Timestamp(((java.util.Date) raw).getTime());
        }
        if (type == Instant.class) {
            if (raw instanceof Timestamp) {
                return (T) ((Timestamp) raw).toInstant();
            }
            if (raw instanceof java.util.Date) {
                return (T) ((java.util.Date) raw).toInstant();
            }
        }
        throw new IllegalArgumentException("Cannot convert value of type " + raw.getClass() + " to " + type);
    }

    public static ParsedSql parseNamedSql(String sql, Map<String, Object> args) {
        if (sql == null) {
            return new ParsedSql("", Collections.emptyList());
        }
        Map<String, Object> params = args != null ? args : Collections.emptyMap();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        List<Object> values = new ArrayList<>();

        while (matcher.find()) {
            String name = matcher.group(1);
            Object val = params.get(name);

            if (val instanceof Collection) {
                Collection<?> col = (Collection<?>) val;
                if (col.isEmpty()) {
                    matcher.appendReplacement(sb, "NULL");
                } else {
                    StringBuilder qm = new StringBuilder();
                    Iterator<?> it = col.iterator();
                    while (it.hasNext()) {
                        values.add(it.next());
                        qm.append("?");
                        if (it.hasNext()) {
                            qm.append(", ");
                        }
                    }
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(qm.toString()));
                }
            } else if (val != null && val.getClass().isArray()) {
                int length = Array.getLength(val);
                if (length == 0) {
                    matcher.appendReplacement(sb, "NULL");
                } else {
                    StringBuilder qm = new StringBuilder();
                    for (int i = 0; i < length; i++) {
                        values.add(Array.get(val, i));
                        qm.append("?");
                        if (i < length - 1) {
                            qm.append(", ");
                        }
                    }
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(qm.toString()));
                }
            } else {
                values.add(val);
                matcher.appendReplacement(sb, "?");
            }
        }
        matcher.appendTail(sb);
        return new ParsedSql(sb.toString(), values);
    }

    public static final class ParsedSql {
        public final String sql;
        public final List<Object> parameters;

        public ParsedSql(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }

    private static void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOG.warn("Failed to close statement", e);
            }
        }
    }

    private static void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOG.warn("Failed to close result set", e);
            }
        }
    }
}
