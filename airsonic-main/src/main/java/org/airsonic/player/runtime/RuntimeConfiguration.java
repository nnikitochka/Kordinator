package org.airsonic.player.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Framework-neutral configuration used by the application runtime. */
public final class RuntimeConfiguration {

    public static final String DEFAULT_HEALTH_STATUS_ORDER = "DOWN,OUT_OF_SERVICE,UP,UNKNOWN";
    public static final String HEALTH_STATUS_ORDER_KEY = "management.endpoint.health.status.order";

    private static final Path AIRSONIC_HOME_WINDOWS = Paths.get("c:/airsonic");
    private static final Path AIRSONIC_HOME_OTHER = Paths.get("/var/airsonic");

    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile(".*(password|secret|key|token|credential).*", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> properties;
    private final java.util.function.Function<String, String> dynamicResolver;
    private final List<String> healthStatusOrder;

    public RuntimeConfiguration(String healthStatusOrder) {
        this(Map.of(HEALTH_STATUS_ORDER_KEY, Objects.requireNonNull(healthStatusOrder, "healthStatusOrder")),
                null,
                healthStatusOrder);
    }

    public RuntimeConfiguration(Map<String, String> properties) {
        this(properties, null, properties != null ? properties.get(HEALTH_STATUS_ORDER_KEY) : null);
    }

    public RuntimeConfiguration(Map<String, String> properties, String healthStatusOrder) {
        this(properties, null, healthStatusOrder);
    }

    public RuntimeConfiguration(Map<String, String> properties,
                                java.util.function.Function<String, String> dynamicResolver,
                                String healthStatusOrder) {
        this.properties = properties == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.dynamicResolver = dynamicResolver;

        String order = healthStatusOrder != null && !healthStatusOrder.trim().isEmpty()
                ? healthStatusOrder
                : this.properties.get(HEALTH_STATUS_ORDER_KEY);
        if (order == null && dynamicResolver != null) {
            order = dynamicResolver.apply(HEALTH_STATUS_ORDER_KEY);
        }
        if (order == null) {
            order = DEFAULT_HEALTH_STATUS_ORDER;
        }

        this.healthStatusOrder = Collections.unmodifiableList(Arrays.stream(order.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList()));
    }

    public String getProperty(String key) {
        String val = properties.get(key);
        if (val == null && dynamicResolver != null) {
            val = dynamicResolver.apply(key);
        }
        return val;
    }

    public String getProperty(String key, String defaultValue) {
        String val = getProperty(key);
        return val != null ? val : defaultValue;
    }

    public boolean containsKey(String key) {
        if (properties.containsKey(key)) {
            return true;
        }
        return dynamicResolver != null && dynamicResolver.apply(key) != null;
    }

    public int getIntProperty(String key, int defaultValue) {
        String val = properties.get(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String val = properties.get(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        String trimmed = val.trim().toLowerCase();
        if ("true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed) || "0".equals(trimmed) || "no".equals(trimmed)) {
            return false;
        }
        return defaultValue;
    }

    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    public Map<String, String> asMap() {
        return properties;
    }

    public Map<String, String> asMaskedMap() {
        Map<String, String> masked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
                masked.put(key, "******");
            } else {
                masked.put(key, value);
            }
        }
        return Collections.unmodifiableMap(masked);
    }

    public List<String> getHealthStatusOrder() {
        return healthStatusOrder;
    }

    public Path getAirsonicHome() {
        String overrideHome = getProperty("airsonic.home");
        String oldHome = getProperty("libresonic.home");
        if (overrideHome != null && !overrideHome.trim().isEmpty()) {
            return Paths.get(overrideHome.trim());
        }
        if (oldHome != null && !oldHome.trim().isEmpty()) {
            return Paths.get(oldHome.trim());
        }
        boolean isWin = System.getProperty("os.name", "unknown").toLowerCase().contains("win");
        return isWin ? AIRSONIC_HOME_WINDOWS : AIRSONIC_HOME_OTHER;
    }

    public String getLogFile() {
        String configured = getProperty("logging.file.name");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return getAirsonicHome().toAbsolutePath().resolve("airsonic.log").toString();
    }

    public String getDatabaseUrl() {
        return getProperty("spring.datasource.url");
    }

    public String getDatabaseDriver() {
        return getProperty("spring.datasource.driver-class-name");
    }

    public String getDatabaseUsername() {
        return getProperty("spring.datasource.username", "sa");
    }

    public String getDatabasePassword() {
        return getProperty("spring.datasource.password", "");
    }

    public String getDatabaseJndiName() {
        return getProperty("spring.datasource.jndi-name");
    }

    public String getLiquibaseRollbackFile() {
        String configured = getProperty("spring.liquibase.rollback-file");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return getAirsonicHome().toAbsolutePath().resolve("rollback.sql").toString();
    }

    public int getLiquibaseMysqlVarcharLimit() {
        return getIntProperty("spring.liquibase.parameters.mysqlVarcharLimit", 255);
    }

    public String getLiquibaseDefaultMusicFolder() {
        return getProperty("spring.liquibase.parameters.defaultMusicFolder");
    }
}
