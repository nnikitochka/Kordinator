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
import org.airsonic.player.security.SpringUserDetailsServiceAdapter;
import org.airsonic.player.security.CredentialEncoderRegistry;
import org.airsonic.player.security.SpringCredentialEncoderRegistry;
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
import org.airsonic.player.i18n.LocaleService;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.MessageSource;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Connects Ktor to the existing servlet container during the migration. */
public class KtorConfiguration {

    @Bean
    public javax.cache.CacheManager jCacheCacheManager(RuntimeCompositionRoot runtime) {
        return runtime.createCacheManager();
    }

    @Bean
    public LegacyViewSuffixHandlerMapping legacyViewSuffixHandlerMapping() {
        return new LegacyViewSuffixHandlerMapping();
    }

    @Bean(destroyMethod = "close")
    public org.airsonic.player.monitor.MetricsManager metricsManager(RuntimeCompositionRoot runtime) {
        return runtime.createMetricsManager();
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
            RuntimeCompositionRoot runtime, javax.sql.DataSource dataSource) {
        return runtime.createHealthContributorRegistry(dataSource);
    }

    @Bean
    public RuntimeCompositionRoot runtimeCompositionRoot(org.springframework.core.env.Environment env) {
        return new RuntimeCompositionRoot(SpringEnvironmentRuntimeConfigurationAdapter.from(env));
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
            SecurityService securityService, SpringUserDetailsServiceAdapter userDetailsService,
            @Lazy org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        MultipleCredsMatchingAuthenticationProvider provider = new MultipleCredsMatchingAuthenticationProvider();
        provider.setSecurityService(securityService);
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SpringUserDetailsServiceAdapter springUserDetailsServiceAdapter(SecurityService securityService) {
        return new SpringUserDetailsServiceAdapter(securityService);
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
    public org.airsonic.player.runtime.PersistenceGraph persistenceGraph(RuntimeCompositionRoot runtime,
            javax.sql.DataSource dataSource) {
        return runtime.createPersistenceGraph(dataSource);
    }

    @Bean
    public org.airsonic.player.dao.TransactionManager airsonicTransactionManager(
            org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getTransactionManager();
    }

    @Bean
    public org.airsonic.player.dao.JdbcHelper jdbcHelper(org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getJdbcHelper();
    }

    @Bean
    public org.airsonic.player.runtime.DaoGraph daoGraph(org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getDaos();
    }

    @Bean
    public AlbumDao albumDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getAlbumDao(); }

    @Bean
    public ArtistDao artistDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getArtistDao(); }

