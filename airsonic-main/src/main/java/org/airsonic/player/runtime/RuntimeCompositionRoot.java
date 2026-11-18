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
    private org.airsonic.player.dao.PlayerDao playerDao;
    private org.airsonic.player.service.VersionService versionService;
    private org.airsonic.player.service.ServerInfoService serverInfoService;
    private org.airsonic.player.service.StatusService statusService;
    private org.airsonic.player.service.PasswordRecoveryService passwordRecoveryService;
    private org.airsonic.player.service.LyricsService lyricsService;
    private org.airsonic.player.service.BookmarkService bookmarkService;
    private org.airsonic.player.service.RatingService ratingService;
    private org.airsonic.player.service.MediaDirectoryService mediaDirectoryService;
    private org.airsonic.player.service.MusicIndexService musicIndexService;
    private org.airsonic.player.service.SecurityService securityService;
    private org.airsonic.player.service.JWTSecurityService jwtSecurityService;
    private org.airsonic.player.service.DatabaseService databaseService;

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

    public synchronized org.airsonic.player.dao.PlayerDao createPlayerDao() throws Exception {
        if (persistenceGraph == null) {
            throw new IllegalStateException("Persistence graph must be created before PlayerDao");
        }
        if (playerDao == null) {
            org.airsonic.player.dao.PlayerDaoPlayQueueFactory queueFactory =
                    new org.airsonic.player.dao.PlayerDaoPlayQueueFactory(createMediaFolderService());
            playerDao = new org.airsonic.player.dao.PlayerDao(queueFactory, persistenceGraph.getJdbcHelper());
            playerDao.register();
        }
        return playerDao;
    }

    public synchronized org.airsonic.player.service.VersionService createVersionService() throws java.io.IOException {
        if (versionService == null) {
            versionService = new org.airsonic.player.service.VersionService();
        }
        return versionService;
    }

    public synchronized org.airsonic.player.service.ServerInfoService createServerInfoService(
            org.airsonic.player.service.VersionService versions,
            org.airsonic.player.service.SettingsService settingsService) {
        if (serverInfoService == null) {
            serverInfoService = new org.airsonic.player.service.ServerInfoService(versions, settingsService);
        }
        return serverInfoService;
    }

    public synchronized org.airsonic.player.service.StatusService createStatusService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.MediaFolderService mediaFolderService,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (statusService == null) {
            statusService = new org.airsonic.player.service.StatusService(
                    mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
            statusService.cleanup();
        }
        return statusService;
    }

    public synchronized org.airsonic.player.service.PasswordRecoveryService createPasswordRecoveryService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.SecurityService securityService) {
        if (passwordRecoveryService == null) {
            passwordRecoveryService = new org.airsonic.player.service.PasswordRecoveryService(
                    settingsService, securityService);
        }
        return passwordRecoveryService;
    }

    public synchronized org.airsonic.player.service.LyricsService createLyricsService() {
        if (lyricsService == null) {
            lyricsService = new org.airsonic.player.service.LyricsService();
        }
        return lyricsService;
    }

    public synchronized org.airsonic.player.service.BookmarkService createBookmarkService(
            org.airsonic.player.dao.BookmarkDao bookmarkDao,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (bookmarkService == null) {
            bookmarkService = new org.airsonic.player.service.BookmarkService(bookmarkDao, mediaFileService);
        }
        return bookmarkService;
    }

    public synchronized org.airsonic.player.service.RatingService createRatingService(
            org.airsonic.player.dao.RatingDao ratingDao,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (ratingService == null) {
            ratingService = new org.airsonic.player.service.RatingService(ratingDao, securityService, mediaFileService);
        }
        return ratingService;
    }

    public synchronized org.airsonic.player.service.MediaDirectoryService createMediaDirectoryService(
            org.airsonic.player.service.MediaFileService mediaFileService,
            org.airsonic.player.service.RatingService ratingService,
            org.airsonic.player.service.SecurityService securityService,
            org.airsonic.player.service.SettingsService settingsService) {
        if (mediaDirectoryService == null) {
            mediaDirectoryService = new org.airsonic.player.service.MediaDirectoryService(
                    mediaFileService, ratingService, securityService, settingsService);
        }
        return mediaDirectoryService;
    }

    public synchronized org.airsonic.player.service.MusicIndexService createMusicIndexService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFileService mediaFileService) {
        if (musicIndexService == null) {
            musicIndexService = new org.airsonic.player.service.MusicIndexService(settingsService, mediaFileService);
        }
        return musicIndexService;
    }

    public synchronized org.airsonic.player.service.SecurityService createSecurityService(
            org.airsonic.player.dao.UserDao userDao,
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.service.MediaFolderService mediaFolderService) {
        if (securityService == null) {
            securityService = new org.airsonic.player.service.SecurityService(
                    userDao, settingsService, mediaFolderService, createCacheManager());
        }
        return securityService;
    }

    public synchronized org.airsonic.player.service.JWTSecurityService createJwtSecurityService(
            org.airsonic.player.service.SettingsService settingsService) {
        if (jwtSecurityService == null) {
            jwtSecurityService = new org.airsonic.player.service.JWTSecurityService(settingsService);
        }
        return jwtSecurityService;
    }

    public synchronized org.airsonic.player.service.DatabaseService createDatabaseService(
            org.airsonic.player.service.SettingsService settingsService,
            org.airsonic.player.dao.DatabaseDao databaseDao,
            org.airsonic.player.service.TaskSchedulingService taskSchedulingService) {
        if (databaseService == null) {
            databaseService = new org.airsonic.player.service.DatabaseService(
                    settingsService, databaseDao, taskSchedulingService);
            databaseService.init();
        }
        return databaseService;
    }
}
