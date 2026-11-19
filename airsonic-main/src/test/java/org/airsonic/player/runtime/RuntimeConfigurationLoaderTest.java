package org.airsonic.player.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigurationLoaderTest {

    private final RuntimeConfigurationLoader loader = new RuntimeConfigurationLoader();

    @ParameterizedTest(name = "layers: cli={0}, jvm={1}, env={2}, app={3}, file={4} => expected={5}")
    @CsvSource({
            "cli, jvm, env, app, file, cli",
            "none, jvm, env, app, file, jvm",
            "none, none, env, app, file, env",
            "none, none, none, app, file, app",
            "none, none, none, none, file, file",
            "none, none, none, none, none, default-fallback"
    })
    void precedenceFollowsStrictOrder(String cliVal, String jvmVal, String envVal, String appVal, String fileVal, String expected) {
        String key = "test.property";
        String[] cli = "none".equals(cliVal) ? new String[0] : new String[]{"--" + key + "=" + cliVal};

        Properties jvm = new Properties();
        if (!"none".equals(jvmVal)) {
            jvm.setProperty(key, jvmVal);
        }

        Map<String, String> env = new HashMap<>();
        if (!"none".equals(envVal)) {
            env.put(key, envVal);
        }

        Properties app = new Properties();
        if (!"none".equals(appVal)) {
            app.setProperty(key, appVal);
        }

        Map<String, String> file = new HashMap<>();
        if (!"none".equals(fileVal)) {
            file.put(key, fileVal);
        }

        RuntimeConfiguration config = loader.load(cli, jvm, env, app, file);
        assertEquals(expected, config.getProperty(key, "default-fallback"));
    }

    @Test
    void relaxedEnvironmentVariablesAreResolved() {
        Map<String, String> env = Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/airsonic",
                "AIRSONIC_HOME", "/custom/airsonic",
                "SERVER_PORT", "9090"
        );

        RuntimeConfiguration config = loader.load(new String[0], new Properties(), env, new Properties(), Collections.emptyMap());

        assertEquals("jdbc:postgresql://localhost:5432/airsonic", config.getProperty("spring.datasource.url"));
        assertEquals("/custom/airsonic", config.getProperty("airsonic.home"));
        assertEquals(Paths.get("/custom/airsonic"), config.getAirsonicHome());
        assertEquals("9090", config.getProperty("server.port"));
        assertEquals(9090, config.getIntProperty("server.port", 8080));
    }

    @Test
    void cliArgumentsParsingHandlesVariousFormats() {
        String[] args = new String[]{
                "--server.port=8080",
                "--airsonic.home", "/opt/airsonic",
                "--feature.enabled"
        };

        Map<String, String> parsed = RuntimeConfigurationLoader.parseCliArgs(args);
        assertEquals("8080", parsed.get("server.port"));
        assertEquals("/opt/airsonic", parsed.get("airsonic.home"));
        assertEquals("true", parsed.get("feature.enabled"));
    }

    @Test
    void migrationAliasesResolveWithProperPrecedence() {
        // DatabaseConfigEmbedUrl is a legacy alias for spring.datasource.url
        Map<String, String> env = Map.of("DatabaseConfigEmbedUrl", "jdbc:mysql://localhost:3306/airsonic");
        RuntimeConfiguration config = loader.load(new String[0], new Properties(), env, new Properties(), Collections.emptyMap());

        assertEquals("jdbc:mysql://localhost:3306/airsonic", config.getProperty("spring.datasource.url"));
        assertEquals("jdbc:mysql://localhost:3306/airsonic", config.getDatabaseUrl());
    }

    @Test
    void chainMigrationAliasesResolveCorrectly() {
        // database.config.embed.url -> DatabaseConfigEmbedUrl -> spring.datasource.url
        Map<String, String> fileProps = Map.of("database.config.embed.url", "jdbc:mariadb://localhost/airsonic");
        RuntimeConfiguration config = loader.load(new String[0], new Properties(), Collections.emptyMap(), new Properties(), fileProps);

        assertEquals("jdbc:mariadb://localhost/airsonic", config.getProperty("spring.datasource.url"));
        assertEquals("jdbc:mariadb://localhost/airsonic", config.getDatabaseUrl());
    }

    @Test
    void directConfigurationOverridesMigratedAlias() {
        // App defines current property name, but File defines old alias
        Properties appProps = new Properties();
        appProps.setProperty("spring.datasource.url", "jdbc:hsqldb:mem:test");

        Map<String, String> fileProps = Map.of("DatabaseConfigEmbedUrl", "jdbc:mysql://legacy");

        RuntimeConfiguration config = loader.load(new String[0], new Properties(), Collections.emptyMap(), appProps, fileProps);
        assertEquals("jdbc:hsqldb:mem:test", config.getProperty("spring.datasource.url"));
    }

    @Test
    void typeConversionsAndInvalidHandling() {
        Properties jvm = new Properties();
        jvm.setProperty("int.valid", "4040");
        jvm.setProperty("int.invalid", "not-a-number");
        jvm.setProperty("bool.true", "true");
        jvm.setProperty("bool.yes", "yes");
        jvm.setProperty("bool.one", "1");
        jvm.setProperty("bool.false", "false");
        jvm.setProperty("bool.invalid", "maybe");

        RuntimeConfiguration config = loader.load(new String[0], jvm, Collections.emptyMap(), new Properties(), Collections.emptyMap());

        assertEquals(4040, config.getIntProperty("int.valid", 1000));
        assertEquals(1000, config.getIntProperty("int.invalid", 1000));
        assertEquals(1000, config.getIntProperty("int.missing", 1000));

        assertTrue(config.getBooleanProperty("bool.true", false));
        assertTrue(config.getBooleanProperty("bool.yes", false));
        assertTrue(config.getBooleanProperty("bool.one", false));
        assertFalse(config.getBooleanProperty("bool.false", true));
        assertFalse(config.getBooleanProperty("bool.invalid", false));
        assertTrue(config.getBooleanProperty("bool.missing", true));
    }

    @Test
    void emptyPropertyDoesNotFallBackToLowerLayer() {
        Properties jvm = new Properties();
        jvm.setProperty("empty.prop", "");

        Properties app = new Properties();
        app.setProperty("empty.prop", "from-app");

        RuntimeConfiguration config = loader.load(new String[0], jvm, Collections.emptyMap(), app, Collections.emptyMap());

        assertEquals("", config.getProperty("empty.prop"));
        assertEquals("", config.getProperty("empty.prop", "fallback"));
    }

    @Test
    void secretMaskingHidesSensitiveKeys() {
        Properties props = new Properties();
        props.setProperty("spring.datasource.password", "secretPassword123");
        props.setProperty("DatabaseConfigEmbedPassword", "legacySecret");
        props.setProperty("server.remember-me-key", "mySuperKey");
        props.setProperty("airsonic.jwt.key", "jwtSecretKey");
        props.setProperty("ldap.managerPassword", "ldapPass");
        props.setProperty("server.port", "8080");
        props.setProperty("airsonic.home", "/var/airsonic");

        RuntimeConfiguration config = loader.load(new String[0], props, Collections.emptyMap(), new Properties(), Collections.emptyMap());

        Map<String, String> masked = config.asMaskedMap();
        assertEquals("******", masked.get("spring.datasource.password"));
        assertEquals("******", masked.get("DatabaseConfigEmbedPassword"));
        assertEquals("******", masked.get("server.remember-me-key"));
        assertEquals("******", masked.get("airsonic.jwt.key"));
        assertEquals("******", masked.get("ldap.managerPassword"));

        // Non-sensitive keys remain unmasked
        assertEquals("8080", masked.get("server.port"));
        assertEquals("/var/airsonic", masked.get("airsonic.home"));
    }

    @Test
    void loggingDefaultLocationAndOverrides() {
        // Without override -> default is <home>/airsonic.log
        Properties props = new Properties();
        props.setProperty("airsonic.home", "/tmp/airsonic-test");
        RuntimeConfiguration config = loader.load(new String[0], props, Collections.emptyMap(), new Properties(), Collections.emptyMap());

        assertEquals("/tmp/airsonic-test/airsonic.log", config.getLogFile());

        // With explicit logging.file.name override
        props.setProperty("logging.file.name", "/var/log/airsonic/custom.log");
        RuntimeConfiguration configOverridden = loader.load(new String[0], props, Collections.emptyMap(), new Properties(), Collections.emptyMap());

        assertEquals("/var/log/airsonic/custom.log", configOverridden.getLogFile());
    }

    @Test
    void runtimeLoaderExposesApplicationConfigurationWithoutFrameworkAdapters() {
        Properties application = new Properties();
        application.setProperty("server.port", "7070");
        application.setProperty("spring.datasource.username", "testuser");
        application.setProperty("spring.datasource.password", "testpass");
        application.setProperty("management.endpoint.health.status.order", "FATAL,DOWN,UP");

        RuntimeConfiguration configuration = loader.load(
                new String[0], new Properties(), Collections.emptyMap(), application, Collections.emptyMap());

        assertEquals("7070", configuration.getProperty("server.port"));
        assertEquals("testuser", configuration.getDatabaseUsername());
        assertEquals("testpass", configuration.getDatabasePassword());
        assertEquals(List.of("FATAL", "DOWN", "UP"), configuration.getHealthStatusOrder());
        assertTrue(configuration.containsKey("server.port"));
        assertFalse(configuration.containsKey("nonexistent"));
        assertEquals("******", configuration.asMaskedMap().get("spring.datasource.password"));
    }
}
