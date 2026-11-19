package org.airsonic.player.dao;

import com.google.common.base.CaseFormat;
import org.airsonic.player.util.LambdaUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract superclass for all DAO's. Backed by framework-neutral JdbcHelper.
 */
public class AbstractDao {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDao.class);

    private JdbcHelper jdbcHelper;

    protected AbstractDao() {
    }

    protected AbstractDao(JdbcHelper jdbcHelper) {
        this.jdbcHelper = Objects.requireNonNull(jdbcHelper, "jdbcHelper");
    }

    public JdbcHelper getJdbcHelper() {
        if (jdbcHelper == null) {
            throw new IllegalStateException("JdbcHelper is not initialized for " + getClass().getName());
        }
        return jdbcHelper;
    }

    protected <T> T inTransaction(TransactionManager.TransactionCallback<T> action) {
        return getJdbcHelper().getTransactionManager().execute(action);
    }

    protected static String questionMarks(String columns) {
        int numberOfColumns = StringUtils.countMatches(columns, ",") + 1;
        return StringUtils.repeat("?", ", ", numberOfColumns);
    }

    protected static String prefix(String columns, String prefix) {
        List<String> l = Arrays.asList(columns.split(", "));
        l.replaceAll(s -> prefix + "." + s);
        return String.join(", ", l);
    }

    protected static Object[] convertToDBTypes(Object[] args) {
        return args == null ? null : Stream.of(args)
                .map(AbstractDao::convertToDBType)
                .toList()
                .toArray();
    }

    protected static Map<String, Object> convertToDBTypes(Map<String, Object> args) {
        return args == null ? null : args.entrySet()
                .stream()
                .map(x -> Pair.of(x.getKey(), convertToDBType(x.getValue())))
                .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
    }

    protected static Object convertToDBType(Object x) {
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

    protected int update(String sql, Object... args) {
        long t = System.nanoTime();
        LOG.trace("Executing query: [{}]", sql);
        int result = getJdbcHelper().update(sql, convertToDBTypes(args));
        LOG.trace("Updated {} rows", result);
        log(sql, t);
        return result;
    }

    protected int namedUpdate(String sql, Map<String, Object> args) {
        long t = System.nanoTime();
        LOG.trace("Executing query: [{}]", sql);
        int result = getJdbcHelper().namedUpdate(sql, convertToDBTypes(args));
        LOG.trace("Updated {} rows", result);
        log(sql, t);
        return result;
    }

    protected int batchedUpdate(String sql, Collection<Object[]> batchArgs) {
        long t = System.nanoTime();
        int batchSize = 30000 / batchArgs.stream().findAny().map(x -> x.length).orElse(1);
        LOG.trace("Executing query: [{}]", sql);
        List<Object[]> converted = batchArgs.stream().map(AbstractDao::convertToDBTypes).collect(Collectors.toList());
        int tally = getJdbcHelper().batchedUpdate(sql, converted, batchSize);
        LOG.trace("Updated {} rows", tally);
        log(sql, t);
        return tally;
    }

    private void log(String sql, long startTimeNano) {
        long millis = (System.nanoTime() - startTimeNano) / 1000000L;
        if (millis > TimeUnit.SECONDS.toMillis(2L)) {
            LOG.debug("{} ms:  {}", millis, sql);
        }
    }

    protected <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        long t = System.nanoTime();
        List<T> result = getJdbcHelper().query(sql, rowMapper, convertToDBTypes(args));
        log(sql, t);
        return result;
    }

    protected <T> List<T> namedQuery(String sql, RowMapper<T> rowMapper, Map<String, Object> args) {
        long t = System.nanoTime();
        List<T> result = getJdbcHelper().namedQuery(sql, rowMapper, convertToDBTypes(args));
        log(sql, t);
        return result;
    }

    protected <T> List<T> queryForTypes(String sql, Class<T> type, Object... args) {
        long t = System.nanoTime();
        List<T> result = getJdbcHelper().queryForList(sql, type, convertToDBTypes(args));
        log(sql, t);
        return result;
    }

    protected <T> List<T> namedQueryForTypes(String sql, Class<T> type, Map<String, Object> args) {
        long t = System.nanoTime();
        List<T> result = getJdbcHelper().namedQueryForList(sql, type, convertToDBTypes(args));
        log(sql, t);
        return result;
    }

    public List<String> queryForStrings(String sql, Object... args) {
        return queryForTypes(sql, String.class, args);
    }

    protected List<Integer> queryForInts(String sql, Object... args) {
        return queryForTypes(sql, Integer.class, args);
    }

    protected List<String> namedQueryForStrings(String sql, Map<String, Object> args) {
        return namedQueryForTypes(sql, String.class, args);
    }

    public Integer queryForInt(String sql, Integer defaultValue, Object... args) {
        return queryForTypes(sql, Integer.class, args).stream().filter(Objects::nonNull).findFirst().orElse(defaultValue);
    }

    protected Integer namedQueryForInt(String sql, Integer defaultValue, Map<String, Object> args) {
        return namedQueryForTypes(sql, Integer.class, args).stream().filter(Objects::nonNull).findFirst().orElse(defaultValue);
    }

    protected Instant queryForInstant(String sql, Instant defaultValue, Object... args) {
        return queryForTypes(sql, Timestamp.class, args).stream().filter(Objects::nonNull).findFirst().map(Timestamp::toInstant).orElse(defaultValue);
    }

    protected Long queryForLong(String sql, Long defaultValue, Object... args) {
        return queryForTypes(sql, Long.class, args).stream().filter(Objects::nonNull).findFirst().orElse(defaultValue);
    }

    protected Double queryForDouble(String sql, Double defaultValue, Object... args) {
        return queryForTypes(sql, Double.class, args).stream().filter(Objects::nonNull).findFirst().orElse(defaultValue);
    }

    protected <T> T queryOne(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> list = query(sql, rowMapper, args);
        return list.isEmpty() ? null : list.getFirst();
    }

    protected <T> T namedQueryOne(String sql, RowMapper<T> rowMapper, Map<String, Object> args) {
        List<T> list = namedQuery(sql, rowMapper, args);
        return list.isEmpty() ? null : list.getFirst();
    }

    private final Map<String, String> insertSqls = new ConcurrentHashMap<>();
    private final Map<String, String> insertGeneratedKeys = new ConcurrentHashMap<>();
    private final Map<String, List<String>> insertColumnLists = new ConcurrentHashMap<>();
    private final Map<String, Map<String, MethodHandle>> methods = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final List<Function<String, String>> colNameTransforms = Arrays.asList(Function.identity(),
        c -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, c),
        c -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, c).toLowerCase());

    protected void registerInserts(String table, String generatedKey, List<String> columns, Class<?> klazz) throws Exception {
        getJdbcHelper();
        String sql = "insert into " + table + " (" + String.join(", ", columns) + ") values (" + questionMarks(String.join(", ", columns)) + ")";
        insertSqls.put(table, sql);
        insertColumnLists.put(table, new ArrayList<>(columns));
        if (generatedKey != null) {
            insertGeneratedKeys.put(table, generatedKey);
        }

        var fields = new HashMap<String, Field>();
        for (Field cf : klazz.getDeclaredFields()) {
            fields.putIfAbsent(cf.getName(), cf);
            Column annotation = cf.getAnnotation(Column.class);
            if (annotation != null) {
                fields.putIfAbsent(annotation.value(), cf);
            }
        }

        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(klazz, lookup);
        methods.put(table, columns.parallelStream().map(LambdaUtils.uncheckFunction(c -> {
            Field f = null;
            var alreadyLooked = new HashSet<String>();

            for (Function<String, String> colNameTransform : colNameTransforms) {
                String lookupCol = colNameTransform.apply(c);
                if (alreadyLooked.add(lookupCol)) {
                    f = fields.get(lookupCol);
                    if (f != null) {
                        LOG.debug("Found suitable field {} (as {}) in class {} for table {} column {}", f.getName(), lookupCol, klazz.getName(), table, c);
                        break;
                    }
                }
            }
            if (f == null) {
                LOG.error("Could not locate a suitable field in class {} for table {} column {}", klazz.getName(), table, c);
            }
            return Pair.of(c, privateLookup.unreflectGetter(f));
        })).collect(Collectors.toConcurrentMap(Pair::getLeft, Pair::getRight)));
    }

    protected Integer insert(String table, Object obj) {
        List<String> cols = insertColumnLists.get(table);
        String sql = insertSqls.get(table);
        String genKey = insertGeneratedKeys.get(table);
        Map<String, MethodHandle> tableMethods = methods.get(table);

        Object[] values = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            MethodHandle mh = tableMethods.get(cols.get(i));
            try {
                values[i] = convertToDBType(mh.invoke(obj));
            } catch (Throwable x) {
                throw new RuntimeException("Failed to read property for column " + cols.get(i) + " on table " + table, x);
            }
        }

        JdbcHelper helper = getJdbcHelper();
        if (genKey == null) {
            helper.update(sql, values);
            return null;
        } else {
            return helper.insertAndReturnKey(sql, genKey, values);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Column {
        String value();
    }
}