    @Bean
    public AvatarDao avatarDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getAvatarDao(); }

    @Bean
    public BookmarkDao bookmarkDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getBookmarkDao(); }

    @Bean
    public CoverArtDao coverArtDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getCoverArtDao(); }

    @Bean
    public DatabaseDao databaseDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getDatabaseDao(); }

    @Bean
    public MediaFileDao mediaFileDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getMediaFileDao(); }

    @Bean
    public MusicFolderDao musicFolderDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getMusicFolderDao(); }

    @Bean
    public PlayQueueDao playQueueDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getPlayQueueDao(); }

    @Bean
    public PlaylistDao playlistDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getPlaylistDao(); }

    @Bean("musicFileInfoDao")
    public RatingDao ratingDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getRatingDao(); }

    @Bean
    public ShareDao shareDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getShareDao(); }

    @Bean
    public TranscodingDao transcodingDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getTranscodingDao(); }

    @Bean
    public UserDao userDao(org.airsonic.player.runtime.DaoGraph graph) { return graph.getUserDao(); }

    @Bean
    public PlayerDao playerDao(RuntimeCompositionRoot runtime) throws Exception {
        return runtime.createPlayerDao();
    }

    @Bean
    public PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory(RuntimeCompositionRoot runtime) {
        return runtime.createPlayerDaoPlayQueueFactory();
    }

    @Bean
    public VersionService versionService(RuntimeCompositionRoot runtime) throws java.io.IOException {
        return runtime.createVersionService();
    }

    @Bean
    public ServerInfoService serverInfoService(RuntimeCompositionRoot runtime, VersionService versionService,
            SettingsService settingsService) {
        return runtime.createServerInfoService(versionService, settingsService);
    }

    @Bean
    public LocaleService localeService(RuntimeCompositionRoot runtime, SettingsService settingsService) {
        return runtime.createLocaleService(settingsService);
    }

    @Bean
    public LocaleResolver localeResolver(SecurityService securityService, LocaleService localeService) {
        return new LocaleResolver(securityService, localeService);
    }

    @Bean
    public StatusService statusService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, SettingsService settingsService,
            TaskSchedulingService taskSchedulingService) {
        return runtime.createStatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
    }

    @Bean
    public AnalyzerFactory analyzerFactory(RuntimeCompositionRoot runtime) {
        return runtime.createAnalyzerFactory();
    }

    @Bean
    public DocumentFactory documentFactory(RuntimeCompositionRoot runtime) {
        return runtime.createDocumentFactory();
    }

    @Bean
    public QueryFactory queryFactory(RuntimeCompositionRoot runtime) {
        return runtime.createQueryFactory();
    }

    @Bean
    public SearchServiceUtilities searchServiceUtilities(RuntimeCompositionRoot runtime, MediaFileService mediaFileService) {
        return runtime.createSearchServiceUtilities(mediaFileService);
    }

    @Bean
    public IndexManager indexManager(RuntimeCompositionRoot runtime) {
        return runtime.createIndexManager();
    }

    @Bean
    public SearchServiceImpl searchServiceImpl(RuntimeCompositionRoot runtime, MediaFileService mediaFileService) {
        return runtime.createSearchService(mediaFileService);
    }

    @Bean
    public PasswordRecoveryService passwordRecoveryService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            SecurityService securityService) {
        return runtime.createPasswordRecoveryService(settingsService, securityService);
    }

    @Bean
    public DefaultPlaylistExportHandler defaultPlaylistExportHandler(RuntimeCompositionRoot runtime,
            MediaFileDao mediaFileDao) {
        return runtime.createDefaultPlaylistExportHandler(mediaFileDao);
    }

    @Bean
    public DefaultPlaylistImportHandler defaultPlaylistImportHandler(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        return runtime.createDefaultPlaylistImportHandler(mediaFileService, settingsService);
    }

    @Bean
    public XspfPlaylistExportHandler xspfPlaylistExportHandler(RuntimeCompositionRoot runtime,
            MediaFileDao mediaFileDao, PlaylistDao playlistDao, CoverArtService coverArtService) {
        return runtime.createXspfPlaylistExportHandler(mediaFileDao, playlistDao, coverArtService);
    }

    @Bean
    public XspfPlaylistImportHandler xspfPlaylistImportHandler(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        return runtime.createXspfPlaylistImportHandler(mediaFileService, settingsService);
    }

    @Bean
    public PlaylistService playlistService(RuntimeCompositionRoot runtime, SecurityService securityService,
            SettingsService settingsService,
            java.util.List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers,
            java.util.List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers) throws Exception {
        return runtime.createPlaylistService(securityService, settingsService, exportHandlers, importHandlers);
    }

    @Bean
    public DefaultMetaDataParser defaultMetaDataParser(RuntimeCompositionRoot runtime) {
        return runtime.createDefaultMetaDataParser();
    }

    @Bean
    public FFmpegParser ffmpegParser(RuntimeCompositionRoot runtime) {
        return runtime.createFFmpegParser();
    }

    @Bean
    public JaudiotaggerParser jaudiotaggerParser(RuntimeCompositionRoot runtime) {
        return runtime.createJaudiotaggerParser();
    }

    @Bean
    public MetaDataParserFactory metaDataParserFactory(RuntimeCompositionRoot runtime) {
        return runtime.createMetaDataParserFactory();
    }

    @Bean
    public HlsPlaylistService hlsPlaylistService(RuntimeCompositionRoot runtime, JWTSecurityService jwtSecurityService) {
        return runtime.createHlsPlaylistService(jwtSecurityService);
    }

    @Bean
    public HlsSegmentService hlsSegmentService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, SecurityService securityService,
            StatusService statusService, SettingsService settingsService, TranscodingService transcodingService) {
        return runtime.createHlsSegmentService(mediaFileService, securityService, statusService,
                settingsService, transcodingService);
    }

    @Bean
    public CoverArtService coverArtService(RuntimeCompositionRoot runtime, CoverArtDao coverArtDao) {
        return runtime.createCoverArtService(coverArtDao);
    }

    @Bean
    public CoverArtImageService coverArtImageService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            CoverArtService coverArtService, TranscodingService transcodingService, SettingsService settingsService,
            PlaylistService playlistService, ArtistDao artistDao, AlbumDao albumDao) {
        return runtime.createCoverArtImageService(mediaFileService, coverArtService, transcodingService,
                settingsService, playlistService, artistDao, albumDao);
    }

    @Bean
    public CoverArtSearchService coverArtSearchService(RuntimeCompositionRoot runtime, SecurityService securityService,
            MediaFileService mediaFileService, LastFmService lastFmService, CoverArtService coverArtService) {
        return runtime.createCoverArtSearchService(securityService, mediaFileService, lastFmService, coverArtService);
    }

    @Bean
    public CoverArtAccentColorBackfill coverArtAccentColorBackfill(RuntimeCompositionRoot runtime,
            CoverArtService coverArtService) {
        return runtime.createCoverArtAccentColorBackfill(coverArtService);
    }

    @Bean
    public TagEditService tagEditService(RuntimeCompositionRoot runtime, MetaDataParserFactory metaDataParserFactory,
            MediaFileService mediaFileService,
            SecurityService securityService) {
        return runtime.createTagEditService(metaDataParserFactory, mediaFileService, securityService);
    }

    @Bean
    public AlbumUpnpProcessor albumUpnpProcessor(RuntimeCompositionRoot runtime, AlbumDao albumDao) {
        return runtime.createAlbumUpnpProcessor(albumDao);
    }

    @Bean
    public RecentAlbumUpnpProcessor recentAlbumUpnpProcessor(RuntimeCompositionRoot runtime, AlbumDao albumDao) {
        return runtime.createRecentAlbumUpnpProcessor(albumDao);
    }

    @Bean
    public ArtistUpnpProcessor artistUpnpProcessor(RuntimeCompositionRoot runtime, ArtistDao artistDao) {
        return runtime.createArtistUpnpProcessor(artistDao);
    }

    @Bean
    public GenreUpnpProcessor genreUpnpProcessor(RuntimeCompositionRoot runtime) {
        return runtime.createGenreUpnpProcessor();
    }

    @Bean
    public MediaFileUpnpProcessor mediaFileUpnpProcessor(RuntimeCompositionRoot runtime, MediaFileDao mediaFileDao) {
        return runtime.createMediaFileUpnpProcessor(mediaFileDao);
    }

    @Bean
    public PlaylistUpnpProcessor playlistUpnpProcessor(RuntimeCompositionRoot runtime, PlaylistService playlistService) {
        return runtime.createPlaylistUpnpProcessor(playlistService);
    }

    @Bean
    public RootUpnpProcessor rootUpnpProcessor(RuntimeCompositionRoot runtime, IndexManager indexManager) {
        return runtime.createRootUpnpProcessor(indexManager);
    }

    @Bean
    public DispatchingContentDirectory dispatchingContentDirectory(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            PlaylistService playlistService, SearchService searchService, MusicIndexService musicIndexService,
            IndexManager indexManager, SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createDispatchingContentDirectory(mediaFileService, mediaFolderService, playlistService,
                searchService, musicIndexService, indexManager, settingsService, playerService, transcodingService,
                jwtSecurityService);
    }

    @Bean
    public FolderBasedContentDirectory folderBasedContentDirectory(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, PlaylistService playlistService, IndexManager indexManager,
            SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createFolderBasedContentDirectory(mediaFileService, mediaFolderService, playlistService,
                indexManager, settingsService, playerService, transcodingService, jwtSecurityService);
    }

    @Bean
    public UPnPService upnpService(SettingsService settingsService,
            @org.springframework.beans.factory.annotation.Qualifier("dispatchingContentDirectory")
            org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory,
            RuntimeCompositionRoot runtime) {
        return runtime.createUpnpService(settingsService, contentDirectory);
    }

    @Bean
    public AudioScrobblerService audioScrobblerService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            SecurityService securityService) {
        return runtime.createAudioScrobblerService(settingsService, securityService);
    }

    @Bean
    public BookmarkService bookmarkService(RuntimeCompositionRoot runtime, BookmarkDao bookmarkDao,
            MediaFileService mediaFileService) {
        return runtime.createBookmarkService(bookmarkDao, mediaFileService);
    }

    @Bean
    public LyricsService lyricsService(RuntimeCompositionRoot runtime) {
        return runtime.createLyricsService();
    }

    @Bean
    public RatingService ratingService(RuntimeCompositionRoot runtime, RatingDao ratingDao, SecurityService securityService,
            MediaFileService mediaFileService) {
        return runtime.createRatingService(ratingDao, securityService, mediaFileService);
    }

    @Bean
    public MediaDirectoryService mediaDirectoryService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            RatingService ratingService, SecurityService securityService, SettingsService settingsService) {
        return runtime.createMediaDirectoryService(mediaFileService, ratingService, securityService, settingsService);
    }

    @Bean
    public MusicIndexService musicIndexService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            MediaFileService mediaFileService) {
        return runtime.createMusicIndexService(settingsService, mediaFileService);
    }

    @Bean
    public DatabaseService databaseService(RuntimeCompositionRoot runtime, SettingsService settingsService, DatabaseDao databaseDao,
            TaskSchedulingService taskSchedulingService) {
        return runtime.createDatabaseService(settingsService, databaseDao, taskSchedulingService);
    }

    @Bean
    public JWTSecurityService jwtSecurityService(RuntimeCompositionRoot runtime, SettingsService settingsService) {
        return runtime.createJwtSecurityService(settingsService);
    }

    @Bean
    public SecurityService securityService(RuntimeCompositionRoot runtime, UserDao userDao, SettingsService settingsService,
            MediaFolderService mediaFolderService, CredentialEncoderRegistry credentialEncoders) {
        return runtime.createSecurityService(userDao, settingsService, mediaFolderService, credentialEncoders);
    }

    @Bean
    public CredentialEncoderRegistry credentialEncoderRegistry() {
        return new SpringCredentialEncoderRegistry();
    }

    @Bean
    public org.airsonic.player.service.ConfigurationPropertiesService configurationPropertiesService(
            RuntimeCompositionRoot runtime) {
        return runtime.createConfigurationPropertiesService();
    }

    @Bean
    public SettingsService settingsService(RuntimeCompositionRoot runtime, UserDao userDao, AvatarDao avatarDao,
            javax.cache.CacheManager cacheManager,
            org.airsonic.player.service.ConfigurationPropertiesService properties) {
        // Keep the DAO/cache parameters as bootstrap dependencies so PersistenceGraph exists first.
        return runtime.createSettingsService();
    }

    @Bean
    public MediaScannerService mediaScannerService(RuntimeCompositionRoot runtime,
            PlaylistService playlistService, SettingsService settingsService,
            IndexManager indexManager, MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            CoverArtService coverArtService) {
        return runtime.createMediaScannerService(playlistService, settingsService, indexManager, mediaFileService,
                mediaFolderService, coverArtService);
    }

    @Bean
    public PlayQueueService playQueueService(RuntimeCompositionRoot runtime, SearchService searchService, JukeboxService jukeboxService,
            SettingsService settingsService, MediaFileService mediaFileService, LastFmService lastFmService,
            RatingService ratingService, PlaylistService playlistService, MediaFileDao mediaFileDao,
            MediaFolderService mediaFolderService, PlayQueueDao playQueueDao,
            JWTSecurityService jwtSecurityService) {
        return runtime.createPlayQueueService(searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, mediaFileDao, mediaFolderService, playQueueDao,
                jwtSecurityService);
    }

    @Bean
    public JukeboxLegacySubsonicService jukeboxLegacySubsonicService(RuntimeCompositionRoot runtime,
            TranscodingService transcodingService,
            AudioScrobblerService audioScrobblerService, StatusService statusService,
            SettingsService settingsService, SecurityService securityService, MediaFileService mediaFileService) {
        return runtime.createJukeboxLegacySubsonicService(transcodingService, audioScrobblerService, statusService,
                settingsService, securityService, mediaFileService);
    }

    @Bean
    public JukeboxService jukeboxService(RuntimeCompositionRoot runtime, JukeboxLegacySubsonicService legacyJukebox) {
        return runtime.createJukeboxService(legacyJukebox);
    }

    @Bean
    public LastFmService lastFmService(RuntimeCompositionRoot runtime, MediaFileDao mediaFileDao, MediaFileService mediaFileService,
            ArtistDao artistDao) {
        return runtime.createLastFmService(mediaFileDao, mediaFileService, artistDao);
    }

    @Bean
    public ShareService shareService(RuntimeCompositionRoot runtime, ShareDao shareDao, MediaFileService mediaFileService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createShareService(shareDao, mediaFileService, jwtSecurityService);
    }

    @Bean
    public UploadFileService uploadFileService(RuntimeCompositionRoot runtime, SecurityService securityService) {
        return runtime.createUploadFileService(securityService);
    }

    @Bean
    public CaptionService captionService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            SettingsService settingsService, MetaDataParserFactory metaDataParserFactory,
            JWTSecurityService jwtSecurityService) {
        return runtime.createCaptionService(mediaFileService, mediaFolderService, settingsService,
                metaDataParserFactory, jwtSecurityService);
    }

    @Bean
    public VideoPlaybackService videoPlaybackService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, CaptionService captionService,
            JWTSecurityService jwtSecurityService, BookmarkService bookmarkService, SettingsService settingsService) {
        return runtime.createVideoPlaybackService(mediaFileService, captionService, jwtSecurityService,
                bookmarkService, settingsService);
    }

    @Bean
    public MediaFolderService mediaFolderService(RuntimeCompositionRoot runtime) {
        return runtime.createMediaFolderService();
    }

    @Bean
    public MediaFileService mediaFileService(RuntimeCompositionRoot runtime, SecurityService securityService, SettingsService settingsService,
            MediaFileDao mediaFileDao, AlbumDao albumDao,
            JaudiotaggerParser parser, MetaDataParserFactory metaDataParserFactory, CoverArtService coverArtService,
            LocaleService localeService) {
        return runtime.createMediaFileService(securityService, settingsService, mediaFileDao, albumDao,
                parser, metaDataParserFactory, coverArtService, localeService);
    }

    @Bean
    public TranscodingService transcodingService(RuntimeCompositionRoot runtime, TranscodingDao transcodingDao,
            SettingsService settingsService, MediaFolderService mediaFolderService,
            org.springframework.beans.factory.ObjectProvider<PlayerService> playerServiceProvider) {
        return runtime.createTranscodingService(transcodingDao, settingsService, mediaFolderService,
                playerServiceProvider::getObject);
    }

    @Bean
    @DependsOn("liquibase")
    public PlayerService playerService(RuntimeCompositionRoot runtime, PlayerDao playerDao, StatusService statusService,
            SecurityService securityService, TranscodingService transcodingService) {
        return runtime.createPlayerService(playerDao, statusService, securityService, transcodingService);
    }

    @Bean
    public TaskSchedulingService taskSchedulingService(RuntimeCompositionRoot runtime) {
        return runtime.createTaskSchedulingService();
    }

    @Bean
    public org.airsonic.player.service.PathWatcherService pathWatcherService(RuntimeCompositionRoot runtime) throws Exception {
        return runtime.createPathWatcherService();
    }

    @Bean
    public org.airsonic.player.health.StatusAggregator serverHealthStatusAggregator(
            RuntimeCompositionRoot runtime) {
        return new org.airsonic.player.health.SimpleStatusAggregator(
                runtime.getConfiguration().getHealthStatusOrder());
    }


    @Bean
    public DownloadService downloadService(RuntimeCompositionRoot runtime, StatusService statusService, SecurityService securityService,
            PlaylistService playlistService, SettingsService settingsService, MediaFileService mediaFileService,
            CoverArtService coverArtService) {
        return runtime.createDownloadService(statusService, securityService, playlistService, settingsService,
                mediaFileService, coverArtService);
    }

    @Bean
    public UserAdministrationService userAdministrationService(RuntimeCompositionRoot runtime,
            SecurityService securityService, SettingsService settingsService) {
        return runtime.createUserAdministrationService(securityService, settingsService);
    }

    @Bean
    public MusicIndexStateService musicIndexStateService(RuntimeCompositionRoot runtime, MediaScannerService scanner,
            SettingsService settings) {
        return runtime.createMusicIndexStateService(scanner, settings);
    }

    @Bean
    public MediaStreamService mediaStreamService(RuntimeCompositionRoot runtime, StatusService statusService, SecurityService securityService,
            SettingsService settingsService, TranscodingService transcodingService, AudioScrobblerService audioScrobblerService,
            MediaFileService mediaFileService, SearchService searchService) {
        return runtime.createMediaStreamService(statusService, securityService, settingsService, transcodingService,
                audioScrobblerService, mediaFileService, searchService);
    }

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration(
            RuntimeCompositionRoot runtime,
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
            LocaleService localeService,
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
                                        mediaFileDao, mediaFolderService, playerService, localeService),
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
                                new GeneralSettingsController(runtime.createGeneralSettingsAdministrationService(
                                        settingsService, playlistService), jaxbWriter),
                                new MusicFolderSettingsController(runtime.createMusicFolderAdministrationService(
                                        settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao,
                                        mediaFileDao, indexManager, playlistService, coverArtService), jaxbWriter),
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
                                new PlaylistPageController(playlistService, securityService, settingsService, localeService, objectMapper),
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
