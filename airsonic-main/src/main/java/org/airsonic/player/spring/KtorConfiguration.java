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
import org.airsonic.player.security.GlobalSecurityConfig;
import org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider;
import org.airsonic.player.security.CredentialEncoderRegistry;
import org.airsonic.player.security.SpringCredentialEncoderRegistry;
import org.airsonic.player.controller.rest.tags.MediaFileCommentController;
import org.airsonic.player.controller.rest.tags.TagEditController;
import org.airsonic.player.controller.rest.livequeue.LiveQueueExportController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.service.MusicIndexStateService;
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.airsonic.player.controller.rest.shares.PublicShareController;
import org.airsonic.player.controller.captions.CaptionsRestController;
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
import org.airsonic.player.service.ConfigurationPropertiesService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;

/** Connects Ktor to the existing servlet container during the migration. */
public class KtorConfiguration {

    @Bean
    public JAXBWriter subsonicJaxbWriter(RuntimeCompositionRoot runtime) {
        return runtime.createSubsonicJaxbWriter();
    }

    @Bean
    public RestPlayerResolver restPlayerResolver(RuntimeCompositionRoot runtime, PlayerService playerService) {
        return runtime.createRestPlayerResolver(playerService);
    }

    @Bean
    public org.airsonic.player.controller.rest.genres.GenresService subsonicGenresService(
            RuntimeCompositionRoot runtime) {
        return runtime.createSubsonicGenresService();
    }

    @Bean
    public GenresController subsonicGenresController(RuntimeCompositionRoot runtime) {
        return runtime.createSubsonicGenresController();
    }

    @Bean
    public ArtistsController subsonicArtistsController(
            RuntimeCompositionRoot runtime, MusicIndexService musicIndexService, SettingsService settingsService,
            MediaFolderService mediaFolderService, SubsonicResponseMapper subsonicResponseMapper) {
        return runtime.createSubsonicArtistsController(
                musicIndexService, settingsService, mediaFolderService, subsonicResponseMapper);
    }

    @Bean
    public org.airsonic.player.controller.rest.music.folder.MusicFoldersService subsonicMusicFoldersService(
            RuntimeCompositionRoot runtime, MediaFolderService mediaFolderService) {
        return runtime.createSubsonicMusicFoldersService(mediaFolderService);
    }

    @Bean
    public MusicFoldersController subsonicMusicFoldersController(
            RuntimeCompositionRoot runtime, org.airsonic.player.controller.rest.music.folder.MusicFoldersService service) {
        return runtime.createSubsonicMusicFoldersController(service);
    }

    @Bean
    public SubsonicResponseMapper subsonicResponseMapper(
            MediaFileService mediaFileService, RatingService ratingService, CoverArtService coverArtService,
            TranscodingService transcodingService, RuntimeCompositionRoot runtime) {
        return runtime.createSubsonicResponseMapper(mediaFileService, ratingService, coverArtService, transcodingService);
    }

    @Bean
    public SearchController subsonicSearchController(
            RuntimeCompositionRoot runtime, org.airsonic.player.controller.rest.search.SearchService service) {
        return runtime.createSubsonicSearchController(service);
    }

    @Bean
    public org.airsonic.player.controller.rest.search.SearchService subsonicSearchService(
            SearchService searchService, MediaFolderService mediaFolderService,
            SubsonicResponseMapper subsonicResponseMapper, RuntimeCompositionRoot runtime) {
        return runtime.createSubsonicSearchService(searchService, mediaFolderService, subsonicResponseMapper);
    }

    @Bean
    public org.springframework.boot.web.server.ErrorPageRegistrar ktorErrorPageRegistrar(
            org.springframework.boot.autoconfigure.web.ServerProperties serverProperties) {
        return factory -> factory.addErrorPages(new org.springframework.boot.web.server.ErrorPage(serverProperties.getError().getPath()));
    }

