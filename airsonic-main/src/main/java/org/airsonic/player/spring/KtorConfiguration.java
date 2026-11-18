package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.TagEditService;
import org.airsonic.player.service.UPnPService;
import org.airsonic.player.service.upnp.AlbumUpnpProcessor;
import org.airsonic.player.service.upnp.ArtistUpnpProcessor;
import org.airsonic.player.service.upnp.DispatchingContentDirectory;
import org.airsonic.player.service.upnp.FolderBasedContentDirectory;
import org.airsonic.player.service.upnp.GenreUpnpProcessor;
import org.airsonic.player.service.upnp.MediaFileUpnpProcessor;
import org.airsonic.player.service.upnp.PlaylistUpnpProcessor;
import org.airsonic.player.service.upnp.RecentAlbumUpnpProcessor;
import org.airsonic.player.service.upnp.RootUpnpProcessor;
import org.airsonic.player.security.CsrfSecurityRequestMatcher;
import org.airsonic.player.security.GlobalSecurityConfig;
import org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider;
import org.airsonic.player.theme.CustomThemeResolver;
import org.airsonic.player.theme.CustomThemeSource;
import org.airsonic.player.controller.rest.tags.MediaFileCommentController;
import org.airsonic.player.controller.rest.tags.TagEditController;
import org.airsonic.player.controller.rest.tags.TagEditorPageController;
import org.airsonic.player.service.CoverArtSearchService;
import org.airsonic.player.controller.rest.cover.CoverArtActionsController;
import org.airsonic.player.controller.rest.settings.WebsiteSettingsController;
import org.airsonic.player.controller.rest.livequeue.LiveQueueController;
import org.airsonic.player.controller.rest.livequeue.LiveQueueExportController;
import org.airsonic.player.controller.rest.livequeue.RandomQueueController;
import org.airsonic.player.service.MediaDirectoryService;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.service.MusicIndexStateService;
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.airsonic.player.controller.rest.shares.PublicShareController;
import org.airsonic.player.controller.captions.CaptionsRestController;
import org.airsonic.player.controller.rest.video.VideoPlayerRestController;
import org.airsonic.player.controller.rest.recovery.PasswordRecoveryController;
import org.airsonic.player.service.PasswordRecoveryService;
import org.airsonic.player.service.ServerInfoService;
import org.airsonic.player.controller.rest.server.ServerInfoController;
import org.airsonic.player.service.VideoPlaybackService;
import org.airsonic.player.service.CaptionService;
import org.airsonic.player.controller.rest.hls.HlsRestController;
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController;
import org.airsonic.player.service.hls.HlsSegmentService;
import org.airsonic.player.controller.rest.stream.StreamRestController;
import org.airsonic.player.service.MediaStreamService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.dao.PlayerDaoPlayQueueFactory;
import org.airsonic.player.dao.AvatarDao;
import org.airsonic.player.dao.BookmarkDao;
import org.airsonic.player.dao.CoverArtDao;
import org.airsonic.player.dao.DatabaseDao;
import org.airsonic.player.dao.MusicFolderDao;
import org.airsonic.player.dao.PlayerDao;
import org.airsonic.player.dao.PlaylistDao;
import org.airsonic.player.controller.rest.cover.CoverArtRestController;
import org.airsonic.player.controller.rest.avatar.AvatarRestController;
import org.airsonic.player.controller.rest.proxy.ProxyRestController;
import org.airsonic.player.controller.rest.login.LoginPageController;
import org.airsonic.player.controller.rest.login.AccessDeniedPageController;
import org.airsonic.player.controller.rest.error.NotFoundPageController;
import org.airsonic.player.controller.rest.avatar.AvatarUploadController;
import org.airsonic.player.controller.rest.indexes.IndexesController;
import org.airsonic.player.controller.rest.music.genre.SongsByGenreController;
import org.airsonic.player.controller.rest.music.top.TopSongsController;
import org.airsonic.player.controller.rest.music.directory.MusicDirectoryController;
import org.airsonic.player.controller.rest.music.random.RandomSongsController;
import org.airsonic.player.controller.rest.music.song.SongController;
import org.airsonic.player.controller.rest.videos.VideosController;
import org.airsonic.player.controller.rest.lyrics.LyricsController;
import org.airsonic.player.controller.rest.rating.RatingController;
import org.airsonic.player.controller.rest.scrobble.ScrobbleController;
import org.airsonic.player.controller.rest.nowplaying.NowPlayingController;
import org.airsonic.player.controller.rest.nowplaying.NowPlayingPageController;
import org.airsonic.player.controller.rest.nowplaying.RightPageController;
import org.airsonic.player.controller.rest.more.MorePageController;
import org.airsonic.player.controller.rest.indexes.LeftPageController;
import org.airsonic.player.controller.rest.indexes.IndexPageController;
import org.airsonic.player.controller.rest.bookmarks.BookmarksPageController;
import org.airsonic.player.controller.rest.playlists.PlaylistPageController;
import org.airsonic.player.controller.rest.music.MainPageController;
import org.airsonic.player.controller.rest.playlists.PlaylistsPageController;
import org.airsonic.player.controller.rest.upload.UploadStatusController;
import org.airsonic.player.controller.rest.statistics.StatisticsController;
import org.airsonic.player.controller.rest.statistics.ScheduledTasksController;
import org.airsonic.player.controller.rest.statistics.PathWatchersController;
import org.airsonic.player.controller.rest.statistics.ServerHealthController;
import org.airsonic.player.controller.rest.statistics.CacheStatisticsController;
import org.airsonic.player.controller.rest.download.DownloadRestController;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.service.TaskSchedulingService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.DownloadService;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.CoverArtImageService;
import org.airsonic.player.service.CoverArtAccentColorBackfill;
import org.airsonic.player.service.DatabaseService;
import org.airsonic.player.controller.rest.playqueue.PlayQueueController;
import org.airsonic.player.controller.rest.playqueue.PlayQueuePageController;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.service.UserAdministrationService;
import org.airsonic.player.controller.rest.users.UsersController;
import org.airsonic.player.controller.rest.admin.MusicFolderSettingsController;
import org.airsonic.player.controller.rest.admin.GeneralSettingsController;
import org.airsonic.player.controller.rest.admin.TranscodingSettingsController;
import org.airsonic.player.controller.rest.admin.AdvancedSettingsController;
import org.airsonic.player.controller.rest.admin.PlayerSettingsController;
import org.airsonic.player.controller.rest.settings.PersonalSettingsController;
import org.airsonic.player.controller.rest.admin.DatabaseSettingsController;
import org.airsonic.player.controller.rest.admin.DLNASettingsController;
import org.airsonic.player.service.UPnPService;
import org.airsonic.player.controller.rest.settings.CredentialsManagementController;
import org.airsonic.player.service.GeneralSettingsAdministrationService;
import org.airsonic.player.service.MusicFolderAdministrationService;
import org.airsonic.player.controller.rest.users.PasswordController;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.runtime.RuntimeCompositionRoot;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.jukebox.JukeboxController;
import org.airsonic.player.controller.rest.starred.StarredController;
import org.airsonic.player.controller.rest.starred.StarredPageController;
import org.airsonic.player.controller.rest.bookmarks.BookmarksController;
import org.airsonic.player.controller.rest.albums.AlbumsController;
import org.airsonic.player.controller.rest.info.album.AlbumInfoController;
import org.airsonic.player.controller.rest.artists.ArtistsController;
import org.airsonic.player.controller.rest.info.artist.ArtistInfoController;
import org.airsonic.player.controller.rest.genres.GenresController;
import org.airsonic.player.controller.rest.music.folder.MusicFoldersController;
import org.airsonic.player.controller.rest.ping.PingController;
import org.airsonic.player.controller.rest.playlists.PlaylistsController;
import org.airsonic.player.controller.rest.upload.UploadController;
import org.airsonic.player.service.UploadFileService;
import org.airsonic.player.controller.rest.playlists.PlaylistExportController;
import org.airsonic.player.controller.rest.playlists.PlaylistImportController;
import org.airsonic.player.controller.rest.scan.ScanController;
import org.airsonic.player.controller.rest.shares.SharesController;
import org.airsonic.player.controller.rest.search.SearchController;
import org.airsonic.player.controller.rest.search.SearchPageController;
import org.airsonic.player.controller.rest.home.HomePageController;
import org.airsonic.player.controller.rest.cover.ChangeCoverArtPageController;
import org.airsonic.player.controller.rest.music.similar.SimilarSongsController;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.RatingDao;
import org.airsonic.player.dao.ShareDao;
import org.airsonic.player.dao.TranscodingDao;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.service.search.AnalyzerFactory;
import org.airsonic.player.service.search.DocumentFactory;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.service.search.QueryFactory;
import org.airsonic.player.service.search.SearchServiceImpl;
import org.airsonic.player.service.search.SearchServiceUtilities;
import org.airsonic.player.service.playlist.DefaultPlaylistExportHandler;
import org.airsonic.player.service.playlist.DefaultPlaylistImportHandler;
import org.airsonic.player.service.playlist.XspfPlaylistExportHandler;
import org.airsonic.player.service.playlist.XspfPlaylistImportHandler;
import org.airsonic.player.service.metadata.DefaultMetaDataParser;
import org.airsonic.player.service.metadata.FFmpegParser;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.JukeboxService;
import org.airsonic.player.service.JukeboxLegacySubsonicService;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.ShareService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.MessageSource;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Connects Ktor to the existing servlet container during the migration. */
@Configuration
public class KtorConfiguration {

