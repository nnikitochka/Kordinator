package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.TagEditService;
import org.airsonic.player.controller.rest.tags.MediaFileCommentController;
import org.airsonic.player.controller.rest.tags.TagEditController;
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
import org.airsonic.player.controller.rest.nowplaying.NowPlayingPageController;
import org.airsonic.player.controller.rest.more.MorePageController;
import org.airsonic.player.controller.rest.indexes.LeftPageController;
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
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.controller.rest.users.PasswordController;
import org.airsonic.player.service.SettingsService;
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
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.JukeboxService;
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

/** Connects Ktor to the existing servlet container during the migration. */
@Configuration
public class KtorConfiguration {

    // Keep the health contributors independent of Actuator HTTP endpoint exposure.
    @Bean
    public org.springframework.boot.actuate.health.HealthContributorRegistry serverHealthContributors(
            java.util.Map<String, org.springframework.boot.actuate.health.HealthContributor> contributors) {
        return new org.springframework.boot.actuate.health.DefaultHealthContributorRegistry(contributors);
    }

    @Bean
    public org.springframework.boot.actuate.health.StatusAggregator serverHealthStatusAggregator(
            @org.springframework.beans.factory.annotation.Value("${management.endpoint.health.status.order:DOWN,OUT_OF_SERVICE,UP,UNKNOWN}")
            java.util.List<String> order) {
        return new org.springframework.boot.actuate.health.SimpleStatusAggregator(order);
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
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            SecurityService securityService,
            SettingsService settingsService,
            MusicIndexService musicIndexService,
            AudioScrobblerService audioScrobblerService,
            UPnPService upnpService,
            StatusService statusService,
            javax.cache.CacheManager jCacheCacheManager,
            org.springframework.boot.actuate.health.HealthContributorRegistry healthContributors,
            org.springframework.boot.actuate.health.StatusAggregator healthStatusAggregator,
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
            UploadFileService uploadFileService
    ) {
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(
                        new AirsonicKtorServlet(searchController, musicFoldersController, genresController,
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
                                new JukeboxController(playQueueService, jukeboxService, responseMapper, jaxbWriter),
                                new UsersController(securityService, settingsService, mediaFolderService, userAdministrationService, transcodingService, jaxbWriter),
                                new DLNASettingsController(settingsService, upnpService, jaxbWriter),
                                new DatabaseSettingsController(settingsService, databaseService, statusService, securityService, jaxbWriter),
                                new CredentialsManagementController(securityService, settingsService, jaxbWriter, transactionManager),
                                new PersonalSettingsController(settingsService, securityService, jaxbWriter),
                                new PlayerSettingsController(playerService, transcodingService, playQueueService, jaxbWriter),
                                new AdvancedSettingsController(settingsService, jaxbWriter),
                                new TranscodingSettingsController(transcodingService, settingsService, jaxbWriter),
                                new GeneralSettingsController(new GeneralSettingsAdministrationService(settingsService, playlistService), jaxbWriter),
                                new MusicFolderSettingsController(new MusicFolderAdministrationService(settingsService, mediaScannerService, mediaFolderService, artistDao, albumDao, mediaFileDao, indexManager, playlistService, coverArtService), jaxbWriter),
                                new PasswordController(securityService, jaxbWriter),
                                new PlayQueueController(playQueueDao, mediaFileService, responseMapper, jaxbWriter),
                                new IndexesController(settingsService, mediaFolderService, musicIndexService, musicIndexStateService, mediaFileDao, ratingService, responseMapper, jaxbWriter),
                                new SongsByGenreController(mediaFileDao, mediaFolderService, responseMapper, jaxbWriter),
                                new TopSongsController(lastFmService, mediaFolderService, responseMapper, jaxbWriter),
                                new MusicDirectoryController(mediaFileService, mediaFileDao, securityService, ratingService, responseMapper, jaxbWriter, mediaDirectoryService),
                                new RandomSongsController(albumSearchService, mediaFolderService, responseMapper, jaxbWriter),
                                new ScrobbleController(mediaFileService, statusService, audioScrobblerService, jaxbWriter),
                                new NowPlayingController(statusService, responseMapper, jaxbWriter),
                                new NowPlayingPageController(playerService, statusService, mediaFileService),
                                new MorePageController(settingsService, mediaFileService, mediaFolderService, objectMapper),
                                new LeftPageController(settingsService, mediaFolderService, musicIndexService, musicIndexStateService, mediaScannerService, indexManager, playerService, objectMapper),
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
                                new WebsiteSettingsController(settingsService, jaxbWriter),
                                new LiveQueueController(playQueueService, jaxbWriter, mediaFileService, playlistService, securityService, playQueueDao, playerService),
                                new RatingController(mediaFileService, ratingService, jaxbWriter),
                                new DownloadRestController(jaxbWriter, downloadService, mediaFileService, playlistService, securityService), downloadService,
                                shareService, mediaFileService,
                                playerResolver, playerService, securityService, localeResolver,
                                new CoverArtRestController(coverArtImageService), new AvatarRestController(settingsService), new AvatarUploadController(settingsService, jaxbWriter),
                                new HlsRestController(hlsPlaylistService), new HlsSegmentRestController(hlsSegmentService), new StreamRestController(mediaStreamService),
                                new PublicShareController(shareService, mediaFileService, mediaFolderService, jwtSecurityService, metadataParsers, objectMapper), new CaptionsRestController(captionService, mediaFolderService, objectMapper),
                                new VideoPlayerRestController(videoPlaybackService, objectMapper), new PasswordRecoveryController(passwordRecoveryService, objectMapper), new ServerInfoController(serverInfoService, objectMapper, settingsService), new PlayQueuePageController(playerService, settingsService, objectMapper), jaxbWriter),
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
        registration.addUrlMappings("/createShare", "/createShare.view");
        registration.addUrlMappings(AlbumsController.PATHS);
        registration.addUrlMappings(BookmarksController.PATHS);
        registration.addUrlMappings(StarredController.PATHS);
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
        registration.addUrlMappings(CoverArtActionsController.PATHS);
        registration.addUrlMappings(MediaFileCommentController.PATH);
        registration.addUrlMappings(TagEditController.PATHS);
        registration.addUrlMappings(WebsiteSettingsController.PATHS);
        registration.addUrlMappings(LiveQueueController.PATHS);
        registration.addUrlMappings(RandomQueueController.PATH);
        registration.addUrlMappings(LiveQueueExportController.PATH);
        registration.addUrlMappings(AvatarRestController.PATHS);
        registration.addUrlMappings(AvatarUploadController.PATH);
        registration.addUrlMappings(UploadController.PATH);
        registration.addUrlMappings(PlaylistExportController.PATH);
        registration.addUrlMappings(PlaylistImportController.PATH, "/importPlaylist", "/importPlaylist.view");
        registration.addUrlMappings("/ext/share/*", "/ext/rest/share/*");
        registration.addUrlMappings(CaptionsRestController.PATHS);
        registration.addUrlMappings(ServerInfoController.PATHS);
        registration.addUrlMappings(ServerInfoController.GETTING_STARTED_PATH, "/gettingStarted", "/gettingStarted.view");
        registration.addUrlMappings(NowPlayingPageController.PATHS);
        registration.addUrlMappings(MorePageController.PATH);
        registration.addUrlMappings(MorePageController.PAGE_PATHS);
        registration.addUrlMappings(LeftPageController.DATA_PATH, LeftPageController.SELECT_PATH, "/left", LeftPageController.PAGE_PATH);
        registration.addUrlMappings("/help", "/help.view");
        registration.addUrlMappings(PasswordRecoveryController.PATH, "/recover", "/recover.view");
        registration.addUrlMappings(VideoPlayerRestController.PATH, "/videoPlayer", "/videoPlayer.view");
        registration.addUrlMappings(HlsRestController.PATHS);
        registration.addUrlMappings(HlsRestController.WEBSITE_PATHS);
        registration.addUrlMappings("/hls/segment/*", "/ext/hls/segment/*");
        registration.addUrlMappings(HlsSegmentRestController.PATHS);
        registration.addUrlMappings(StreamRestController.PATHS);
        registration.addUrlMappings("/stream", "/stream/*", "/ext/stream", "/ext/stream/*");
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