    @Bean
    public RuntimeCompositionRoot runtimeCompositionRoot(ApplicationArguments arguments) {
        ConfigurationPropertiesService properties = ConfigurationPropertiesService.getInstance();
        SettingsService.migratePropFileKeys(SettingsService.getMigratedPropertyKeys(), properties);
        org.airsonic.player.runtime.RuntimeConfiguration configuration =
                new org.airsonic.player.runtime.RuntimeConfigurationLoader().load(
                        arguments.getSourceArgs(), System.getProperties(), System.getenv(), null, properties.getConfiguration());
        return new RuntimeCompositionRoot(configuration);
    }

    @Bean(destroyMethod = "shutdown")
    public org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry(RuntimeCompositionRoot runtime) {
        return runtime.getLifecycleRegistry();
    }

    @Bean
    public org.springframework.context.ApplicationListener<org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent>
            authenticationFailureListener(GlobalSecurityConfig securityConfig) {
        return securityConfig::loginFailureListener;
    }

    @Bean
    public org.airsonic.player.security.SubsonicCredentialAuthenticationService subsonicCredentialAuthenticationService(
            RuntimeCompositionRoot runtime, SecurityService securityService, CredentialEncoderRegistry credentialEncoders) {
        return runtime.createSubsonicCredentialAuthenticationService(securityService, credentialEncoders);
    }

    @Bean
    public MultipleCredsMatchingAuthenticationProvider multipleCredsMatchingAuthenticationProvider(
            org.airsonic.player.security.SubsonicCredentialAuthenticationService authenticationService) {
        return new MultipleCredsMatchingAuthenticationProvider(authenticationService);
    }

    public org.airsonic.player.runtime.PersistenceGraph persistenceGraph(RuntimeCompositionRoot runtime,
            javax.sql.DataSource dataSource) {
        return runtime.createPersistenceGraph(dataSource);
    }

    public org.airsonic.player.dao.TransactionManager airsonicTransactionManager(
            org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getTransactionManager();
    }

    public org.airsonic.player.dao.JdbcHelper jdbcHelper(org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getJdbcHelper();
    }

    public org.airsonic.player.runtime.DaoGraph daoGraph(org.airsonic.player.runtime.PersistenceGraph persistenceGraph) {
        return persistenceGraph.getDaos();
    }

    public PlayerDao playerDao(RuntimeCompositionRoot runtime) throws Exception {
        return runtime.createPlayerDao();
    }

    public PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory(RuntimeCompositionRoot runtime) {
        return runtime.createPlayerDaoPlayQueueFactory();
    }

    public VersionService versionService(RuntimeCompositionRoot runtime) throws java.io.IOException {
        return runtime.createVersionService();
    }

    public ServerInfoService serverInfoService(RuntimeCompositionRoot runtime, VersionService versionService,
            SettingsService settingsService) {
        return runtime.createServerInfoService(versionService, settingsService);
    }

    public LocaleService localeService(RuntimeCompositionRoot runtime, SettingsService settingsService) {
        return runtime.createLocaleService(settingsService);
    }

    @Bean
    public LocaleResolver servletLocaleResolver(SecurityService securityService, LocaleService localeService) {
        return new LocaleResolver(securityService, localeService);
    }

