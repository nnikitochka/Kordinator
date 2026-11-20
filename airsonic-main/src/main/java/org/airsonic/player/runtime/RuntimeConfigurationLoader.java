package org.airsonic.player.runtime;

import org.airsonic.player.service.SettingsService;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loads framework-neutral application configuration following the exact precedence order:
 * CLI arguments > JVM system properties > Environment variables > application.properties >
 * airsonic.properties > Default constants. Legacy aliases are normalized within each source layer.
 */
public final class RuntimeConfigurationLoader {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeConfigurationLoader.class);

    private static final String APP_PROPERTIES_RESOURCE = "/application.properties";

    public RuntimeConfiguration load() {
        return load(new String[0], System.getProperties(), System.getenv(), null, (Map<String, String>) null);
    }

    public RuntimeConfiguration load(String[] cliArgs,
                                     Properties jvmProperties,
                                     Map<String, String> environment,
                                     Properties applicationProperties) {
        return load(cliArgs, jvmProperties, environment, applicationProperties, (Map<String, String>) null);
    }

    public RuntimeConfiguration load(String[] cliArgs,
                                    Properties jvmProperties,
                                    Map<String, String> environment,
                                    Properties applicationProperties,
                                    Map<String, String> airsonicProperties) {
        Map<String, String> rawCliMap = parseCliArgs(cliArgs);
        Map<String, String> rawJvmMap = propertiesToMap(jvmProperties);
        Map<String, String> rawEnvMap = environment != null ? new LinkedHashMap<>(environment) : Collections.emptyMap();
        Map<String, String> rawAppMap = applicationProperties != null
                ? propertiesToMap(applicationProperties)
                : loadClasspathProperties(APP_PROPERTIES_RESOURCE);
        Map<String, String> keyMaps = SettingsService.getMigratedPropertyKeys();
        Map<String, String> cliMap = normalizeLayer(rawCliMap, keyMaps, rawCliMap::get, "cli");
        Map<String, String> jvmMap = normalizeLayer(rawJvmMap, keyMaps, rawJvmMap::get, "jvm");
        Map<String, String> envMap = normalizeLayer(rawEnvMap, keyMaps, key -> lookupEnv(rawEnvMap, key), "environment");
        Map<String, String> appMap = normalizeLayer(rawAppMap, keyMaps, rawAppMap::get, "application.properties");
        Map<String, String> rawFileMap = airsonicProperties != null
                ? new LinkedHashMap<>(airsonicProperties)
                : loadAirsonicProperties(cliMap, jvmMap, envMap, appMap);
        Map<String, String> fileMap = normalizeLayer(rawFileMap, keyMaps, rawFileMap::get, "airsonic.properties");

        return buildConfiguration(cliMap, jvmMap, envMap, appMap, fileMap);
    }

    public RuntimeConfiguration load(String[] cliArgs,
                                    Properties jvmProperties,
                                    Map<String, String> environment,
                                    Properties applicationProperties,
                                    Configuration airsonicConfig) {
        Map<String, String> fileMap = new LinkedHashMap<>();
        if (airsonicConfig != null) {
            Iterator<String> keys = airsonicConfig.getKeys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = airsonicConfig.getProperty(key);
                if (val != null) {
                    fileMap.put(key, val.toString());
                }
            }
        }
        return load(cliArgs, jvmProperties, environment, applicationProperties, fileMap);
    }

    private RuntimeConfiguration buildConfiguration(Map<String, String> cli,
                                                    Map<String, String> jvm,
                                                    Map<String, String> env,
                                                    Map<String, String> app,
                                                    Map<String, String> file) {
        Map<String, String> migratedStringMap = Collections.emptyMap();

        // Determine airsonic home first (needed for default paths)
        String airsonicHomeStr = resolveRaw(cli, jvm, env, app, file, migratedStringMap, Collections.emptyMap(), "airsonic.home");
        if (StringUtils.isBlank(airsonicHomeStr)) {
            airsonicHomeStr = resolveRaw(cli, jvm, env, app, file, migratedStringMap, Collections.emptyMap(), "libresonic.home");
        }
        Path airsonicHome;
        if (StringUtils.isNotBlank(airsonicHomeStr)) {
            airsonicHome = Paths.get(airsonicHomeStr.trim());
        } else {
            boolean isWin = System.getProperty("os.name", "unknown").toLowerCase().contains("win");
            airsonicHome = isWin ? Paths.get("c:/airsonic") : Paths.get("/var/airsonic");
        }

        // Default constants
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("management.endpoint.health.status.order", RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER);
        defaults.put(RuntimeConfiguration.DATABASE_USERNAME_KEY, "sa");
        defaults.put(RuntimeConfiguration.DATABASE_PASSWORD_KEY, "");
        defaults.put(RuntimeConfiguration.DATABASE_DRIVER_KEY, "org.hsqldb.jdbc.JDBCDriver");
        defaults.put(RuntimeConfiguration.DATABASE_URL_KEY, "jdbc:hsqldb:file:"
                + airsonicHome.resolve("db/airsonic").toString()
                + ";hsqldb.default_table_type=cached;hsqldb.cache_rows=100000;hsqldb.cache_size=65536;hsqldb.applog=0"
                + ";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;sql.pad_space=false"
                + ";hsqldb.defrag_limit=50;shutdown=true");
        defaults.put("logging.file.name", airsonicHome.resolve("airsonic.log").toString());
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_ENABLED_KEY, "true");
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_CHANGELOG_KEY, "classpath:liquibase/db-changelog.xml");
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_CONTEXTS_KEY, "");
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_LABEL_FILTER_KEY, "");
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_ROLLBACK_FILE_KEY, airsonicHome.resolve("rollback.sql").toString());
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_MYSQL_VARCHAR_LIMIT_KEY, "255");
        defaults.put(RuntimeConfiguration.DATABASE_MIGRATION_DEFAULT_MUSIC_FOLDER_KEY,
                System.getProperty("os.name", "unknown").toLowerCase().contains("win") ? "c:\\music" : "/var/music");

        // Merge all known keys
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(cli.keySet());
        allKeys.addAll(jvm.keySet());
        allKeys.addAll(env.keySet());
        // Map relaxed environment keys to dotted property names.
        for (String envKey : env.keySet()) {
            if (envKey.contains("_")) {
                allKeys.add(envKey.toLowerCase(Locale.ROOT).replace('_', '.'));
            }
        }
        allKeys.addAll(app.keySet());
        allKeys.addAll(file.keySet());
        allKeys.addAll(migratedStringMap.keySet());
        allKeys.addAll(defaults.keySet());

        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : allKeys) {
            String value = resolveRaw(cli, jvm, env, app, file, migratedStringMap, defaults, key);
            if (value != null) {
                resolved.put(key, value);
            }
        }

        return new RuntimeConfiguration(resolved);
    }

    private Map<String, String> normalizeLayer(Map<String, String> source,
                                              Map<String, String> keyMaps,
                                              java.util.function.Function<String, Object> propertyLookup,
                                              String sourceName) {
        Map<String, String> normalized = new LinkedHashMap<>(source);
        Map<String, Object> migrated = new LinkedHashMap<>();
        SettingsService.migrateKeys(keyMaps, propertyLookup, sourceName, migrated);
        migrated.forEach((key, value) -> normalized.putIfAbsent(key, value.toString()));

        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            String canonicalKey = null;
            if (key.startsWith("spring.liquibase.parameters.")) {
                canonicalKey = RuntimeConfiguration.DATABASE_MIGRATION_PARAMETERS_PREFIX
                        + key.substring("spring.liquibase.parameters.".length());
            } else if (key.startsWith("spring.datasource.hikari.")) {
                canonicalKey = RuntimeConfiguration.DATABASE_HIKARI_PREFIX
                        + key.substring("spring.datasource.hikari.".length());
            }
            if (canonicalKey != null) {
                normalized.putIfAbsent(canonicalKey, entry.getValue());
            }
        }
        return normalized;
    }

    private String resolveRaw(Map<String, String> cli,
                              Map<String, String> jvm,
                              Map<String, String> env,
                              Map<String, String> app,
                              Map<String, String> file,
                              Map<String, String> migrated,
                              Map<String, String> defaults,
                              String key) {
        if (cli.containsKey(key)) {
            return cli.get(key);
        }
        if (jvm.containsKey(key)) {
            return jvm.get(key);
        }
        String envVal = lookupEnv(env, key);
        if (envVal != null) {
            return envVal;
        }
        if (app.containsKey(key)) {
            return app.get(key);
        }
        if (file.containsKey(key)) {
            return file.get(key);
        }
        if (migrated.containsKey(key)) {
            return migrated.get(key);
        }
        String defaultVal = defaults.get(key);
        if (defaultVal != null) {
            return defaultVal;
        }
        return null;
    }

    public static String lookupEnv(Map<String, String> env, String key) {
        if (env == null || key == null) {
            return null;
        }
        if (env.containsKey(key)) {
            return env.get(key);
        }
        String relaxed = key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        if (env.containsKey(relaxed)) {
            return env.get(relaxed);
        }
        return null;
    }

    public static Map<String, String> parseCliArgs(String[] args) {
        if (args == null || args.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith("--")) {
                String trimmed = arg.substring(2);
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                } else if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                    result.put(trimmed.trim(), args[i + 1].trim());
                    i++;
                } else {
                    result.put(trimmed.trim(), "true");
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> propertiesToMap(Properties properties) {
        if (properties == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }

    private static Map<String, String> loadClasspathProperties(String resourcePath) {
        Properties properties = new Properties();
        try (InputStream is = RuntimeConfigurationLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception e) {
            LOG.warn("Could not load classpath properties from {}", resourcePath, e);
        }
        return propertiesToMap(properties);
    }

    private Map<String, String> loadAirsonicProperties(Map<String, String> cli,
                                                       Map<String, String> jvm,
                                                       Map<String, String> env,
                                                       Map<String, String> app) {
        String home = resolveRaw(cli, jvm, env, app, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), "airsonic.home");
        if (StringUtils.isBlank(home)) {
            home = resolveRaw(cli, jvm, env, app, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), "libresonic.home");
        }
        if (StringUtils.isBlank(home)) {
            boolean isWin = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("win");
            home = isWin ? "c:/airsonic" : "/var/airsonic";
        }

        Path homePath = Paths.get(home.trim());
        String fileName = homePath.toString().contains("libresonic") ? "libresonic.properties" : "airsonic.properties";
        Path propertyFile = homePath.resolve(fileName);
        if (!Files.isRegularFile(propertyFile)) {
            return Collections.emptyMap();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertyFile)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load application properties from " + propertyFile, failure);
        }
        return propertiesToMap(properties);
    }

}
