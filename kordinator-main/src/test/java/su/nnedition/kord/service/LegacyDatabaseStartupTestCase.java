package su.nnedition.kord.service;

import su.nnedition.kord.TestCaseUtils;
import su.nnedition.kord.runtime.RuntimeCompositionRoot;
import su.nnedition.kord.runtime.RuntimeConfigurationLoader;
import su.nnedition.kord.util.EmbeddedTestCategory;
import su.nnedition.kord.util.FileUtils;
import su.nnedition.kord.util.HomeRule;
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
    public static final HomeRule kordinatorRule = new HomeRule();

    @Test
    public void testStartup() throws Exception {
        String homeParent = TestCaseUtils.kordinatorHomePathForTest();
        Path dbDirectory = Paths.get(homeParent, "db");
        FileUtils.copyRecursively(LegacyDatabaseStartupTestCase.class.getResource("/db/pre-liquibase/db"), dbDirectory);

        // The fixture uses the database basename from before the Kordinator rename.
        Properties properties = new Properties();
        properties.putAll(System.getProperties());
        properties.setProperty(SettingsService.KEY_DATABASE_URL,
                SettingsService.getDefaultJDBCUrl().replace("kordinator;", "libresonic;"));
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
