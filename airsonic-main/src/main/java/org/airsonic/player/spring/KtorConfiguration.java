package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
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
import org.airsonic.player.service.hls.HlsPlaylistService;
import org.airsonic.player.controller.rest.shares.PublicShareController;
import org.airsonic.player.controller.captions.CaptionsRestController;
import org.airsonic.player.controller.rest.recovery.PasswordRecoveryController;
import org.airsonic.player.controller.rest.server.ServerInfoController;
import org.airsonic.player.controller.rest.hls.HlsRestController;
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController;
import org.airsonic.player.service.hls.HlsSegmentService;
import org.airsonic.player.controller.rest.stream.StreamRestController;
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
import org.airsonic.player.controller.rest.playqueue.PlayQueueController;
import org.airsonic.player.dao.PlayQueueDao;
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
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.service.search.SearchServiceImpl;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.i18n.LocaleService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.JukeboxService;
import org.airsonic.player.service.JukeboxLegacySubsonicService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.ConfigurationPropertiesService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;

/** Connects Ktor to the existing servlet container during the migration. */
public class KtorConfiguration {

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

    public org.airsonic.player.runtime.DaoGraph daoGraph(RuntimeCompositionRoot runtime) {
        return runtime.createPersistenceGraph().getDaos();
    }

    public PlayerDao playerDao(RuntimeCompositionRoot runtime) throws Exception {
        return runtime.createPlayerDao();
    }

    public PlayerDaoPlayQueueFactory playerDaoPlayQueueFactory(RuntimeCompositionRoot runtime) {
        return runtime.createPlayerDaoPlayQueueFactory();
    }

