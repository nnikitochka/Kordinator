package org.airsonic.player.service;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.runtime.RuntimeCompositionRoot;
import org.airsonic.player.runtime.RuntimeConfigurationLoader;
import org.airsonic.player.util.EmbeddedTestCategory;
import org.airsonic.player.util.FileUtils;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Category(EmbeddedTestCategory.class)
public class LegacyDatabaseStartupTestCase {

    @ClassRule
    public static final HomeRule airsonicRule = new HomeRule();

    @Test
    public void testStartup() throws Exception {
        String homeParent = TestCaseUtils.airsonicHomePathForTest();
        Path dbDirectory = Paths.get(homeParent, "db");
        FileUtils.copyRecursively(LegacyDatabaseStartupTestCase.class.getResource("/db/pre-liquibase/db"), dbDirectory);

        // The fixture uses the database basename from before the Airsonic rename.
        Properties properties = new Properties();
        properties.putAll(System.getProperties());
        properties.setProperty(SettingsService.KEY_DATABASE_URL,
                SettingsService.getDefaultJDBCUrl().replace("airsonic;", "libresonic;"));
        properties.setProperty(SettingsService.KEY_DATABASE_USERNAME, "sa");
        properties.setProperty(SettingsService.KEY_DATABASE_PASSWORD, "");

        RuntimeCompositionRoot runtime = new RuntimeCompositionRoot(new RuntimeConfigurationLoader()
                .load(new String[0], properties, System.getenv(), null, Map.of()));
        try (var lifecycle = runtime.getLifecycleRegistry()) {
            var persistence = runtime.createPersistenceGraph();
            try (var connection = persistence.getDataSource().getConnection()) {
                assertThat(connection.getMetaData().getURL()).contains("libresonic");
            }
            assertThat(persistence.getJdbcHelper().queryForList(
                    "select count(*) from databasechangelog where id=?", Integer.class, "create-kordinator-api-sessions"))
                    .containsExactly(1);
        }
    }
}
