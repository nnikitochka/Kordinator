package org.airsonic.player.runtime;

import org.airsonic.player.service.SettingsService;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loads framework-neutral application configuration following the exact precedence order:
 * CLI arguments > JVM system properties > Environment variables > application.properties >
 * airsonic.properties > Migrated aliases > Default constants.
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
                                    Properties applicationProperties,
                                    Map<String, String> airsonicProperties) {
        Map<String, String> cliMap = parseCliArgs(cliArgs);
        Map<String, String> jvmMap = propertiesToMap(jvmProperties);
        Map<String, String> envMap = environment != null ? new LinkedHashMap<>(environment) : Collections.emptyMap();
        Map<String, String> appMap = applicationProperties != null
                ? propertiesToMap(applicationProperties)
                : loadClasspathProperties(APP_PROPERTIES_RESOURCE);
        Map<String, String> fileMap = airsonicProperties != null
                ? new LinkedHashMap<>(airsonicProperties)
                : Collections.emptyMap();

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
        // Migration aliases
        Map<String, String> keyMaps = SettingsService.getMigratedPropertyKeys();
        Map<String, Object> migrated = new LinkedHashMap<>();

        // Migrate in precedence order: CLI > JVM > ENV > app > file
        SettingsService.migrateKeys(keyMaps, cli::get, "cli", migrated);
        SettingsService.migrateKeys(keyMaps, jvm::get, "jvm", migrated);
        SettingsService.migrateKeys(keyMaps, key -> lookupEnv(env, key), "environment", migrated);
        SettingsService.migrateKeys(keyMaps, app::get, "application.properties", migrated);
        SettingsService.migrateKeys(keyMaps, file::get, "airsonic.properties", migrated);

        Map<String, String> migratedStringMap = new LinkedHashMap<>();
        migrated.forEach((k, v) -> {
            if (v != null) {
                migratedStringMap.put(k, v.toString());
            }
        });

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
        defaults.put("spring.datasource.username", "sa");
        defaults.put("spring.datasource.password", "");
        defaults.put("spring.datasource.driver-class-name", "org.hsqldb.jdbc.JDBCDriver");
        defaults.put("spring.datasource.url", "jdbc:hsqldb:file:"
                + airsonicHome.resolve("db/airsonic").toString()
                + ";hsqldb.default_table_type=cached;hsqldb.cache_rows=100000;hsqldb.cache_size=65536;hsqldb.applog=0"
                + ";hsqldb.tx=mvcc;sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;sql.pad_space=false"
                + ";hsqldb.defrag_limit=50;shutdown=true");
        defaults.put("logging.file.name", airsonicHome.resolve("airsonic.log").toString());
        defaults.put("spring.liquibase.rollback-file", airsonicHome.resolve("rollback.sql").toString());
        defaults.put("spring.liquibase.parameters.mysqlVarcharLimit", "255");
        defaults.put("spring.liquibase.parameters.defaultMusicFolder",
                System.getProperty("os.name", "unknown").toLowerCase().contains("win") ? "c:\\music" : "/var/music");

        // Merge all known keys
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(cli.keySet());
        allKeys.addAll(jvm.keySet());
        allKeys.addAll(env.keySet());
        // Map relaxed env keys to dotted keys if they look like relaxed Spring properties
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

}