    public StatusService statusService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, SettingsService settingsService,
            TaskSchedulingService taskSchedulingService) {
        return runtime.createStatusService(mediaFileService, mediaFolderService, settingsService, taskSchedulingService);
    }

    public SearchServiceImpl searchServiceImpl(RuntimeCompositionRoot runtime, MediaFileService mediaFileService) {
        return runtime.createSearchService(mediaFileService);
    }

    public PlaylistService playlistService(RuntimeCompositionRoot runtime, SecurityService securityService,
            SettingsService settingsService, MediaFileService mediaFileService, CoverArtService coverArtService)
            throws Exception {
        return runtime.createPlaylistService(securityService, settingsService, mediaFileService, coverArtService);
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

    public TagEditService tagEditService(RuntimeCompositionRoot runtime, MediaFileService mediaFileService,
            SecurityService securityService) {
        return runtime.createTagEditService(runtime.createMetaDataParserFactory(), mediaFileService, securityService);
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

    public SettingsService settingsService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph) {
        // The graph argument preserves startup ordering while DAOs remain owned by RuntimeCompositionRoot.
        return runtime.createSettingsService();
    }

    public MediaFolderService mediaFolderService(RuntimeCompositionRoot runtime) {
        return runtime.createMediaFolderService();
    }

    public MediaFileService mediaFileService(RuntimeCompositionRoot runtime, SecurityService securityService, SettingsService settingsService,
            org.airsonic.player.runtime.DaoGraph graph, CoverArtService coverArtService) {
        return runtime.createMediaFileService(securityService, settingsService, graph.getMediaFileDao(), graph.getAlbumDao(),
                runtime.createJaudiotaggerParser(), runtime.createMetaDataParserFactory(), coverArtService,
                runtime.createLocaleService(settingsService));
    }

    public TranscodingService transcodingService(RuntimeCompositionRoot runtime, org.airsonic.player.runtime.DaoGraph graph,
            SettingsService settingsService, MediaFolderService mediaFolderService) {
        return runtime.createTranscodingService(graph.getTranscodingDao(), settingsService, mediaFolderService);
    }

    public TaskSchedulingService taskSchedulingService(RuntimeCompositionRoot runtime) {
        return runtime.createTaskSchedulingService();
    }

    public DownloadService downloadService(RuntimeCompositionRoot runtime, StatusService statusService, SecurityService securityService,
            PlaylistService playlistService, SettingsService settingsService, MediaFileService mediaFileService,
            CoverArtService coverArtService) {
        return runtime.createDownloadService(statusService, securityService, playlistService, settingsService,
                mediaFileService, coverArtService);
    }

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration(
            RuntimeCompositionRoot runtime,
            JWTSecurityService jwtSecurityService,
            MediaFileService mediaFileService,
            org.airsonic.player.runtime.DaoGraph graph,
            MediaFolderService mediaFolderService,
            SecurityService securityService,
            CredentialEncoderRegistry credentialEncoderRegistry,
            SettingsService settingsService,
            UPnPService upnpService,
            StatusService statusService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            TranscodingService transcodingService
    ) throws Exception {
        TaskSchedulingService taskSchedulingService = runtime.createTaskSchedulingService();
        CoverArtService coverArtService = runtime.createCoverArtService(graph.getCoverArtDao());
        runtime.createCoverArtAccentColorBackfill(coverArtService);
        LocaleService localeService = runtime.createLocaleService(settingsService);
        IndexManager indexManager = runtime.createIndexManager();
        SearchService albumSearchService = runtime.createSearchService(mediaFileService);
        PlaylistService playlistService = runtime.createPlaylistService(
                securityService, settingsService, mediaFileService, coverArtService);
        DownloadService downloadService = runtime.createDownloadService(
                statusService, securityService, playlistService, settingsService, mediaFileService, coverArtService);
        CoverArtImageService coverArtImageService = coverArtImageService(runtime, mediaFileService, coverArtService,
                transcodingService, settingsService, playlistService, graph);
        HlsPlaylistService hlsPlaylistService = runtime.createHlsPlaylistService(jwtSecurityService);
        HlsSegmentService hlsSegmentService = runtime.createHlsSegmentService(
                mediaFileService, securityService, statusService, settingsService, transcodingService);
        TagEditService tagEditService = runtime.createTagEditService(
                runtime.createMetaDataParserFactory(), mediaFileService, securityService);
        AudioScrobblerService audioScrobblerService = runtime.createAudioScrobblerService(settingsService, securityService);
        org.airsonic.player.service.MediaScannerService mediaScannerService = runtime.createMediaScannerService(
                playlistService, settingsService, indexManager, mediaFileService, mediaFolderService, coverArtService);
        JukeboxLegacySubsonicService legacyJukebox = runtime.createJukeboxLegacySubsonicService(
                transcodingService, audioScrobblerService, statusService, settingsService, securityService,
                mediaFileService);
        JukeboxService jukeboxService = runtime.createJukeboxService(legacyJukebox);
        PlayerService playerService = runtime.createPlayerService(
                runtime.createPlayerDao(), statusService, securityService, transcodingService);
        org.airsonic.player.service.DatabaseService databaseService = runtime.createDatabaseService(
                settingsService, graph.getDatabaseDao(), taskSchedulingService);
        RatingService ratingService = runtime.createRatingService(
                graph.getRatingDao(), securityService, mediaFileService);
        LastFmService lastFmService = runtime.createLastFmService(
                graph.getMediaFileDao(), mediaFileService, graph.getArtistDao());
        PlayQueueService playQueueService = runtime.createPlayQueueService(
                albumSearchService, jukeboxService, settingsService, mediaFileService, lastFmService, ratingService,
                playlistService, graph.getMediaFileDao(), mediaFolderService, graph.getPlayQueueDao(), jwtSecurityService);
        JAXBWriter jaxbWriter = runtime.createSubsonicJaxbWriter();
        SubsonicResponseMapper responseMapper = runtime.createSubsonicResponseMapper(
                mediaFileService, ratingService, coverArtService, transcodingService);
        org.airsonic.player.controller.rest.search.SearchService subsonicSearchService =
                runtime.createSubsonicSearchService(albumSearchService, mediaFolderService, responseMapper);
        SearchController searchController = runtime.createSubsonicSearchController(subsonicSearchService);
        MusicFoldersController musicFoldersController = runtime.createSubsonicMusicFoldersController(
                runtime.createSubsonicMusicFoldersService(mediaFolderService));
        GenresController genresController = runtime.createSubsonicGenresController();
        MusicIndexService musicIndexService = runtime.createMusicIndexService(settingsService, mediaFileService);
        ArtistsController artistsController = runtime.createSubsonicArtistsController(
                musicIndexService, settingsService, mediaFolderService, responseMapper);
        RestPlayerResolver playerResolver = runtime.createRestPlayerResolver(playerService);
        org.airsonic.player.service.ShareService shareService = runtime.createShareService(
                graph.getShareDao(), mediaFileService, jwtSecurityService);
        ArtistDao artistDao = graph.getArtistDao();
        AlbumDao albumDao = graph.getAlbumDao();
        MediaFileDao mediaFileDao = graph.getMediaFileDao();
        PlayQueueDao playQueueDao = graph.getPlayQueueDao();
        org.airsonic.player.ktor.KordinatorSessionService kordinatorSessionService =
                runtime.createKordinatorSessionService(securityService, credentialEncoderRegistry, settingsService);
        org.airsonic.player.ktor.KordinatorAuthRoutes kordinatorAuthRoutes =
                runtime.createKordinatorAuthRoutes(kordinatorSessionService);
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
                                runtime.createPlaylistsController(playlistService, mediaFileService, securityService,
                                        responseMapper, mediaFolderService, playerService, localeService),
                                runtime.createKordinatorPlaylistRoutes(playlistService, securityService, mediaFileService),
                                runtime.createKordinatorAlbumRoutes(mediaFileService, mediaFolderService, securityService),
                                runtime.createKordinatorArtistRoutes(mediaFolderService),
                                runtime.createKordinatorSearchRoutes(albumSearchService, mediaFolderService, securityService),
                                kordinatorAuthRoutes,
                                runtime.createKordinatorFavoriteRoutes(mediaFileService, mediaFolderService, securityService),
                                runtime.createKordinatorLibraryRoutes(mediaFileService, mediaFolderService, securityService),
                                kordinatorSessionService,
                                runtime.createUploadController(runtime.createUploadFileService(securityService),
                                        statusService, securityService, settingsService),
                                runtime.createPlaylistExportController(playlistService),
                                runtime.createPlaylistImportController(playlistService),
                                runtime.createLiveQueueExportController(transcodingService, jwtSecurityService, securityService),
                                runtime.createSharesController(shareService, mediaFileService, mediaFolderService,
                                        responseMapper, playerService, playlistService, securityService),
                                runtime.createSubsonicAlbumsController(mediaFileService, mediaFolderService,
                                        ratingService, albumSearchService, responseMapper),
                                runtime.createBookmarksController(runtime.createBookmarkService(
                                        graph.getBookmarkDao(), mediaFileService), mediaFileService, responseMapper),
                                runtime.createStarredController(mediaFolderService, responseMapper),
                                runtime.createJukeboxController(playQueueService, jukeboxService, responseMapper),
                                runtime.createUsersController(securityService, settingsService, mediaFolderService,
                                        runtime.createUserAdministrationService(securityService, settingsService)),
                                runtime.createDLNASettingsController(settingsService, upnpService),
                                runtime.createDatabaseSettingsController(settingsService, databaseService,
                                        statusService, securityService, runtime.createConfigurationPropertiesService()),
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
                                runtime.createIndexesController(settingsService, mediaFolderService,
                                        runtime.createMusicIndexService(settingsService, mediaFileService),
                                        runtime.createMusicIndexStateService(mediaScannerService, settingsService),
                                        ratingService, responseMapper, playerService),
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
                                runtime.createPathWatchersController(runtime.createPathWatcherService()),
                                runtime.createServerHealthController(
                                        runtime.createHealthContributorRegistry(runtime.createDataSource()), objectMapper),
                                runtime.createCacheStatisticsController(runtime.createCacheManager()),
                                runtime.createSongController(securityService, responseMapper),
                                runtime.createVideosController(mediaFolderService, responseMapper),
                                runtime.createLyricsController(runtime.createLyricsService()),
                                runtime.createMediaFileCommentController(mediaFileService, securityService),
                                runtime.createTagEditController(tagEditService),
                                runtime.createRatingController(mediaFileService, ratingService),
                                runtime.createDownloadRestController(), downloadService,
                                shareService, mediaFileService,
                                playerResolver, playerService, securityService,
                                new LocaleResolver(securityService, localeService),
                                runtime.createCoverArtRestController(coverArtImageService),
                                runtime.createAvatarRestController(settingsService),
                                runtime.createAvatarUploadController(settingsService),
                                runtime.createHlsRestController(hlsPlaylistService),
                                runtime.createHlsSegmentRestController(hlsSegmentService),
                                runtime.createStreamRestController(runtime.createMediaStreamService(
                                        statusService, securityService, settingsService, transcodingService,
                                        audioScrobblerService, mediaFileService, albumSearchService)),
                                runtime.createPublicShareController(shareService, mediaFileService, mediaFolderService,
                                        jwtSecurityService, runtime.createMetaDataParserFactory(), objectMapper),
                                runtime.createCaptionsRestController(runtime.createCaptionService(mediaFileService,
                                        mediaFolderService, settingsService, runtime.createMetaDataParserFactory(),
                                        jwtSecurityService), mediaFolderService, objectMapper),
                                runtime.createPasswordRecoveryController(runtime.createPasswordRecoveryService(
                                        settingsService, securityService), objectMapper),
                                runtime.createServerInfoController(
                                        runtime.createServerInfoService(runtime.createVersionService(), settingsService),
                                        objectMapper, settingsService), jaxbWriter,
                                () -> InfrastructureErrorSettings.from(runtime.getConfiguration()),
                                objectMapper, runtime.createMetricsManager(), runtime.getLifecycleRegistry(), settingsService),
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
