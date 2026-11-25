package su.nnedition.kord.dao;

import su.nnedition.kord.runtime.DaoGraph;
import su.nnedition.kord.runtime.RuntimeDatabaseRule;
import org.junit.ClassRule;

public abstract class DaoTestCase {
    @ClassRule
    public static final RuntimeDatabaseRule database = new RuntimeDatabaseRule();

    protected final JdbcHelper getJdbcHelper() {
        return database.getPersistence().getJdbcHelper();
    }

    protected final DaoGraph daos() {
        return database.getPersistence().getDaos();
    }
}