    public StatusService statusService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, SettingsService settingsService,
            TaskSchedulingService taskSchedulingService) {
        return runtime.createStatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
    }

    public AnalyzerFactory analyzerFactory(RuntimeCompositionRoot runtime) {
        return runtime.createAnalyzerFactory();
    }

    public DocumentFactory documentFactory(RuntimeCompositionRoot runtime) {
        return runtime.createDocumentFactory();
    }

    public QueryFactory queryFactory(RuntimeCompositionRoot runtime) {
        return runtime.createQueryFactory();
    }

    public SearchServiceUtilities searchServiceUtilities(RuntimeCompositionRoot runtime, MediaFileService mediaFileService) {
        return runtime.createSearchServiceUtilities(mediaFileService);
    }

    public IndexManager indexManager(RuntimeCompositionRoot runtime) {
        return runtime.createIndexManager();
    }

    public SearchServiceImpl searchServiceImpl(RuntimeCompositionRoot runtime, MediaFileService mediaFileService) {
        return runtime.createSearchService(mediaFileService);
    }

    public PasswordRecoveryService passwordRecoveryService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            SecurityService securityService) {
        return runtime.createPasswordRecoveryService(settingsService, securityService);
    }

    public DefaultPlaylistExportHandler defaultPlaylistExportHandler(RuntimeCompositionRoot runtime,
            org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createDefaultPlaylistExportHandler(graph.getMediaFileDao());
    }

    public DefaultPlaylistImportHandler defaultPlaylistImportHandler(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        return runtime.createDefaultPlaylistImportHandler(mediaFileService, settingsService);
    }

    public XspfPlaylistExportHandler xspfPlaylistExportHandler(RuntimeCompositionRoot runtime,
            org.airsonic.player.runtime.DaoGraph graph, CoverArtService coverArtService) {
        return runtime.createXspfPlaylistExportHandler(
                graph.getMediaFileDao(), graph.getPlaylistDao(), coverArtService);
    }

    public XspfPlaylistImportHandler xspfPlaylistImportHandler(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            SettingsService settingsService) {
        return runtime.createXspfPlaylistImportHandler(mediaFileService, settingsService);
    }

    public PlaylistService playlistService(RuntimeCompositionRoot runtime, SecurityService securityService,
            SettingsService settingsService,
            java.util.List<org.airsonic.player.service.playlist.PlaylistExportHandler> exportHandlers,
            java.util.List<org.airsonic.player.service.playlist.PlaylistImportHandler> importHandlers) throws Exception {
        return runtime.createPlaylistService(securityService, settingsService, exportHandlers, importHandlers);
    }

    public DefaultMetaDataParser defaultMetaDataParser(RuntimeCompositionRoot runtime) {
        return runtime.createDefaultMetaDataParser();
    }

    public FFmpegParser ffmpegParser(RuntimeCompositionRoot runtime) {
        return runtime.createFFmpegParser();
    }

    public JaudiotaggerParser jaudiotaggerParser(RuntimeCompositionRoot runtime) {
        return runtime.createJaudiotaggerParser();
    }

    public MetaDataParserFactory metaDataParserFactory(RuntimeCompositionRoot runtime) {
        return runtime.createMetaDataParserFactory();
    }

    public HlsPlaylistService hlsPlaylistService(RuntimeCompositionRoot runtime, JWTSecurityService jwtSecurityService) {
        return runtime.createHlsPlaylistService(jwtSecurityService);
    }

    public HlsSegmentService hlsSegmentService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, SecurityService securityService,
            StatusService statusService, SettingsService settingsService, TranscodingService transcodingService) {
        return runtime.createHlsSegmentService(mediaFileService, securityService, statusService,
                settingsService, transcodingService);
    }

    public CoverArtService coverArtService(
            RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createCoverArtService(graph.getCoverArtDao());
    }

    public CoverArtImageService coverArtImageService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            CoverArtService coverArtService, TranscodingService transcodingService, SettingsService settingsService,
            PlaylistService playlistService, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createCoverArtImageService(mediaFileService, coverArtService, transcodingService,
                settingsService, playlistService, graph.getArtistDao(), graph.getAlbumDao());
    }

    public CoverArtAccentColorBackfill coverArtAccentColorBackfill(RuntimeCompositionRoot runtime,
            CoverArtService coverArtService) {
        return runtime.createCoverArtAccentColorBackfill(coverArtService);
    }

    public TagEditService tagEditService(RuntimeCompositionRoot runtime, MetaDataParserFactory metaDataParserFactory,
            MediaFileService mediaFileService,
            SecurityService securityService) {
        return runtime.createTagEditService(metaDataParserFactory, mediaFileService, securityService);
    }

    public AlbumUpnpProcessor albumUpnpProcessor(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createAlbumUpnpProcessor(graph.getAlbumDao());
    }

    public RecentAlbumUpnpProcessor recentAlbumUpnpProcessor(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createRecentAlbumUpnpProcessor(graph.getAlbumDao());
    }

    public ArtistUpnpProcessor artistUpnpProcessor(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createArtistUpnpProcessor(graph.getArtistDao());
    }

    public GenreUpnpProcessor genreUpnpProcessor(RuntimeCompositionRoot runtime) {
        return runtime.createGenreUpnpProcessor();
    }

    public MediaFileUpnpProcessor mediaFileUpnpProcessor(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        return runtime.createMediaFileUpnpProcessor(graph.getMediaFileDao());
    }

    public PlaylistUpnpProcessor playlistUpnpProcessor(RuntimeCompositionRoot runtime, PlaylistService playlistService) {
        return runtime.createPlaylistUpnpProcessor(playlistService);
    }

    public RootUpnpProcessor rootUpnpProcessor(RuntimeCompositionRoot runtime, IndexManager indexManager) {
        return runtime.createRootUpnpProcessor(indexManager);
    }

    public DispatchingContentDirectory dispatchingContentDirectory(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            PlaylistService playlistService, SearchService searchService, MusicIndexService musicIndexService,
            IndexManager indexManager, SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createDispatchingContentDirectory(mediaFileService, mediaFolderService, playlistService,
                searchService, musicIndexService, indexManager, settingsService, playerService, transcodingService,
                jwtSecurityService);
    }

    public FolderBasedContentDirectory folderBasedContentDirectory(RuntimeCompositionRoot runtime,
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, PlaylistService playlistService, IndexManager indexManager,
            SettingsService settingsService, PlayerService playerService, TranscodingService transcodingService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createFolderBasedContentDirectory(mediaFileService, mediaFolderService, playlistService,
                indexManager, settingsService, playerService, transcodingService, jwtSecurityService);
    }

    public UPnPService upnpService(SettingsService settingsService,
            @org.springframework.beans.factory.annotation.Qualifier("dispatchingContentDirectory")
            org.airsonic.player.service.upnp.CustomContentDirectory contentDirectory,
            RuntimeCompositionRoot runtime) {
        return runtime.createUpnpService(settingsService, contentDirectory);
    }

    public AudioScrobblerService audioScrobblerService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            SecurityService securityService) {
        return runtime.createAudioScrobblerService(settingsService, securityService);
    }

    public BookmarkService bookmarkService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph,
            MediaFileService mediaFileService) {
        return runtime.createBookmarkService(graph.getBookmarkDao(), mediaFileService);
    }

    public LyricsService lyricsService(RuntimeCompositionRoot runtime) {
        return runtime.createLyricsService();
    }

    public RatingService ratingService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph, SecurityService securityService,
            MediaFileService mediaFileService) {
        return runtime.createRatingService(graph.getRatingDao(), securityService, mediaFileService);
    }

    public MusicIndexService musicIndexService(RuntimeCompositionRoot runtime, SettingsService settingsService,
            MediaFileService mediaFileService) {
        return runtime.createMusicIndexService(settingsService, mediaFileService);
    }

    public DatabaseService databaseService(RuntimeCompositionRoot runtime, SettingsService settingsService, org.airsonic.player.runtime.DaoGraph graph,
            TaskSchedulingService taskSchedulingService) {
        return runtime.createDatabaseService(settingsService, graph.getDatabaseDao(), taskSchedulingService);
    }

    public JWTSecurityService jwtSecurityService(RuntimeCompositionRoot runtime, SettingsService settingsService) {
        return runtime.createJwtSecurityService(settingsService);
    }

    public SecurityService securityService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph, SettingsService settingsService,
            MediaFolderService mediaFolderService, CredentialEncoderRegistry credentialEncoders) {
        return runtime.createSecurityService(graph.getUserDao(), settingsService, mediaFolderService, credentialEncoders);
    }

    public CredentialEncoderRegistry credentialEncoderRegistry() {
        return new SpringCredentialEncoderRegistry();
    }

    public org.airsonic.player.service.ConfigurationPropertiesService configurationPropertiesService(
            RuntimeCompositionRoot runtime) {
        return runtime.createConfigurationPropertiesService();
    }

    public SettingsService settingsService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph,
            javax.cache.CacheManager cacheManager,
            org.airsonic.player.service.ConfigurationPropertiesService properties) {
        // The graph argument preserves startup ordering while DAOs remain owned by RuntimeCompositionRoot.
        return runtime.createSettingsService();
    }

    public MediaScannerService mediaScannerService(RuntimeCompositionRoot runtime,
            PlaylistService playlistService, SettingsService settingsService,
            IndexManager indexManager, MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            CoverArtService coverArtService) {
        return runtime.createMediaScannerService(playlistService, settingsService, indexManager, mediaFileService,
                mediaFolderService, coverArtService);
    }

    public PlayQueueService playQueueService(RuntimeCompositionRoot runtime, SearchService searchService, JukeboxService jukeboxService,
            SettingsService settingsService, MediaFileService mediaFileService, LastFmService lastFmService,
            RatingService ratingService, PlaylistService playlistService, org.airsonic.player.runtime.DaoGraph graph,
            MediaFolderService mediaFolderService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createPlayQueueService(searchService, jukeboxService, settingsService, mediaFileService,
                lastFmService, ratingService, playlistService, graph.getMediaFileDao(), mediaFolderService,
                graph.getPlayQueueDao(),
                jwtSecurityService);
    }

    public JukeboxLegacySubsonicService jukeboxLegacySubsonicService(RuntimeCompositionRoot runtime,
            TranscodingService transcodingService,
            AudioScrobblerService audioScrobblerService, StatusService statusService,
            SettingsService settingsService, SecurityService securityService, MediaFileService mediaFileService) {
        return runtime.createJukeboxLegacySubsonicService(transcodingService, audioScrobblerService, statusService,
                settingsService, securityService, mediaFileService);
    }

    public JukeboxService jukeboxService(RuntimeCompositionRoot runtime, JukeboxLegacySubsonicService legacyJukebox) {
        return runtime.createJukeboxService(legacyJukebox);
    }

    public LastFmService lastFmService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph,
            MediaFileService mediaFileService) {
        return runtime.createLastFmService(graph.getMediaFileDao(), mediaFileService, graph.getArtistDao());
    }

    public ShareService shareService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph, MediaFileService mediaFileService,
            JWTSecurityService jwtSecurityService) {
        return runtime.createShareService(graph.getShareDao(), mediaFileService, jwtSecurityService);
    }

    public UploadFileService uploadFileService(RuntimeCompositionRoot runtime, SecurityService securityService) {
        return runtime.createUploadFileService(securityService);
    }

    public CaptionService captionService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, MediaFolderService mediaFolderService,
            SettingsService settingsService, MetaDataParserFactory metaDataParserFactory,
            JWTSecurityService jwtSecurityService) {
        return runtime.createCaptionService(mediaFileService, mediaFolderService, settingsService,
                metaDataParserFactory, jwtSecurityService);
    }

    public VideoPlaybackService videoPlaybackService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService, CaptionService captionService,
            JWTSecurityService jwtSecurityService, BookmarkService bookmarkService, SettingsService settingsService) {
        return runtime.createVideoPlaybackService(mediaFileService, captionService, jwtSecurityService,
                bookmarkService, settingsService);
    }

    public MediaFolderService mediaFolderService(RuntimeCompositionRoot runtime) {
        return runtime.createMediaFolderService();
    }

    public MediaFileService mediaFileService(RuntimeCompositionRoot runtime, SecurityService securityService, SettingsService settingsService,
            org.airsonic.player.runtime.DaoGraph graph,
            JaudiotaggerParser parser, MetaDataParserFactory metaDataParserFactory, CoverArtService coverArtService,
            LocaleService localeService) {
        return runtime.createMediaFileService(securityService, settingsService, graph.getMediaFileDao(), graph.getAlbumDao(),
                parser, metaDataParserFactory, coverArtService, localeService);
    }

    public TranscodingService transcodingService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph,
            SettingsService settingsService, MediaFolderService mediaFolderService) {
        return runtime.createTranscodingService(graph.getTranscodingDao(), settingsService, mediaFolderService);
    }

    public PlayerService playerService(RuntimeCompositionRoot runtime, StatusService statusService,
            SecurityService securityService, TranscodingService transcodingService) throws Exception {
        return runtime.createPlayerService(runtime.createPlayerDao(), statusService, securityService, transcodingService);
    }

    public TaskSchedulingService taskSchedulingService(RuntimeCompositionRoot runtime) {
        return runtime.createTaskSchedulingService();
    }

    public org.airsonic.player.service.PathWatcherService pathWatcherService(RuntimeCompositionRoot runtime) throws Exception {
        return runtime.createPathWatcherService();
    }

    @Bean
    public org.airsonic.player.health.StatusAggregator serverHealthStatusAggregator(
            RuntimeCompositionRoot runtime) {
        return runtime.createStatusAggregator();
    }


    public DownloadService downloadService(RuntimeCompositionRoot runtime, StatusService statusService, SecurityService securityService,
            PlaylistService playlistService, SettingsService settingsService, MediaFileService mediaFileService,
            CoverArtService coverArtService) {
        return runtime.createDownloadService(statusService, securityService, playlistService, settingsService,
                mediaFileService, coverArtService);
    }

    public UserAdministrationService userAdministrationService(RuntimeCompositionRoot runtime,
            SecurityService securityService, SettingsService settingsService) {
        return runtime.createUserAdministrationService(securityService, settingsService);
    }

    public MusicIndexStateService musicIndexStateService(RuntimeCompositionRoot runtime, MediaScannerService scanner,
            SettingsService settings) {
        return runtime.createMusicIndexStateService(scanner, settings);
    }

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
            PasswordRecoveryService passwordRecoveryService,
            ServerInfoService serverInfoService,
            org.airsonic.player.service.metadata.MetaDataParserFactory metadataParsers,
            MediaScannerService mediaScannerService,
            MediaFileService mediaFileService,
            org.airsonic.player.runtime.DaoGraph graph,
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
            TagEditService tagEditService,
            BookmarkService bookmarkService,
            JukeboxService jukeboxService,
            PlayQueueService playQueueService,
            javax.sql.DataSource dataSource,
            SecurityService securityService,
            CredentialEncoderRegistry credentialEncoderRegistry,
            SettingsService settingsService,
            org.airsonic.player.service.ConfigurationPropertiesService properties,
            VersionService versionService,
            MusicIndexService musicIndexService,
            AudioScrobblerService audioScrobblerService,
            UPnPService upnpService,
            StatusService statusService,
            javax.cache.CacheManager jCacheCacheManager,
            org.airsonic.player.health.HealthContributorRegistry healthContributors,
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
            org.airsonic.player.runtime.LifecycleRegistry lifecycleRegistry,
            org.airsonic.player.monitor.MetricsManager metricsManager
    ) {
        ArtistDao artistDao = graph.getArtistDao();
        AlbumDao albumDao = graph.getAlbumDao();
        MediaFileDao mediaFileDao = graph.getMediaFileDao();
        PlayQueueDao playQueueDao = graph.getPlayQueueDao();
        org.airsonic.player.ktor.KordinatorSessionService kordinatorSessionService =
                new org.airsonic.player.ktor.KordinatorSessionService(
                        securityService, credentialEncoderRegistry,
                        new org.airsonic.player.ktor.JdbcKordinatorSessionStore(dataSource),
                        settingsService.getNonDecodablePasswordEncoder());
        org.airsonic.player.ktor.KordinatorAuthRoutes kordinatorAuthRoutes =
                new org.airsonic.player.ktor.KordinatorAuthRoutes(
                        kordinatorSessionService, new org.airsonic.player.security.LoginRateLimiter());
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(
                        new AirsonicKtorServlet(searchController,
                                musicFoldersController, genresController,
                                artistsController, runtime.createPingController(), runtime.createScanController(mediaScannerService),
                                runtime.createSimilarSongsController(mediaFileService, lastFmService,
                                        mediaFolderService, responseMapper),
                                runtime.createArtistInfoController(mediaFileService, lastFmService,
                                        mediaFolderService, responseMapper),
                                runtime.createAlbumInfoController(mediaFileService, lastFmService),
                                new PlaylistsController(playlistService, mediaFileService, securityService, responseMapper, jaxbWriter,
                                        mediaFileDao, mediaFolderService, playerService, localeService),
                                new org.airsonic.player.ktor.KordinatorPlaylistRoutes(playlistService, securityService, mediaFileService),
                                new org.airsonic.player.ktor.KordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService),
                                new org.airsonic.player.ktor.KordinatorArtistRoutes(artistDao, albumDao, mediaFolderService),
                                new org.airsonic.player.ktor.KordinatorSearchRoutes(albumSearchService, mediaFolderService, securityService),
                                kordinatorAuthRoutes,
                                new org.airsonic.player.ktor.KordinatorFavoriteRoutes(
                                        mediaFileDao, albumDao, artistDao, mediaFileService, mediaFolderService, securityService),
                                new org.airsonic.player.ktor.KordinatorLibraryRoutes(
                                        mediaFileService, mediaFolderService, securityService),
                                kordinatorSessionService,
                                runtime.createUploadController(uploadFileService, statusService, securityService, settingsService),
                                runtime.createPlaylistExportController(playlistService),
                                runtime.createPlaylistImportController(playlistService),
                                runtime.createLiveQueueExportController(transcodingService, jwtSecurityService, securityService),
                                new SharesController(shareService, mediaFileService, mediaFolderService, responseMapper, jaxbWriter, playerService, playlistService, securityService),
                                runtime.createSubsonicAlbumsController(mediaFileService, mediaFolderService,
                                        ratingService, albumSearchService, responseMapper),
                                runtime.createBookmarksController(bookmarkService, mediaFileService, responseMapper),
                                runtime.createStarredController(mediaFolderService, responseMapper),
                                runtime.createJukeboxController(playQueueService, jukeboxService, responseMapper),
                                runtime.createUsersController(securityService, settingsService, mediaFolderService,
                                        userAdministrationService),
                                runtime.createDLNASettingsController(settingsService, upnpService),
                                runtime.createDatabaseSettingsController(settingsService, databaseService,
                                        statusService, securityService, properties),
                                runtime.createCredentialsManagementController(securityService, settingsService),
                                runtime.createPersonalSettingsController(settingsService, securityService),
                                runtime.createPlayerSettingsController(playerService, transcodingService, playQueueService),
                                runtime.createAdvancedSettingsController(settingsService),
                                runtime.createTranscodingSettingsController(transcodingService, settingsService),
                                runtime.createGeneralSettingsController(runtime.createGeneralSettingsAdministrationService(
                                        settingsService, playlistService)),
                                runtime.createMusicFolderSettingsController(runtime.createMusicFolderAdministrationService(
                                        settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao,
                                        mediaFileDao, indexManager, playlistService, coverArtService)),
                                runtime.createPasswordController(securityService),
                                runtime.createPlayQueueController(mediaFileService, responseMapper),
                                runtime.createIndexesController(settingsService, mediaFolderService, musicIndexService,
                                        musicIndexStateService, ratingService, responseMapper, playerService),
                                runtime.createSongsByGenreController(mediaFolderService, responseMapper),
                                runtime.createTopSongsController(lastFmService, mediaFolderService, responseMapper),
                                runtime.createMusicDirectoryController(mediaFileService, securityService,
                                        ratingService, responseMapper),
                                runtime.createRandomSongsController(albumSearchService, mediaFolderService, responseMapper),
                                runtime.createScrobbleController(mediaFileService, statusService, audioScrobblerService),
                                runtime.createNowPlayingController(statusService, responseMapper),
                                runtime.createUploadStatusController(statusService, databaseService),
                                runtime.createStatisticsController(statusService, securityService),
                                runtime.createScheduledTasksController(taskSchedulingService),
                                runtime.createPathWatchersController(pathWatcherService),
                                runtime.createServerHealthController(healthContributors, objectMapper),
                                runtime.createCacheStatisticsController(jCacheCacheManager, meterRegistry),
                                runtime.createSongController(securityService, responseMapper),
                                runtime.createVideosController(mediaFolderService, responseMapper),
                                runtime.createLyricsController(lyricsService),
                                new MediaFileCommentController(mediaFileService, securityService, jaxbWriter),
                                new TagEditController(tagEditService, jaxbWriter),
                                runtime.createRatingController(mediaFileService, ratingService),
                                runtime.createDownloadRestController(), downloadService,
                                shareService, mediaFileService,
                                playerResolver, playerService, securityService, localeResolver,
                                new CoverArtRestController(coverArtImageService), new AvatarRestController(settingsService), new AvatarUploadController(settingsService, jaxbWriter),
                                new HlsRestController(hlsPlaylistService), new HlsSegmentRestController(hlsSegmentService), new StreamRestController(mediaStreamService),
                                new PublicShareController(shareService, mediaFileService, mediaFolderService, jwtSecurityService, metadataParsers, objectMapper), new CaptionsRestController(captionService, mediaFolderService, objectMapper),
                                runtime.createPasswordRecoveryController(passwordRecoveryService, objectMapper),
                                runtime.createServerInfoController(serverInfoService, objectMapper, settingsService), jaxbWriter,
                                () -> InfrastructureErrorSettings.from(runtime.getConfiguration()),
                                objectMapper, metricsManager, lifecycleRegistry, settingsService),
                        "/internal/ktor/*");
        registration.addUrlMappings(SearchController.PATHS);
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
        registration.addUrlMappings(AlbumsController.PATHS);
        registration.addUrlMappings(BookmarksController.PATHS);
        registration.addUrlMappings(StarredController.PATHS);
        registration.addUrlMappings(JukeboxController.PATHS);
        registration.addUrlMappings(UsersController.PATHS);
        registration.addUrlMappings(TranscodingSettingsController.READ_PATH, TranscodingSettingsController.SAVE_PATH);
        registration.addUrlMappings(CredentialsManagementController.READ_PATH, CredentialsManagementController.KEYS_PATH, CredentialsManagementController.CREATE_PATH, CredentialsManagementController.UPDATE_PATH, CredentialsManagementController.ADMIN_PATH);
        registration.addUrlMappings(DLNASettingsController.READ_PATH, DLNASettingsController.SAVE_PATH);
        registration.addUrlMappings(DatabaseSettingsController.PATHS);
        registration.addUrlMappings(PersonalSettingsController.READ_PATH, PersonalSettingsController.SAVE_PATH);
        registration.addUrlMappings(PlayerSettingsController.READ_PATH, PlayerSettingsController.SAVE_PATH, PlayerSettingsController.CLONE_PATH, PlayerSettingsController.DELETE_PATH);
        registration.addUrlMappings(AdvancedSettingsController.READ_PATH, AdvancedSettingsController.SAVE_PATH);
        registration.addUrlMappings(GeneralSettingsController.READ_PATH);
        registration.addUrlMappings(GeneralSettingsController.WRITE_PATHS);
        registration.addUrlMappings(MusicFolderSettingsController.READ_PATH);
        registration.addUrlMappings(MusicFolderSettingsController.WRITE_PATHS);
        registration.addUrlMappings(PasswordController.PATHS);
        registration.addUrlMappings(PlayQueueController.PATHS);
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
        registration.addUrlMappings(CoverArtRestController.PATHS);
        registration.addUrlMappings(CoverArtRestController.EXTERNAL_PATHS);
        registration.addUrlMappings(MediaFileCommentController.PATH);
        registration.addUrlMappings(TagEditController.PATHS);
        registration.addUrlMappings(AvatarRestController.PATHS);
        registration.addUrlMappings(AvatarUploadController.PATH);
        registration.addUrlMappings(UploadController.PATH);
        registration.addUrlMappings(PlaylistExportController.PATH);
        registration.addUrlMappings(PlaylistImportController.PATH);
        registration.addUrlMappings(LiveQueueExportController.PATH);
        registration.addUrlMappings("/ext/rest/share/*");
        registration.addUrlMappings(CaptionsRestController.PATHS);
        registration.addUrlMappings(ServerInfoController.PATHS);
        registration.addUrlMappings(PasswordRecoveryController.PATH);
        registration.addUrlMappings(HlsRestController.PATHS);
        registration.addUrlMappings(HlsRestController.EXTERNAL_PATHS);
        registration.addUrlMappings("/ext/hls/segment/*");
        registration.addUrlMappings(HlsSegmentRestController.PATHS);
        registration.addUrlMappings(StreamRestController.PATHS);
        registration.addUrlMappings("/ext/stream", "/ext/stream/*");
        // Keep container error dispatch in Ktor.
        registration.addUrlMappings(runtime.getConfiguration().getProperty("server.error.path", "/error"), "");
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
