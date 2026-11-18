package org.airsonic.player.runtime;

import java.util.Objects;
import javax.sql.DataSource;

/** Minimal framework-neutral composition root. Runtime services are added here as they are migrated. */
public final class RuntimeCompositionRoot {

    private final RuntimeConfiguration configuration;
    private final LifecycleRegistry lifecycleRegistry;
    private PersistenceGraph persistenceGraph;
    private javax.cache.CacheManager cacheManager;
    private org.airsonic.player.service.MediaFolderService mediaFolderService;

    public RuntimeCompositionRoot() {
        this(new RuntimeConfiguration(RuntimeConfiguration.DEFAULT_HEALTH_STATUS_ORDER), new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(String healthStatusOrder) {
        this(new RuntimeConfiguration(healthStatusOrder), new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(RuntimeConfiguration configuration) {
        this(configuration, new LifecycleRegistry());
    }

    public RuntimeCompositionRoot(RuntimeConfiguration configuration, LifecycleRegistry lifecycleRegistry) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.lifecycleRegistry = Objects.requireNonNull(lifecycleRegistry, "lifecycleRegistry");
    }

    public RuntimeConfiguration getConfiguration() {
        return configuration;
    }

    public LifecycleRegistry getLifecycleRegistry() {
        return lifecycleRegistry;
    }

    public synchronized PersistenceGraph createPersistenceGraph(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        if (persistenceGraph != null) {
            if (persistenceGraph.getDataSource() != dataSource) {
                throw new IllegalStateException("Persistence graph already created for another DataSource");
            }
            return persistenceGraph;
        }

        JdbcFactory jdbcFactory = new JdbcFactory();
        org.airsonic.player.dao.TransactionManager transactionManager = jdbcFactory.transactionManager(dataSource);
        org.airsonic.player.dao.JdbcHelper jdbcHelper = jdbcFactory.jdbcHelper(dataSource, transactionManager);
        DaoGraph daoGraph = new DaoFactory().create(jdbcHelper, transactionManager);
        persistenceGraph = new PersistenceGraph(dataSource, transactionManager, jdbcHelper, daoGraph);
        if (dataSource instanceof AutoCloseable closeable) {
            lifecycleRegistry.registerShutdown("dataSource", closeable);
        }
        return persistenceGraph;
    }

    public synchronized javax.cache.CacheManager createCacheManager() {
        if (cacheManager == null) {
            cacheManager = new CacheFactory().createCacheManager();
            lifecycleRegistry.registerShutdown("cacheManager", cacheManager::close);
        }
        return cacheManager;
    }

    public synchronized org.airsonic.player.service.MediaFolderService createMediaFolderService() {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before media services");
        }
        if (mediaFolderService == null) {
            mediaFolderService = new org.airsonic.player.service.MediaFolderService(
                    persistenceGraph.getDaos().getMusicFolderDao(),
                    persistenceGraph.getDaos().getMediaFileDao(),
                    createCacheManager());
        }
        return mediaFolderService;
    }
}
