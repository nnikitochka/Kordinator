package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.TagEditService;
import org.airsonic.player.controller.rest.tags.TagEditController;
import org.airsonic.player.service.CoverArtSearchService;
import org.airsonic.player.controller.rest.cover.CoverArtActionsController;
import org.airsonic.player.controller.rest.settings.WebsiteSettingsController;
import org.airsonic.player.controller.rest.livequeue.LiveQueueController;
import org.airsonic.player.service.MediaDirectoryService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.LeftController;
import org.airsonic.player.controller.HLSController;
import org.airsonic.player.controller.rest.hls.HlsRestController;
import org.airsonic.player.controller.rest.stream.StreamRestController;
import org.airsonic.player.controller.StreamController;
import org.airsonic.player.controller.rest.cover.CoverArtRestController;
import org.airsonic.player.controller.rest.avatar.AvatarRestController;
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
import org.airsonic.player.controller.rest.download.DownloadRestController;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.DownloadService;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.CoverArtImageService;
import org.airsonic.player.controller.rest.playqueue.PlayQueueController;
import org.airsonic.player.dao.PlayQueueDao;
import org.airsonic.player.controller.UserSettingsController;
import org.airsonic.player.controller.rest.users.UsersController;
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

    @Bean
    public DownloadService downloadService(StatusService statusService, SecurityService securityService,
            PlaylistService playlistService, SettingsService settingsService, MediaFileService mediaFileService,
            MediaFolderService mediaFolderService, CoverArtService coverArtService) {
        return new DownloadService(statusService, securityService, playlistService, settingsService, mediaFileService,
                mediaFolderService, coverArtService);
    }

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration(
            SearchController searchController,
            MusicFoldersController musicFoldersController,
            GenresController genresController,
            ArtistsController artistsController,
            JAXBWriter jaxbWriter,
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
            SecurityService securityService,
            SettingsService settingsService,
            SimpMessagingTemplate messagingTemplate,
            MusicIndexService musicIndexService,
            AudioScrobblerService audioScrobblerService,
            StatusService statusService,
            DownloadService downloadService,
            LeftController leftController,
            UserSettingsController userSettingsController,
            LocaleResolver localeResolver,
            CoverArtImageService coverArtImageService,
            HLSController hlsController,
            StreamController streamController
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
                                new SharesController(shareService, mediaFileService, mediaFolderService, responseMapper, jaxbWriter),
                                new AlbumsController(albumDao, mediaFileDao, mediaFileService, mediaFolderService,
                                        ratingService, albumSearchService, responseMapper, jaxbWriter),
                                new BookmarksController(bookmarkService, mediaFileService, responseMapper, jaxbWriter),
                                new StarredController(mediaFileDao, albumDao, artistDao, mediaFolderService, responseMapper, jaxbWriter),
                                new JukeboxController(playQueueService, jukeboxService, responseMapper, jaxbWriter),
                                new UsersController(securityService, settingsService, mediaFolderService, userSettingsController, jaxbWriter),
                                new PasswordController(securityService, jaxbWriter),
                                new PlayQueueController(playQueueDao, mediaFileService, responseMapper, jaxbWriter),
                                new IndexesController(settingsService, mediaFolderService, musicIndexService, mediaFileDao, ratingService, responseMapper, jaxbWriter),
                                new SongsByGenreController(mediaFileDao, mediaFolderService, responseMapper, jaxbWriter),
                                new TopSongsController(lastFmService, mediaFolderService, responseMapper, jaxbWriter),
                                new MusicDirectoryController(mediaFileService, mediaFileDao, securityService, ratingService, responseMapper, jaxbWriter, mediaDirectoryService),
                                new RandomSongsController(albumSearchService, mediaFolderService, responseMapper, jaxbWriter),
                                new ScrobbleController(mediaFileService, statusService, audioScrobblerService, jaxbWriter),
                                new NowPlayingController(statusService, responseMapper, jaxbWriter),
                                new SongController(mediaFileDao, securityService, responseMapper, jaxbWriter),
                                new VideosController(mediaFileDao, mediaFolderService, responseMapper, jaxbWriter),
                                new LyricsController(lyricsService, jaxbWriter),
                                new CoverArtActionsController(coverArtSearchService, jaxbWriter),
                                new TagEditController(tagEditService, jaxbWriter),
                                new WebsiteSettingsController(settingsService, messagingTemplate, jaxbWriter),
                                new LiveQueueController(playQueueService, jaxbWriter, mediaFileService, playlistService, securityService, playQueueDao),
                                new RatingController(mediaFileService, ratingService, jaxbWriter),
                                new DownloadRestController(jaxbWriter), downloadService,
                                shareService, mediaFileService,
                                playerResolver, playerService, securityService, localeResolver, leftController,
                                new CoverArtRestController(coverArtImageService), new AvatarRestController(settingsService),
                                new HlsRestController(), hlsController, new StreamRestController(), streamController, jaxbWriter),
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
        registration.addUrlMappings(DownloadRestController.PATHS);
        registration.addUrlMappings(CoverArtRestController.PATHS);
        registration.addUrlMappings(CoverArtActionsController.PATHS);
        registration.addUrlMappings(TagEditController.PATHS);
        registration.addUrlMappings(WebsiteSettingsController.PATHS);
        registration.addUrlMappings(LiveQueueController.PATHS);
        registration.addUrlMappings(AvatarRestController.PATHS);
        registration.addUrlMappings(HlsRestController.PATHS);
        registration.addUrlMappings(StreamRestController.PATHS);
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