    @Bean
    public javax.cache.CacheManager jCacheCacheManager() {
        return new org.airsonic.player.runtime.CacheFactory().createCacheManager();
    }

    @Bean
    public LegacyViewSuffixHandlerMapping legacyViewSuffixHandlerMapping() {
        return new LegacyViewSuffixHandlerMapping();
    }

    @Bean(destroyMethod = "close")
    public org.airsonic.player.monitor.MetricsManager metricsManager(RuntimeCompositionRoot runtime) {
        org.airsonic.player.monitor.MetricsManager manager =
                new org.airsonic.player.monitor.MetricsManager(runtime.getConfiguration());
        runtime.getLifecycleRegistry().registerShutdown("metricsManager", manager);
        return manager;
    }

    @Bean
    public WebMvcConfigurer servletMvcCompatibility(MessageSource messageSource) {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                // Spring 6 changed trailing-slash matching; preserve existing URL behavior during migration.
                configurer.setUseTrailingSlashMatch(true);
            }

            @Override
            public Validator getValidator() {
                LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
                factory.setValidationMessageSource(messageSource);
                return factory;
            }
        };
    }

    @Bean
    public JAXBWriter subsonicJaxbWriter() {
        return new JAXBWriter();
    }

    @Bean
    public RestPlayerResolver restPlayerResolver(PlayerService playerService) {
        return new RestPlayerResolver(playerService);
    }

    @Bean
    public org.airsonic.player.controller.rest.genres.GenresService subsonicGenresService(MediaFileDao mediaFileDao) {
        return new org.airsonic.player.controller.rest.genres.GenresService(() -> mediaFileDao.getGenres(false));
    }

    @Bean
    public GenresController subsonicGenresController(
            org.airsonic.player.controller.rest.genres.GenresService genresService, JAXBWriter subsonicJaxbWriter) {
        return new GenresController(genresService, subsonicJaxbWriter);
    }

    @Bean
    public ArtistsController subsonicArtistsController(
            ArtistDao artistDao, AlbumDao albumDao, MusicIndexService musicIndexService, SettingsService settingsService,
            MediaFolderService mediaFolderService, SubsonicResponseMapper subsonicResponseMapper,
            JAXBWriter subsonicJaxbWriter) {
        return new ArtistsController(artistDao, albumDao, musicIndexService, settingsService, mediaFolderService,
                subsonicResponseMapper, subsonicJaxbWriter);
    }

    @Bean
    public org.airsonic.player.controller.rest.music.folder.MusicFoldersService subsonicMusicFoldersService(
            MediaFolderService mediaFolderService) {
        return new org.airsonic.player.controller.rest.music.folder.MusicFoldersService(
                mediaFolderService::getMusicFoldersForUser);
    }

    @Bean
    public MusicFoldersController subsonicMusicFoldersController(
            org.airsonic.player.controller.rest.music.folder.MusicFoldersService musicFoldersService,
            JAXBWriter subsonicJaxbWriter) {
        return new MusicFoldersController(musicFoldersService, subsonicJaxbWriter);
    }

    @Bean
    public SubsonicResponseMapper subsonicResponseMapper(
            MediaFileService mediaFileService, MediaFileDao mediaFileDao, RatingService ratingService,
            CoverArtService coverArtService, TranscodingService transcodingService, AlbumDao albumDao,
            ArtistDao artistDao, JAXBWriter subsonicJaxbWriter) {
        return new SubsonicResponseMapper(mediaFileService, mediaFileDao, ratingService, coverArtService,
                transcodingService, albumDao, artistDao, subsonicJaxbWriter);
    }

    @Bean
    public SearchController subsonicSearchController(
            org.airsonic.player.controller.rest.search.SearchService searchService, JAXBWriter subsonicJaxbWriter) {
        return new SearchController(searchService, subsonicJaxbWriter);
    }

    @Bean
    public org.airsonic.player.controller.rest.search.SearchService subsonicSearchService(
            SearchService searchService, MediaFolderService mediaFolderService,
            SubsonicResponseMapper subsonicResponseMapper, JAXBWriter subsonicJaxbWriter) {
        return new org.airsonic.player.controller.rest.search.SearchService(
                searchService, mediaFolderService, subsonicResponseMapper, subsonicJaxbWriter);
    }

    @Bean
    public org.springframework.boot.web.server.ErrorPageRegistrar ktorErrorPageRegistrar(
            org.springframework.boot.autoconfigure.web.ServerProperties serverProperties) {
        return factory -> factory.addErrorPages(new org.springframework.boot.web.server.ErrorPage(serverProperties.getError().getPath()));
    }

    // Keep the health contributors independent of Actuator HTTP endpoint exposure.
    @Bean
    public org.airsonic.player.health.HealthContributorRegistry serverHealthContributors(
            javax.sql.DataSource dataSource,
            SettingsService settingsService) {
        org.airsonic.player.health.DefaultHealthContributorRegistry registry =
                new org.airsonic.player.health.DefaultHealthContributorRegistry();
        registry.registerContributor("db", new org.airsonic.player.health.DatabaseHealthIndicator(dataSource));
        registry.registerContributor("diskSpace", new org.airsonic.player.health.DiskSpaceHealthIndicator(
                settingsService.getAirsonicHome()));
        registry.registerContributor("ping", new org.airsonic.player.health.PingHealthIndicator());
        return registry;
    }

    @Bean
    public RuntimeCompositionRoot runtimeCompositionRoot(org.springframework.core.env.Environment env) {
        return new RuntimeCompositionRoot(org.airsonic.player.runtime.RuntimeConfigurationLoader.fromSpringEnvironment(env));
    }

    @Bean
    public org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry(RuntimeCompositionRoot runtime) {
        org.airsonic.player.runtime.LifecycleRegistry registry = runtime.getLifecycleRegistry();
        Runtime.getRuntime().addShutdownHook(new Thread(registry::shutdown, "airsonic-lifecycle-shutdown"));
        return registry;
    }

    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationReadyEvent>
            runtimeLifecycleStartupListener(org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry) {
        return event -> lifecycleRegistry.start();
    }

    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent>
            authenticationFailureListener(GlobalSecurityConfig securityConfig) {
        return securityConfig::loginFailureListener;
    }

    @Bean
    public CsrfSecurityRequestMatcher csrfSecurityRequestMatcher() {
        return new CsrfSecurityRequestMatcher();
    }

    @Bean
    public MultipleCredsMatchingAuthenticationProvider multipleCredsMatchingAuthenticationProvider(
            SecurityService securityService, @Lazy org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        MultipleCredsMatchingAuthenticationProvider provider = new MultipleCredsMatchingAuthenticationProvider();
        provider.setSecurityService(securityService);
        provider.setUserDetailsService(securityService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean(name = "themeResolver")
    public CustomThemeResolver customThemeResolver(SecurityService securityService, SettingsService settingsService) {
        CustomThemeResolver resolver = new CustomThemeResolver();
        resolver.setSecurityService(securityService);
        resolver.setSettingsService(settingsService);
        return resolver;
    }

    @Bean(name = org.springframework.ui.context.support.UiApplicationContextUtils.THEME_SOURCE_BEAN_NAME)
    public CustomThemeSource customThemeSource(SettingsService settingsService) {
        CustomThemeSource source = new CustomThemeSource();
        source.setBasenamePrefix("org.airsonic.player.theme.");
        source.setSettingsService(settingsService);
        return source;
    }

    @Bean
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public LoggingExceptionResolver loggingExceptionResolver() {
        return new LoggingExceptionResolver();
    }

    @Bean
    public org.airsonic.player.dao.TransactionManager airsonicTransactionManager(javax.sql.DataSource dataSource) {
        return new org.airsonic.player.dao.TransactionManager(dataSource);
    }

    @Bean
    public org.airsonic.player.dao.JdbcHelper jdbcHelper(javax.sql.DataSource dataSource,
            org.airsonic.player.dao.TransactionManager transactionManager) {
        return new org.airsonic.player.dao.JdbcHelper(dataSource, transactionManager);
    }

    @Bean
    public AlbumDao albumDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new AlbumDao(jdbcHelper);
    }

    @Bean
    public ArtistDao artistDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new ArtistDao(jdbcHelper);
    }

    @Bean
    public AvatarDao avatarDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new AvatarDao(jdbcHelper);
    }

    @Bean
    public BookmarkDao bookmarkDao(org.airsonic.player.dao.JdbcHelper jdbcHelper,
            org.airsonic.player.dao.TransactionManager transactionManager) throws Exception {
        BookmarkDao dao = new BookmarkDao(jdbcHelper, transactionManager);
        dao.register();
        return dao;
    }

    @Bean
    public CoverArtDao coverArtDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        CoverArtDao dao = new CoverArtDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public DatabaseDao databaseDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new DatabaseDao(jdbcHelper);
    }

    @Bean
    public MediaFileDao mediaFileDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new MediaFileDao(jdbcHelper);
    }

    @Bean
    public MusicFolderDao musicFolderDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        MusicFolderDao dao = new MusicFolderDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public PlayQueueDao playQueueDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        PlayQueueDao dao = new PlayQueueDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public PlayerDao playerDao(PlayerDaoPlayQueueFactory playQueueFactory,
            org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        PlayerDao dao = new PlayerDao(playQueueFactory, jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public PlaylistDao playlistDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        PlaylistDao dao = new PlaylistDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean("musicFileInfoDao")
    public RatingDao ratingDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new RatingDao(jdbcHelper);
    }

    @Bean
    public ShareDao shareDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        ShareDao dao = new ShareDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public TranscodingDao transcodingDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) throws Exception {
        TranscodingDao dao = new TranscodingDao(jdbcHelper);
        dao.register();
        return dao;
    }

    @Bean
    public UserDao userDao(org.airsonic.player.dao.JdbcHelper jdbcHelper) {
        return new UserDao(jdbcHelper);
    }

    @Bean
    public PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory(MediaFolderService mediaFolderService) {
        return new PlayerDaoPlayQueueFactory(mediaFolderService);
    }

    @Bean
    public VersionService versionService() throws java.io.IOException {
        return new VersionService();
    }

    @Bean
    public ServerInfoService serverInfoService(VersionService versionService, SettingsService settingsService) {
        return new ServerInfoService(versionService, settingsService);
    }

    @Bean
    public LocaleResolver localeResolver(SecurityService securityService, SettingsService settingsService) {
        return new LocaleResolver(securityService, settingsService);
    }

    @Bean
    public StatusService statusService(MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            SettingsService settingsService, TaskSchedulingService taskSchedulingService) {
        StatusService service = new StatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
        service.cleanup();
        return service;
    }

    @Bean
    public AnalyzerFactory analyzerFactory() {
        return new AnalyzerFactory();
    }

    @Bean
    public DocumentFactory documentFactory() {
        return new DocumentFactory();
    }

    @Bean
    public QueryFactory queryFactory(AnalyzerFactory analyzerFactory) {
        return new QueryFactory(analyzerFactory);
    }

    @Bean
    public SearchServiceUtilities searchServiceUtilities(ArtistDao artistDao, AlbumDao albumDao,
            MediaFileService mediaFileService) {
        return new SearchServiceUtilities(artistDao, albumDao, mediaFileService);
    }

    @Bean
    public IndexManager indexManager(AnalyzerFactory analyzerFactory, DocumentFactory documentFactory,
            MediaFileDao mediaFileDao, ArtistDao artistDao, AlbumDao albumDao) {
        return new IndexManager(analyzerFactory, documentFactory, mediaFileDao, artistDao, albumDao);
    }

    @Bean
    public SearchServiceImpl searchServiceImpl(QueryFactory queryFactory, IndexManager indexManager,
            SearchServiceUtilities searchServiceUtilities) {
        return new SearchServiceImpl(queryFactory, indexManager, searchServiceUtilities);
    }

    @Bean
    public PasswordRecoveryService passwordRecoveryService(SettingsService settingsService,
            SecurityService securityService) {
        return new PasswordRecoveryService(settingsService, securityService);
    }

    @Bean
    public DefaultPlaylistExportHandler defaultPlaylistExportHandler(MediaFileDao mediaFileDao,
            MediaFolderService mediaFolderService) {
        return new DefaultPlaylistExportHandler(mediaFileDao, mediaFolderService);
    }

    @Bean
    public DefaultPlaylistImportHandler defaultPlaylistImportHandler(MediaFileService mediaFileService,
            SettingsService settingsService) {
        return new DefaultPlaylistImportHandler(mediaFileService, settingsService);
    }

    @Bean
    public XspfPlaylistExportHandler xspfPlaylistExportHandler(MediaFileDao mediaFileDao, PlaylistDao playlistDao,
            MediaFolderService mediaFolderService, CoverArtService coverArtService) {
        return new XspfPlaylistExportHandler(mediaFileDao, playlistDao, mediaFolderService, coverArtService);
    }

    @Bean
    public XspfPlaylistImportHandler xspfPlaylistImportHandler(MediaFileService mediaFileService,
            SettingsService settingsService) {
        return new XspfPlaylistImportHandler(mediaFileService, settingsService);
    }

    @Bean
    public PlaylistService playlistService(MediaFileDao mediaFileDao, PlaylistDao playlistDao,
            SecurityService securityService, SettingsService settingsService,
            java.util.List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers,
            java.util.List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers,
            org.airsonic.player.service.PathWatcherService pathWatcherService,
            javax.cache.CacheManager cacheManager) throws java.io.IOException {
        PlaylistService service = new PlaylistService(mediaFileDao, playlistDao, securityService, settingsService,
                exportHandlers, importHandlers, pathWatcherService, cacheManager);
        service.init();
        return service;
    }

    @Bean
    public DefaultMetaDataParser defaultMetaDataParser(MediaFolderService mediaFolderService) {
        return new DefaultMetaDataParser(mediaFolderService);
    }

    @Bean
    public FFmpegParser ffmpegParser(MediaFolderService mediaFolderService) {
        return new FFmpegParser(mediaFolderService);
    }

    @Bean
    public JaudiotaggerParser jaudiotaggerParser(MediaFolderService mediaFolderService) {
        return new JaudiotaggerParser(mediaFolderService);
    }

    @Bean
    public MetaDataParserFactory metaDataParserFactory(DefaultMetaDataParser defaultMetaDataParser,
            FFmpegParser ffmpegParser, JaudiotaggerParser jaudiotaggerParser) {
        return new MetaDataParserFactory(new java.util.ArrayList<MetaDataParser>(
                java.util.List.of(jaudiotaggerParser, ffmpegParser, defaultMetaDataParser)));
    }

    @Bean
    public HlsPlaylistService hlsPlaylistService(JWTSecurityService jwtSecurityService) {
        return new HlsPlaylistService(jwtSecurityService);
    }

    @Bean
    public HlsSegmentService hlsSegmentService(MediaFileService mediaFileService, SecurityService securityService,
            StatusService statusService, SettingsService settingsService, TranscodingService transcodingService) {
        HlsSegmentService service = new HlsSegmentService(mediaFileService, securityService, statusService,
                settingsService, transcodingService);
        service.init();
        return service;
    }

    @Bean
    public CoverArtService coverArtService(CoverArtDao coverArtDao, MediaFolderService mediaFolderService,
            JaudiotaggerParser jaudiotaggerParser, javax.cache.CacheManager cacheManager) {
        return new CoverArtService(coverArtDao, mediaFolderService, jaudiotaggerParser, cacheManager);
    }

    @Bean
    public CoverArtImageService coverArtImageService(MediaFileService mediaFileService,
            CoverArtService coverArtService, TranscodingService transcodingService, SettingsService settingsService,
            PlaylistService playlistService, ArtistDao artistDao, AlbumDao albumDao,
            JaudiotaggerParser jaudiotaggerParser) {
        CoverArtImageService service = new CoverArtImageService(mediaFileService, coverArtService, transcodingService,
                settingsService, playlistService, artistDao, albumDao, jaudiotaggerParser);
        service.init();
        return service;
    }

    @Bean
    public CoverArtSearchService coverArtSearchService(SecurityService securityService,
            MediaFileService mediaFileService, LastFmService lastFmService, MediaFolderService mediaFolderService,
            CoverArtService coverArtService) {
        return new CoverArtSearchService(securityService, mediaFileService, lastFmService, mediaFolderService,
                coverArtService);
    }

    @Bean
    public CoverArtAccentColorBackfill coverArtAccentColorBackfill(CoverArtService coverArtService,
            org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry) {
        CoverArtAccentColorBackfill service = new CoverArtAccentColorBackfill(coverArtService);
        lifecycleRegistry.registerStartup("coverArtAccentColorBackfill", service::backfillAccentColors);
        return service;
    }

    @Bean
    public TagEditService tagEditService(MetaDataParserFactory metaDataParserFactory,
            MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            SecurityService securityService) {
        return new TagEditService(metaDataParserFactory, mediaFileService, mediaFolderService, securityService);
    }

    @Bean
    public AlbumUpnpProcessor albumUpnpProcessor(AlbumDao albumDao) {
        AlbumUpnpProcessor processor = new AlbumUpnpProcessor();
        processor.setAlbumDao(albumDao);
        return processor;
    }

    @Bean
    public RecentAlbumUpnpProcessor recentAlbumUpnpProcessor(AlbumDao albumDao) {
        RecentAlbumUpnpProcessor processor = new RecentAlbumUpnpProcessor();
        processor.setAlbumDao(albumDao);
        return processor;
    }

    @Bean
    public ArtistUpnpProcessor artistUpnpProcessor(ArtistDao artistDao) {
        ArtistUpnpProcessor processor = new ArtistUpnpProcessor();
        processor.setArtistDao(artistDao);
        return processor;
    }

    @Bean
    public GenreUpnpProcessor genreUpnpProcessor() {
        return new GenreUpnpProcessor();
    }

    @Bean
    public MediaFileUpnpProcessor mediaFileUpnpProcessor(MediaFileDao mediaFileDao) {
        MediaFileUpnpProcessor processor = new MediaFileUpnpProcessor();
        processor.setMediaFileDao(mediaFileDao);
        return processor;
    }

    @Bean
    public PlaylistUpnpProcessor playlistUpnpProcessor(PlaylistService playlistService) {
        PlaylistUpnpProcessor processor = new PlaylistUpnpProcessor();
        processor.setPlaylistService(playlistService);
        return processor;
    }

    @Bean
    public RootUpnpProcessor rootUpnpProcessor(IndexManager indexManager) {
        return new RootUpnpProcessor(indexManager);
    }

    @Bean
    public DispatchingContentDirectory dispatchingContentDirectory(MediaFileUpnpProcessor mediaFileProcessor,
            PlaylistUpnpProcessor playlistProcessor,
            @org.springframework.beans.factory.annotation.Qualifier("albumUpnpProcessor")
            AlbumUpnpProcessor albumProcessor,
            RecentAlbumUpnpProcessor recentAlbumProcessor, ArtistUpnpProcessor artistProcessor,
            GenreUpnpProcessor genreProcessor, RootUpnpProcessor rootProcessor,
            MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            PlaylistService playlistService, SearchService searchService, MusicIndexService musicIndexService,
            SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        DispatchingContentDirectory directory = new DispatchingContentDirectory(mediaFileProcessor,
                playlistProcessor, albumProcessor, recentAlbumProcessor, artistProcessor, genreProcessor,
                rootProcessor, mediaFileService, mediaFolderService, playlistService, searchService,
                musicIndexService);
        directory.setSettingsService(settingsService);
        directory.setPlayerService(playerService);
        directory.setTranscodingService(transcodingService);
        directory.setJwtSecurityService(jwtSecurityService);
        mediaFileProcessor.setDispatchingContentDirectory(directory);
        playlistProcessor.setDispatchingContentDirectory(directory);
        albumProcessor.setDispatchingContentDirectory(directory);
        recentAlbumProcessor.setDispatchingContentDirectory(directory);
        artistProcessor.setDispatchingContentDirectory(directory);
        genreProcessor.setDispatchingContentDirectory(directory);
        rootProcessor.setDispatchingContentDirectory(directory);
        return directory;
    }

    @Bean
    public FolderBasedContentDirectory folderBasedContentDirectory(MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, PlaylistService playlistService, IndexManager indexManager,
            SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        FolderBasedContentDirectory directory = new FolderBasedContentDirectory(mediaFileService,
                mediaFolderService, playlistService, indexManager);
        directory.setSettingsService(settingsService);
        directory.setPlayerService(playerService);
        directory.setTranscodingService(transcodingService);
        directory.setJwtSecurityService(jwtSecurityService);
        return directory;
    }

    @Bean
    public UPnPService upnpService(SettingsService settingsService,
            @org.springframework.beans.factory.annotation.Qualifier("dispatchingContentDirectory")
            org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory,
            org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry) {
        UPnPService service = new UPnPService(settingsService, contentDirectory);
        service.init();
        lifecycleRegistry.registerShutdown("upnpService", service);
        return service;
    }

    @Bean
    public AudioScrobblerService audioScrobblerService(SettingsService settingsService,
            SecurityService securityService) {
        return new AudioScrobblerService(settingsService, securityService);
    }

    @Bean
    public BookmarkService bookmarkService(BookmarkDao bookmarkDao, MediaFileService mediaFileService) {
        return new BookmarkService(bookmarkDao, mediaFileService);
    }

    @Bean
    public LyricsService lyricsService() {
        return new LyricsService();
    }

    @Bean
    public RatingService ratingService(RatingDao ratingDao, SecurityService securityService,
            MediaFileService mediaFileService) {
        return new RatingService(ratingDao, securityService, mediaFileService);
    }

    @Bean
    public MediaDirectoryService mediaDirectoryService(MediaFileService mediaFileService,
            RatingService ratingService, SecurityService securityService, SettingsService settingsService) {
        return new MediaDirectoryService(mediaFileService, ratingService, securityService, settingsService);
    }

    @Bean
    public MusicIndexService musicIndexService(SettingsService settingsService, MediaFileService mediaFileService) {
        return new MusicIndexService(settingsService, mediaFileService);
    }

    @Bean
    public DatabaseService databaseService(SettingsService settingsService, DatabaseDao databaseDao,
            TaskSchedulingService taskSchedulingService) {
        DatabaseService service = new DatabaseService(settingsService, databaseDao, taskSchedulingService);
        service.init();
        return service;
    }

    @Bean
    public JWTSecurityService jwtSecurityService(SettingsService settingsService) {
        return new JWTSecurityService(settingsService);
    }

    @Bean
    public SecurityService securityService(UserDao userDao, SettingsService settingsService,
            MediaFolderService mediaFolderService, javax.cache.CacheManager cacheManager) {
        return new SecurityService(userDao, settingsService, mediaFolderService, cacheManager);
    }

    @Bean
    public org.airsonic.player.service.ConfigurationPropertiesService configurationPropertiesService() {
        return org.airsonic.player.service.ConfigurationPropertiesService.getInstance();
    }

    @Bean
    public SettingsService settingsService(UserDao userDao, AvatarDao avatarDao,
            org.springframework.core.env.Environment environment, javax.cache.CacheManager cacheManager,
            org.airsonic.player.service.ConfigurationPropertiesService properties) {
        SettingsService service = new SettingsService(userDao, avatarDao, environment, cacheManager, properties);
        service.init();
        return service;
    }

    @Bean
    public MediaScannerService mediaScannerService(PlaylistService playlistService, SettingsService settingsService,
            IndexManager indexManager, MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            CoverArtService coverArtService, MediaFileDao mediaFileDao, ArtistDao artistDao, AlbumDao albumDao,
            TaskSchedulingService taskSchedulingService, org.springframework.core.env.Environment environment) {
        int scannerParallelism = environment.getProperty("MediaScannerParallelism", Integer.class,
                Runtime.getRuntime().availableProcessors() + 1);
        MediaScannerService service = new MediaScannerService(playlistService, settingsService, indexManager, mediaFileService,
                mediaFolderService, coverArtService, mediaFileDao, artistDao, albumDao, taskSchedulingService,
                scannerParallelism);
        service.init();
        return service;
    }

    @Bean
    public PlayQueueService playQueueService(SearchService searchService, JukeboxService jukeboxService,
            SettingsService settingsService, MediaFileService mediaFileService, LastFmService lastFmService,
            RatingService ratingService, PlaylistService playlistService, MediaFileDao mediaFileDao,
            MediaFolderService mediaFolderService, PlayQueueDao playQueueDao,
            JWTSecurityService jwtSecurityService) {
        return new PlayQueueService(searchService, jukeboxService, settingsService, mediaFileService, lastFmService,
                ratingService, playlistService, mediaFileDao, mediaFolderService, playQueueDao, jwtSecurityService);
    }

    @Bean
    public JukeboxLegacySubsonicService jukeboxLegacySubsonicService(TranscodingService transcodingService,
            AudioScrobblerService audioScrobblerService, StatusService statusService,
            SettingsService settingsService, SecurityService securityService, MediaFileService mediaFileService) {
        return new JukeboxLegacySubsonicService(transcodingService, audioScrobblerService, statusService,
                settingsService, securityService, mediaFileService);
    }

    @Bean
    public JukeboxService jukeboxService(JukeboxLegacySubsonicService legacyJukebox) {
        return new JukeboxService(legacyJukebox);
    }

    @Bean
    public LastFmService lastFmService(MediaFileDao mediaFileDao, MediaFileService mediaFileService,
            ArtistDao artistDao) {
        LastFmService service = new LastFmService(mediaFileDao, mediaFileService, artistDao);
        service.init();
        return service;
    }

    @Bean
    public ShareService shareService(ShareDao shareDao, MediaFileService mediaFileService,
            JWTSecurityService jwtSecurityService) {
        return new ShareService(shareDao, mediaFileService, jwtSecurityService);
    }

    @Bean
    public UploadFileService uploadFileService(SecurityService securityService) {
        return new UploadFileService(securityService);
    }

    @Bean
    public CaptionService captionService(MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            SettingsService settingsService, MetaDataParserFactory metaDataParserFactory,
            JWTSecurityService jwtSecurityService) {
        return new CaptionService(mediaFileService, mediaFolderService, settingsService, metaDataParserFactory,
                jwtSecurityService);
    }

    @Bean
    public VideoPlaybackService videoPlaybackService(MediaFileService mediaFileService, CaptionService captionService,
            JWTSecurityService jwtSecurityService, BookmarkService bookmarkService, SettingsService settingsService) {
        return new VideoPlaybackService(mediaFileService, captionService, jwtSecurityService, bookmarkService,
                settingsService);
    }

    @Bean
    public MediaFolderService mediaFolderService(MusicFolderDao musicFolderDao, MediaFileDao mediaFileDao,
            javax.cache.CacheManager cacheManager) {
        return new MediaFolderService(musicFolderDao, mediaFileDao, cacheManager);
    }

    @Bean
    public MediaFileService mediaFileService(SecurityService securityService, SettingsService settingsService,
            MediaFileDao mediaFileDao, MediaFolderService mediaFolderService, AlbumDao albumDao,
            JaudiotaggerParser parser, MetaDataParserFactory metaDataParserFactory, CoverArtService coverArtService,
            LocaleResolver localeResolver, javax.cache.CacheManager cacheManager) {
        return new MediaFileService(securityService, settingsService, mediaFileDao, mediaFolderService, albumDao,
                parser, metaDataParserFactory, coverArtService, localeResolver, cacheManager);
    }

    @Bean
    public TranscodingService transcodingService(TranscodingDao transcodingDao, SettingsService settingsService,
            MediaFolderService mediaFolderService, @Lazy PlayerService playerService) {
        return new TranscodingService(transcodingDao, settingsService, mediaFolderService, playerService);
    }

    @Bean
    @DependsOn("liquibase")
    public PlayerService playerService(PlayerDao playerDao, StatusService statusService,
            SecurityService securityService, TranscodingService transcodingService) {
        PlayerService service = new PlayerService(playerDao, statusService, securityService, transcodingService);
        service.init();
        return service;
    }

    @Bean(destroyMethod = "shutdown")
    public TaskSchedulingService taskSchedulingService(org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry) {
        TaskSchedulingService service = new TaskSchedulingService();
        lifecycleRegistry.registerShutdown("taskSchedulingService", service);
        return service;
    }

    @Bean(destroyMethod = "close")
    public org.airsonic.player.service.PathWatcherService pathWatcherService(org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry) throws Exception {
        org.airsonic.player.service.PathWatcherService service = new org.airsonic.player.service.PathWatcherService();
        lifecycleRegistry.registerShutdown("pathWatcherService", service);
        return service;
    }

    @Bean
    public org.airsonic.player.health.StatusAggregator serverHealthStatusAggregator(
            RuntimeCompositionRoot runtime) {
        return new org.airsonic.player.health.SimpleStatusAggregator(
                runtime.getConfiguration().getHealthStatusOrder());
    }


    @Bean
    public DownloadService downloadService(StatusService statusService, SecurityService securityService,
            PlaylistService playlistService, SettingsService settingsService, MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, CoverArtService coverArtService) {
        return new DownloadService(statusService, securityService, playlistService, settingsService, mediaFileService,
                mediaFolderService, coverArtService);
    }

    @Bean
    public UserAdministrationService userAdministrationService(SecurityService securityService, SettingsService settingsService, MediaFolderService mediaFolderService) {
        return new UserAdministrationService(securityService, settingsService, mediaFolderService);
    }

    @Bean
    public MusicIndexStateService musicIndexStateService(MediaScannerService scanner, SettingsService settings, MediaFolderService folders) {
        return new MusicIndexStateService(scanner, settings, folders);
    }

    @Bean
    public MediaStreamService mediaStreamService(StatusService statusService, SecurityService securityService,
            SettingsService settingsService, TranscodingService transcodingService, AudioScrobblerService audioScrobblerService,
            MediaFileService mediaFileService, SearchService searchService, PlayerDaoPlayQueueFactory playQueueFactory) {
        return new MediaStreamService(statusService, securityService, settingsService, transcodingService,
                audioScrobblerService, mediaFileService, searchService, playQueueFactory);
    }

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration(
            SearchController searchController,
            MusicFoldersController musicFoldersController,
            GenresController genresController,
            ArtistsController artistsController,
            JAXBWriter jaxbWriter,
            JWTSecurityService jwtSecurityService,
            CaptionService captionService,
            VideoPlaybackService videoPlaybackService,
            PasswordRecoveryService passwordRecoveryService,
            ServerInfoService serverInfoService,
            org.airsonic.player.service.metadata.MetaDataParserFactory metadataParsers,
            MediaScannerService mediaScannerService,
            MediaFileService mediaFileService,
            ArtistDao artistDao,
            AlbumDao albumDao,
            MediaFileDao mediaFileDao,
            RatingService ratingService,
            SearchService albumSearchService,
            LastFmService lastFmService,
            MediaFolderService mediaFolderService,
            SubsonicResponseMapper responseMapper,
            RestPlayerResolver playerResolver,
            PlayerService playerService,
            PlaylistService playlistService,
            ShareService shareService,
            LyricsService lyricsService,
            CoverArtSearchService coverArtSearchService,
            TagEditService tagEditService,
            MediaDirectoryService mediaDirectoryService,
            BookmarkService bookmarkService,
            JukeboxService jukeboxService,
            PlayQueueService playQueueService,
            PlayQueueDao playQueueDao,
            javax.sql.DataSource dataSource,
            SecurityService securityService,
            SettingsService settingsService,
            org.airsonic.player.service.ConfigurationPropertiesService properties,
            VersionService versionService,
            MusicIndexService musicIndexService,
            AudioScrobblerService audioScrobblerService,
            UPnPService upnpService,
            StatusService statusService,
            javax.cache.CacheManager jCacheCacheManager,
            org.airsonic.player.health.HealthContributorRegistry healthContributors,
            org.airsonic.player.health.StatusAggregator healthStatusAggregator,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            TaskSchedulingService taskSchedulingService,
            org.airsonic.player.service.PathWatcherService pathWatcherService,
            org.airsonic.player.service.DatabaseService databaseService,
            DownloadService downloadService,
            MusicIndexStateService musicIndexStateService,
            UserAdministrationService userAdministrationService,
            CoverArtService coverArtService,
            IndexManager indexManager,
            TranscodingService transcodingService,
            LocaleResolver localeResolver,
            CoverArtImageService coverArtImageService,
            HlsPlaylistService hlsPlaylistService,
            HlsSegmentService hlsSegmentService,
            MediaStreamService mediaStreamService,
            UploadFileService uploadFileService,
            org.springframework.boot.autoconfigure.web.ServerProperties serverProperties,
            org.airsonic.player.monitor.MetricsManager metricsManager,
            org.springframework.security.web.util.matcher.RequestMatcher csrfRequestMatcher,
            org.airsonic.player.theme.CustomThemeResolver themeResolver,
            org.airsonic.player.theme.CustomThemeSource themeSource,
            org.airsonic.player.dao.TransactionManager transactionManager
    ) {
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(
                        new AirsonicKtorServlet(searchController,
                                new SearchPageController(albumSearchService, securityService, mediaFolderService, settingsService,
                                        albumDao, mediaFileService, objectMapper),
                                musicFoldersController, genresController,
                                artistsController, new PingController(jaxbWriter), new ScanController(mediaScannerService, jaxbWriter),
                                new SimilarSongsController(mediaFileService, artistDao, lastFmService,
                                        mediaFolderService, responseMapper, jaxbWriter),
                                new ArtistInfoController(mediaFileService, artistDao, lastFmService,
                                        mediaFolderService, responseMapper, jaxbWriter, securityService),
                                new AlbumInfoController(mediaFileService, albumDao, lastFmService, jaxbWriter),
                                new PlaylistsController(playlistService, mediaFileService, securityService, responseMapper, jaxbWriter,
                                        mediaFileDao, mediaFolderService, playerService, localeResolver),
                                new UploadController(uploadFileService, statusService, securityService, settingsService, jaxbWriter),
                                new PlaylistExportController(playlistService, jaxbWriter),
                                new PlaylistImportController(playlistService, jaxbWriter),
                                new RandomQueueController(playQueueService, mediaFolderService, jaxbWriter),
                                new LiveQueueExportController(transcodingService, jwtSecurityService, securityService, jaxbWriter),
                                new SharesController(shareService, mediaFileService, mediaFolderService, responseMapper, jaxbWriter, playerService, playlistService, securityService),
                                new AlbumsController(albumDao, mediaFileDao, mediaFileService, mediaFolderService,
                                        ratingService, albumSearchService, responseMapper, jaxbWriter),
                                new BookmarksController(bookmarkService, mediaFileService, responseMapper, jaxbWriter),
                                new StarredController(mediaFileDao, albumDao, artistDao, mediaFolderService, responseMapper, jaxbWriter),
                                new StarredPageController(mediaFileDao, mediaFileService, mediaFolderService, securityService, settingsService, objectMapper),
                                new JukeboxController(playQueueService, jukeboxService, responseMapper, jaxbWriter),
                                new UsersController(securityService, settingsService, mediaFolderService, userAdministrationService, transcodingService, jaxbWriter),
                                new DLNASettingsController(settingsService, upnpService, jaxbWriter),
                                new DatabaseSettingsController(settingsService, databaseService, statusService, securityService, jaxbWriter,
                                        properties),
                                new CredentialsManagementController(securityService, settingsService, jaxbWriter, transactionManager),
                                new PersonalSettingsController(settingsService, securityService, jaxbWriter),
                                new PlayerSettingsController(playerService, transcodingService, playQueueService, jaxbWriter),
                                new AdvancedSettingsController(settingsService, jaxbWriter),
                                new TranscodingSettingsController(transcodingService, settingsService, jaxbWriter),
                                new GeneralSettingsController(new GeneralSettingsAdministrationService(settingsService, playlistService), jaxbWriter),
                                new MusicFolderSettingsController(new MusicFolderAdministrationService(settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao, indexManager, playlistService, coverArtService), jaxbWriter),
                                new PasswordController(securityService, jaxbWriter),
                                new PlayQueueController(playQueueDao, mediaFileService, responseMapper, jaxbWriter),
                                new IndexesController(settingsService, mediaFolderService, musicIndexService, musicIndexStateService, mediaFileDao, ratingService, responseMapper, jaxbWriter, playerService),
                                new SongsByGenreController(mediaFileDao, mediaFolderService, responseMapper, jaxbWriter),
                                new TopSongsController(lastFmService, mediaFolderService, responseMapper, jaxbWriter),
                                new MusicDirectoryController(mediaFileService, mediaFileDao, securityService, ratingService, responseMapper, jaxbWriter, mediaDirectoryService),
                                new RandomSongsController(albumSearchService, mediaFolderService, responseMapper, jaxbWriter),
                                new ScrobbleController(mediaFileService, statusService, audioScrobblerService, jaxbWriter),
                                new NowPlayingController(statusService, responseMapper, jaxbWriter),
                                new NowPlayingPageController(playerService, statusService, mediaFileService),
                                new RightPageController(settingsService, versionService, objectMapper),
                                new IndexPageController(settingsService, objectMapper),
                                new MorePageController(settingsService, mediaFileService, mediaFolderService, objectMapper),
                                new LeftPageController(settingsService, mediaFolderService, musicIndexService, musicIndexStateService, mediaScannerService, indexManager, playerService, objectMapper),
                                new BookmarksPageController(settingsService, objectMapper),
                                new PlaylistPageController(playlistService, securityService, settingsService, localeResolver, objectMapper),
                                new MainPageController(securityService, settingsService, objectMapper),
                                new PlaylistsPageController(settingsService, objectMapper),
                                new HomePageController(settingsService, mediaScannerService, ratingService, securityService,
                                        mediaFileService, mediaFolderService, albumSearchService, objectMapper),
                                new ChangeCoverArtPageController(mediaFileService, mediaFolderService),
                                new UploadStatusController(statusService, jaxbWriter, databaseService),
                                new StatisticsController(statusService, securityService, jaxbWriter),
                                new ScheduledTasksController(taskSchedulingService, jaxbWriter),
                                new PathWatchersController(pathWatcherService, jaxbWriter),
                                new ServerHealthController(healthContributors, healthStatusAggregator, objectMapper, jaxbWriter),
                                new CacheStatisticsController(jCacheCacheManager, meterRegistry, jaxbWriter),
                                new SongController(mediaFileDao, securityService, responseMapper, jaxbWriter),
                                new VideosController(mediaFileDao, mediaFolderService, responseMapper, jaxbWriter),
                                new LyricsController(lyricsService, jaxbWriter),
                                new CoverArtActionsController(coverArtSearchService, jaxbWriter),
                                new MediaFileCommentController(mediaFileService, securityService, jaxbWriter),
                                new TagEditController(tagEditService, jaxbWriter),
                                new TagEditorPageController(mediaFileService, mediaFolderService, securityService, objectMapper),
                                new WebsiteSettingsController(settingsService, jaxbWriter),
                                new LiveQueueController(playQueueService, jaxbWriter, mediaFileService, playlistService, securityService, playQueueDao, playerService),
                                new RatingController(mediaFileService, ratingService, jaxbWriter),
                                new DownloadRestController(jaxbWriter, downloadService, mediaFileService, playlistService, securityService), downloadService,
                                shareService, mediaFileService,
                                playerResolver, playerService, securityService, localeResolver,
                                new CoverArtRestController(coverArtImageService), new AvatarRestController(settingsService), new AvatarUploadController(settingsService, jaxbWriter),
                                new HlsRestController(hlsPlaylistService), new HlsSegmentRestController(hlsSegmentService), new StreamRestController(mediaStreamService),
                                new PublicShareController(shareService, mediaFileService, mediaFolderService, jwtSecurityService, metadataParsers, objectMapper), new CaptionsRestController(captionService, mediaFolderService, objectMapper),
                                new VideoPlayerRestController(videoPlaybackService, objectMapper), new PasswordRecoveryController(passwordRecoveryService, objectMapper), new ServerInfoController(serverInfoService, objectMapper, settingsService), new PlayQueuePageController(playerService, settingsService, objectMapper), new LoginPageController(securityService, settingsService), new AccessDeniedPageController(), jaxbWriter, serverProperties.getError(), objectMapper, metricsManager, settingsService, csrfRequestMatcher, themeResolver, themeSource),
                        "/internal/ktor/*");
        registration.addUrlMappings(SearchController.PATHS);
        registration.addUrlMappings(SearchPageController.DATA_PATH);
        registration.addUrlMappings(SearchPageController.PAGE_PATHS);
        registration.addUrlMappings(HomePageController.DATA_PATH);
        registration.addUrlMappings(HomePageController.PAGE_PATHS);
        registration.addUrlMappings("/lyrics", "/lyrics.view");
        registration.addUrlMappings("/changeCoverArt", "/changeCoverArt.view");
        registration.addUrlMappings(MusicFoldersController.PATHS);
        registration.addUrlMappings(GenresController.PATHS);
        registration.addUrlMappings(ArtistsController.PATHS);
        registration.addUrlMappings(PingController.PATHS);
        registration.addUrlMappings(ScanController.PATHS);
        registration.addUrlMappings(SimilarSongsController.PATHS);
        registration.addUrlMappings(ArtistInfoController.PATHS);
        registration.addUrlMappings(AlbumInfoController.PATHS);
        registration.addUrlMappings(PlaylistsController.PATHS);
        registration.addUrlMappings(SharesController.PATHS);
        registration.addUrlMappings("/createShare", "/createShare.view");
        registration.addUrlMappings(AlbumsController.PATHS);
        registration.addUrlMappings(BookmarksController.PATHS);
        registration.addUrlMappings(StarredController.PATHS);
        registration.addUrlMappings(StarredPageController.DATA_PATH);
        registration.addUrlMappings(StarredPageController.PAGE_PATHS);
        registration.addUrlMappings(JukeboxController.PATHS);
        registration.addUrlMappings(UsersController.PATHS);
        registration.addUrlMappings(UsersController.WEBSITE_PATHS);
        registration.addUrlMappings(UsersController.WEBSITE_READ_PATH);
        registration.addUrlMappings("/userSettings", "/userSettings.view", "/musicFolderSettings", "/musicFolderSettings.view");
        registration.addUrlMappings("/settings", "/settings.view", "/top", "/top.view", "/status", "/status.view");
        registration.addUrlMappings("/generalSettings", "/generalSettings.view");
        registration.addUrlMappings("/transcodingSettings", "/transcodingSettings.view", TranscodingSettingsController.READ_PATH, TranscodingSettingsController.SAVE_PATH);
        registration.addUrlMappings("/credentialsSettings", "/credentialsSettings.view", CredentialsManagementController.READ_PATH, CredentialsManagementController.KEYS_PATH, CredentialsManagementController.CREATE_PATH, CredentialsManagementController.UPDATE_PATH, CredentialsManagementController.ADMIN_PATH);
        registration.addUrlMappings("/dlnaSettings", "/dlnaSettings.view", DLNASettingsController.READ_PATH, DLNASettingsController.SAVE_PATH);
        registration.addUrlMappings(DatabaseSettingsController.PATHS);
        registration.addUrlMappings("/databaseSettings", "/databaseSettings.view");
        registration.addUrlMappings("/shareSettings", "/shareSettings.view");
        registration.addUrlMappings("/personalSettings", "/personalSettings.view", PersonalSettingsController.READ_PATH, PersonalSettingsController.SAVE_PATH);
        registration.addUrlMappings("/playerSettings", "/playerSettings.view", PlayerSettingsController.READ_PATH, PlayerSettingsController.SAVE_PATH, PlayerSettingsController.CLONE_PATH, PlayerSettingsController.DELETE_PATH);
        registration.addUrlMappings("/advancedSettings", "/advancedSettings.view", AdvancedSettingsController.READ_PATH, AdvancedSettingsController.SAVE_PATH);
        registration.addUrlMappings(GeneralSettingsController.READ_PATH);
        registration.addUrlMappings(GeneralSettingsController.WRITE_PATHS);
        registration.addUrlMappings(MusicFolderSettingsController.READ_PATH);
        registration.addUrlMappings(MusicFolderSettingsController.WRITE_PATHS);
        registration.addUrlMappings(PasswordController.PATHS);
        registration.addUrlMappings(PlayQueueController.PATHS);
        registration.addUrlMappings(PlayQueuePageController.PATHS);
        registration.addUrlMappings("/playQueue", "/playQueue.view");
        registration.addUrlMappings(IndexesController.PATHS);
        registration.addUrlMappings(SongsByGenreController.PATHS);
        registration.addUrlMappings(TopSongsController.PATHS);
        registration.addUrlMappings(MusicDirectoryController.PATHS);
        registration.addUrlMappings(RandomSongsController.PATHS);
        registration.addUrlMappings(SongController.PATHS);
        registration.addUrlMappings(VideosController.PATHS);
        registration.addUrlMappings(LyricsController.PATHS);
        registration.addUrlMappings(RatingController.PATHS);
        registration.addUrlMappings(ScrobbleController.PATHS);
        registration.addUrlMappings(NowPlayingController.PATHS);
        registration.addUrlMappings(UploadStatusController.PATHS);
        registration.addUrlMappings(StatisticsController.PATHS);
        registration.addUrlMappings(ScheduledTasksController.PATH);
        registration.addUrlMappings(PathWatchersController.PATH);
        registration.addUrlMappings(ServerHealthController.PATH);
        registration.addUrlMappings(CacheStatisticsController.PATHS);
        registration.addUrlMappings(DownloadRestController.PATHS);
        registration.addUrlMappings(DownloadRestController.WEBSITE_PATH);
        registration.addUrlMappings(CoverArtRestController.PATHS);
        registration.addUrlMappings(CoverArtRestController.LEGACY_PATHS);
        registration.addUrlMappings(CoverArtActionsController.PATHS);
        registration.addUrlMappings(MediaFileCommentController.PATH);
        registration.addUrlMappings(TagEditController.PATHS);
        registration.addUrlMappings(TagEditorPageController.PATH);
        registration.addUrlMappings(TagEditorPageController.PAGE_PATHS);
        registration.addUrlMappings(WebsiteSettingsController.PATHS);
        registration.addUrlMappings(LiveQueueController.PATHS);
        registration.addUrlMappings(RandomQueueController.PATH);
        registration.addUrlMappings(LiveQueueExportController.PATH);
        registration.addUrlMappings(AvatarRestController.PATHS);
        registration.addUrlMappings(AvatarRestController.LEGACY_PATHS);
        registration.addUrlMappings(ProxyRestController.PATHS);
        registration.addUrlMappings(AvatarUploadController.PATH);
        registration.addUrlMappings(UploadController.PATH);
        registration.addUrlMappings(PlaylistExportController.PATH);
        registration.addUrlMappings(PlaylistImportController.PATH, "/importPlaylist", "/importPlaylist.view");
        registration.addUrlMappings("/ext/share/*", "/ext/rest/share/*");
        registration.addUrlMappings(CaptionsRestController.PATHS);
        registration.addUrlMappings(ServerInfoController.PATHS);
        registration.addUrlMappings(ServerInfoController.GETTING_STARTED_PATH, "/gettingStarted", "/gettingStarted.view");
        registration.addUrlMappings(NowPlayingPageController.PATHS);
        registration.addUrlMappings(RightPageController.DATA_PATH);
        registration.addUrlMappings(RightPageController.PAGE_PATHS);
        registration.addUrlMappings(IndexPageController.DATA_PATH);
        registration.addUrlMappings(IndexPageController.PAGE_PATHS);
        registration.addUrlMappings(MorePageController.PATH);
        registration.addUrlMappings(MorePageController.PAGE_PATHS);
        registration.addUrlMappings(LeftPageController.DATA_PATH, LeftPageController.SELECT_PATH, "/left", LeftPageController.PAGE_PATH);
        registration.addUrlMappings(BookmarksPageController.DATA_PATH);
        registration.addUrlMappings(BookmarksPageController.PAGE_PATHS);
        registration.addUrlMappings(PlaylistPageController.DATA_PATH);
        registration.addUrlMappings(PlaylistPageController.PAGE_PATHS);
        registration.addUrlMappings(MainPageController.DATA_PATH);
        registration.addUrlMappings(MainPageController.PAGE_PATHS);
        registration.addUrlMappings(PlaylistsPageController.DATA_PATH);
        registration.addUrlMappings(PlaylistsPageController.PAGE_PATHS);
        registration.addUrlMappings("/help", "/help.view");
        registration.addUrlMappings(LoginPageController.PATHS);
        registration.addUrlMappings(AccessDeniedPageController.PATHS);
        registration.addUrlMappings(NotFoundPageController.PATHS);
        registration.addUrlMappings(PasswordRecoveryController.PATH, "/recover", "/recover.view");
        registration.addUrlMappings(VideoPlayerRestController.PATH, "/videoPlayer", "/videoPlayer.view");
        registration.addUrlMappings(HlsRestController.PATHS);
        registration.addUrlMappings(HlsRestController.WEBSITE_PATHS);
        registration.addUrlMappings("/hls/segment/*", "/ext/hls/segment/*");
        registration.addUrlMappings(HlsSegmentRestController.PATHS);
        registration.addUrlMappings(StreamRestController.PATHS);
        registration.addUrlMappings("/stream", "/stream/*", "/ext/stream", "/ext/stream/*");
        // Explicit mappings keep the migrated browser assets and error dispatch in Ktor.
        registration.addUrlMappings("/script/*", "/style/*", "/icons/*", "/favicon.ico", "/browserconfig.xml", "/index.html", serverProperties.getError().getPath(), "");
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
