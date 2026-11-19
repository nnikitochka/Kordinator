package org.airsonic.player.spring

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.isHandled
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.RoutingFailureStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.servlet.jakarta.ServletApplicationEngine
import io.ktor.server.servlet.jakarta.ServletApplicationRequest
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.FilterChain
import org.airsonic.player.monitor.MetricsManager
import org.airsonic.player.controller.rest.stream.StreamRestController
import org.airsonic.player.controller.JAXBWriter
import org.airsonic.player.controller.rest.server.ServerInfoController
import org.airsonic.player.controller.rest.recovery.PasswordRecoveryController
import org.airsonic.player.controller.rest.video.VideoPlayerRestController
import org.airsonic.player.controller.captions.CaptionsRestController
import org.airsonic.player.controller.rest.shares.PublicShareController
import org.airsonic.player.controller.rest.hls.HlsRestController
import org.airsonic.player.controller.rest.hls.HlsSegmentRestController
import org.airsonic.player.controller.rest.starred.StarredController
import org.airsonic.player.controller.rest.starred.StarredPageController
import org.airsonic.player.controller.rest.starred.StarredRequest
import org.airsonic.player.controller.rest.jukebox.JukeboxController
import org.airsonic.player.controller.rest.jukebox.JukeboxRequest
import org.airsonic.player.controller.rest.RestPlayerResolver
import org.airsonic.player.controller.rest.bookmarks.BookmarksController
import org.airsonic.player.controller.rest.bookmarks.BookmarksRequest
import org.airsonic.player.controller.rest.albums.AlbumsController
import org.airsonic.player.controller.rest.albums.AlbumsRequest
import org.airsonic.player.controller.rest.info.album.AlbumInfoController
import org.airsonic.player.controller.rest.info.album.AlbumInfoRequest
import org.airsonic.player.controller.rest.info.artist.ArtistInfoController
import org.airsonic.player.controller.rest.info.artist.ArtistInfoRequest
import org.airsonic.player.controller.rest.artists.ArtistsController
import org.airsonic.player.controller.rest.artists.ArtistsRequest
import org.airsonic.player.controller.rest.genres.GenresController
import org.airsonic.player.controller.rest.music.folder.MusicFoldersController
import org.airsonic.player.controller.rest.ping.PingController
import org.airsonic.player.controller.rest.upload.UploadController
import org.airsonic.player.controller.rest.playlists.PlaylistExportController
import org.airsonic.player.controller.rest.playlists.PlaylistImportController
import org.airsonic.player.controller.rest.playlists.PlaylistsController
import org.airsonic.player.controller.rest.playlists.PlaylistsRequest
import org.airsonic.player.controller.rest.scan.ScanController
import org.airsonic.player.controller.rest.shares.SharesController
import org.airsonic.player.controller.rest.shares.SharesRequest
import org.airsonic.player.controller.rest.search.SearchController
import org.airsonic.player.controller.rest.search.SearchPageController
import org.airsonic.player.controller.rest.home.HomePageController
import org.airsonic.player.controller.rest.cover.ChangeCoverArtPageController
import org.airsonic.player.controller.rest.music.similar.SimilarSongsController
import org.airsonic.player.controller.rest.music.similar.SimilarSongsRequest
import org.airsonic.player.service.MediaStreamService
import org.airsonic.player.service.CoverArtImageService
import org.airsonic.player.filter.RequestEncodingPolicy
import org.airsonic.player.controller.rest.avatar.AvatarRestController
import org.airsonic.player.controller.rest.proxy.ProxyRestController
import org.airsonic.player.controller.rest.avatar.AvatarUploadController
import io.ktor.http.parseQueryString
import org.airsonic.player.controller.rest.cover.CoverArtActionsController
import org.airsonic.player.controller.rest.tags.MediaFileCommentController
import org.airsonic.player.controller.rest.tags.TagEditController
import org.airsonic.player.controller.rest.tags.TagEditorPageController
import org.airsonic.player.controller.rest.settings.WebsiteSettingsController
import org.airsonic.player.controller.rest.livequeue.LiveQueueExportController
import org.airsonic.player.controller.rest.livequeue.RandomQueueController
import org.airsonic.player.controller.rest.livequeue.LiveQueueController
import org.airsonic.player.controller.rest.cover.CoverArtRestController
import org.airsonic.player.controller.rest.admin.MusicFolderSettingsController
import org.airsonic.player.controller.rest.admin.GeneralSettingsController
import org.airsonic.player.controller.rest.admin.TranscodingSettingsController
import org.airsonic.player.controller.rest.admin.AdvancedSettingsController
import org.airsonic.player.controller.rest.admin.PlayerSettingsController
import org.airsonic.player.controller.rest.settings.PersonalSettingsController
import org.airsonic.player.controller.rest.admin.DLNASettingsController
import org.airsonic.player.controller.rest.admin.DatabaseSettingsController
import org.airsonic.player.controller.rest.settings.CredentialsManagementController
import org.airsonic.player.controller.rest.users.UsersController
import org.airsonic.player.controller.rest.users.PasswordController
import org.airsonic.player.controller.rest.users.UsersRequest
import org.airsonic.player.controller.rest.playqueue.PlayQueueController
import org.airsonic.player.controller.rest.playqueue.PlayQueuePageController
import org.airsonic.player.controller.rest.playqueue.PlayQueueRequest
import org.airsonic.player.controller.rest.indexes.IndexesController
import org.airsonic.player.controller.rest.indexes.IndexesRequest
import org.airsonic.player.controller.rest.music.genre.SongsByGenreController
import org.airsonic.player.controller.rest.music.genre.SongsByGenreRequest
import org.airsonic.player.controller.rest.music.top.TopSongsController
import org.airsonic.player.controller.rest.music.top.TopSongsRequest
import org.airsonic.player.controller.rest.music.directory.MusicDirectoryController
import org.airsonic.player.controller.rest.music.directory.MusicDirectoryRequest
import org.airsonic.player.security.ServletCallerContextAdapter
import org.airsonic.player.controller.rest.music.random.RandomSongsController
import org.airsonic.player.controller.rest.music.random.RandomSongsRequest
import org.airsonic.player.controller.rest.scrobble.ScrobbleController
import org.airsonic.player.controller.rest.scrobble.ScrobbleRequest
import org.airsonic.player.controller.rest.music.song.SongController
import org.airsonic.player.controller.rest.music.song.SongRequest
import org.airsonic.player.controller.rest.videos.VideosController
import org.airsonic.player.controller.rest.videos.VideosRequest
import org.airsonic.player.controller.rest.lyrics.LyricsController
import org.airsonic.player.controller.rest.rating.RatingController
import org.airsonic.player.controller.rest.rating.RatingRequest
import org.airsonic.player.controller.rest.nowplaying.NowPlayingController
import org.airsonic.player.controller.rest.nowplaying.NowPlayingPageController
import org.airsonic.player.controller.rest.nowplaying.RightPageController
import org.airsonic.player.controller.rest.more.MorePageController
import org.airsonic.player.controller.rest.indexes.LeftPageController
import org.airsonic.player.controller.rest.indexes.IndexPageController
import org.airsonic.player.controller.rest.bookmarks.BookmarksPageController
import org.airsonic.player.controller.rest.playlists.PlaylistPageController
import org.airsonic.player.controller.rest.music.MainPageController
import org.airsonic.player.controller.rest.playlists.PlaylistsPageController
import org.airsonic.player.controller.rest.statistics.ScheduledTasksController
import org.airsonic.player.controller.rest.statistics.ServerHealthController
import org.airsonic.player.controller.rest.statistics.PathWatchersController
import org.airsonic.player.controller.rest.statistics.CacheStatisticsController
import org.airsonic.player.controller.rest.statistics.StatisticsController
import org.airsonic.player.controller.rest.upload.UploadStatusController
import org.airsonic.player.controller.rest.download.DownloadRestController
import org.airsonic.player.controller.rest.download.DownloadRequest
import org.airsonic.player.controller.rest.error.ErrorCode
import org.airsonic.player.service.DownloadService
import org.springframework.http.ContentDisposition
import org.springframework.web.context.request.ServletWebRequest
import org.apache.commons.io.FilenameUtils
import java.nio.charset.StandardCharsets
import java.util.Optional
import org.airsonic.player.i18n.LocaleResolver
import org.airsonic.player.service.MediaFileService
import org.airsonic.player.service.NetworkService
import org.airsonic.player.security.ExternalJwtPrincipal
import org.airsonic.player.security.HlsLinkContext
import org.airsonic.player.security.AuthorizationDeniedException
import org.airsonic.player.service.PlayerService
import org.airsonic.player.service.SecurityService
import org.airsonic.player.service.ShareService
import org.airsonic.player.util.StringUtil
import org.apache.commons.lang.StringUtils
import java.security.Principal
class AirsonicKtorServlet(
    private val searchController: SearchController,
    private val searchPageController: SearchPageController,
    private val musicFoldersController: MusicFoldersController,
    private val genresController: GenresController,
    private val artistsController: ArtistsController,
    private val pingController: PingController,
    private val scanController: ScanController,
    private val similarSongsController: SimilarSongsController,
    private val artistInfoController: ArtistInfoController,
    private val albumInfoController: AlbumInfoController,
    private val playlistsController: PlaylistsController,
    private val uploadController: UploadController,
    private val playlistExportController: PlaylistExportController,
    private val playlistImportController: PlaylistImportController,
    private val randomQueueController: RandomQueueController,
    private val liveQueueExportController: LiveQueueExportController,
    private val sharesController: SharesController,
    private val albumsController: AlbumsController,
    private val bookmarksController: BookmarksController,
    private val starredController: StarredController,
    private val starredPageController: StarredPageController,
    private val jukeboxController: JukeboxController,
    private val usersController: UsersController,
    private val dlnaSettingsController: DLNASettingsController,
    private val databaseSettingsController: DatabaseSettingsController,
    private val credentialsManagementController: CredentialsManagementController,
    private val personalSettingsController: PersonalSettingsController,
    private val playerSettingsController: PlayerSettingsController,
    private val advancedSettingsController: AdvancedSettingsController,
    private val transcodingSettingsController: TranscodingSettingsController,
    private val generalSettingsController: GeneralSettingsController,
    private val musicFolderSettingsController: MusicFolderSettingsController,
    private val passwordController: PasswordController,
    private val playQueueController: PlayQueueController,
    private val indexesController: IndexesController,
    private val songsByGenreController: SongsByGenreController,
    private val topSongsController: TopSongsController,
    private val musicDirectoryController: MusicDirectoryController,
    private val randomSongsController: RandomSongsController,
    private val scrobbleController: ScrobbleController,
    private val nowPlayingController: NowPlayingController,
    private val nowPlayingPageController: NowPlayingPageController,
    private val rightPageController: RightPageController,
    private val indexPageController: IndexPageController,
    private val morePageController: MorePageController,
    private val leftPageController: LeftPageController,
    private val bookmarksPageController: BookmarksPageController,
    private val playlistPageController: PlaylistPageController,
    private val mainPageController: MainPageController,
    private val playlistsPageController: PlaylistsPageController,
    private val homePageController: HomePageController,
    private val changeCoverArtPageController: ChangeCoverArtPageController,
    private val uploadStatusController: UploadStatusController,
    private val statisticsController: StatisticsController,
    private val scheduledTasksController: ScheduledTasksController,
    private val pathWatchersController: PathWatchersController,
    private val serverHealthController: ServerHealthController,
    private val cacheStatisticsController: CacheStatisticsController,
    private val songController: SongController,
    private val videosController: VideosController,
    private val lyricsController: LyricsController,
    private val coverArtActionsController: CoverArtActionsController,
    private val mediaFileCommentController: MediaFileCommentController,
    private val tagEditController: TagEditController,
    private val tagEditorPageController: TagEditorPageController,
    private val websiteSettingsController: WebsiteSettingsController,
    private val liveQueueController: LiveQueueController,
    private val ratingController: RatingController,
    private val downloadRestController: DownloadRestController,
    private val downloads: DownloadService,
    private val shareService: ShareService,
    private val mediaFileService: MediaFileService,
    private val playerResolver: RestPlayerResolver,
    private val playerService: PlayerService,
    private val securityService: SecurityService,
    private val localeResolver: LocaleResolver,
    private val coverArtRestController: CoverArtRestController,
    private val avatarRestController: AvatarRestController,
    private val avatarUploadController: AvatarUploadController,
    private val hlsRestController: HlsRestController,
    private val hlsSegmentRestController: HlsSegmentRestController,
    private val streamRestController: StreamRestController,
    private val publicShareController: PublicShareController,
    private val captionsRestController: CaptionsRestController,
    private val videoPlayerRestController: VideoPlayerRestController,
    private val passwordRecoveryController: PasswordRecoveryController,
    private val serverInfoController: ServerInfoController,
    private val playQueuePageController: PlayQueuePageController,
    private val jaxbWriter: JAXBWriter,
    private val errorProperties: java.util.function.Supplier<InfrastructureErrorSettings>,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    metricsManager: MetricsManager,
    settingsService: org.airsonic.player.service.SettingsService,
) : ServletApplicationEngine() {
    private val proxyRestController = ProxyRestController()
    private val callerContextAdapter = ServletCallerContextAdapter(securityService)
    private val requestFilterPipeline = KtorServletFilterPipeline(metricsManager)
    private val externalSecurityPipeline = KtorExternalSecurityPipeline(
        settingsService,
    )

    @OptIn(InternalAPI::class)
    override fun init() {
        super.init()
        application.intercept(ApplicationCallPipeline.Setup) {
            val request = (call.request as ServletApplicationRequest).servletRequest
            val requestPath = request.requestURI.removePrefix(request.contextPath)
            val servletPath = request.servletPath
            val errorPath = errorProperties.get().path
            val isErrorRequest = requestPath == errorPath || servletPath == errorPath ||
                request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE) != null
            if (isErrorRequest) {
                call.attributes.put(INFRASTRUCTURE_REQUEST, infrastructureRequestSnapshot(request))
            }
            val searchRequest = request.getAttribute(SEARCH_REQUEST) as? SearchController.Request
            if (searchRequest != null) {
                call.attributes.put(SEARCH_REQUEST_KEY, searchRequest)
            }
            val searchPageRequest = request.getAttribute(SearchPageController.REQUEST_ATTRIBUTE) as? SearchPageController.Request
            if (searchPageRequest != null) call.attributes.put(SEARCH_PAGE_REQUEST_KEY, searchPageRequest)
            val nowPlayingPageRequest = request.getAttribute(NowPlayingPageController.REQUEST_ATTRIBUTE)
                as? NowPlayingPageController.Request
            if (nowPlayingPageRequest != null) call.attributes.put(NOW_PLAYING_PAGE_REQUEST_KEY, nowPlayingPageRequest)
            val rightPageRequest = request.getAttribute(RightPageController.REQUEST_ATTRIBUTE) as? RightPageController.Request
            if (rightPageRequest != null) call.attributes.put(RIGHT_PAGE_REQUEST_KEY, rightPageRequest)
            val indexPageRequest = request.getAttribute(IndexPageController.REQUEST_ATTRIBUTE) as? IndexPageController.Request
            if (indexPageRequest != null) call.attributes.put(INDEX_PAGE_REQUEST_KEY, indexPageRequest)
            val morePageRequest = request.getAttribute(MorePageController.REQUEST_ATTRIBUTE) as? MorePageController.Request
            if (morePageRequest != null) call.attributes.put(MORE_PAGE_REQUEST_KEY, morePageRequest)
            val leftPageRequest = request.getAttribute(LeftPageController.REQUEST_ATTRIBUTE) as? LeftPageController.Request
            if (leftPageRequest != null) call.attributes.put(LEFT_PAGE_REQUEST_KEY, leftPageRequest)
            val leftPageSelection = request.getAttribute(LeftPageController.SELECTION_ATTRIBUTE) as? LeftPageController.Selection
            if (leftPageSelection != null) call.attributes.put(LEFT_PAGE_SELECTION_KEY, leftPageSelection)
            val bookmarksPageRequest = request.getAttribute(BookmarksPageController.REQUEST_ATTRIBUTE) as? BookmarksPageController.Request
            if (bookmarksPageRequest != null) call.attributes.put(BOOKMARKS_PAGE_REQUEST_KEY, bookmarksPageRequest)
            val playlistPageRequest = request.getAttribute(PlaylistPageController.REQUEST_ATTRIBUTE) as? PlaylistPageController.Request
            if (playlistPageRequest != null) call.attributes.put(PLAYLIST_PAGE_REQUEST_KEY, playlistPageRequest)
            val mainPageRequest = request.getAttribute(MainPageController.REQUEST_ATTRIBUTE) as? MainPageController.Request
            if (mainPageRequest != null) call.attributes.put(MAIN_PAGE_REQUEST_KEY, mainPageRequest)
            val playlistsPageRequest = request.getAttribute(PlaylistsPageController.REQUEST_ATTRIBUTE) as? PlaylistsPageController.Request
            if (playlistsPageRequest != null) call.attributes.put(PLAYLISTS_PAGE_REQUEST_KEY, playlistsPageRequest)
            val homePageRequest = request.getAttribute(HomePageController.REQUEST_ATTRIBUTE) as? HomePageController.Request
            if (homePageRequest != null) call.attributes.put(HOME_PAGE_REQUEST_KEY, homePageRequest)
            val musicFoldersRequest = request.getAttribute(MUSIC_FOLDERS_REQUEST)
                as? MusicFoldersController.Request
            if (musicFoldersRequest != null) {
                call.attributes.put(MUSIC_FOLDERS_REQUEST_KEY, musicFoldersRequest)
            }
            val artistsRequest = request.getAttribute(ARTISTS_REQUEST)
                as? ArtistsRequest
            if (artistsRequest != null) {
                call.attributes.put(ARTISTS_REQUEST_KEY, artistsRequest)
            }
            val similarSongsRequest = request.getAttribute(SIMILAR_SONGS_REQUEST) as? SimilarSongsRequest
            if (similarSongsRequest != null) {
                call.attributes.put(SIMILAR_SONGS_REQUEST_KEY, similarSongsRequest)
            }
            val artistInfoRequest = request.getAttribute(ARTIST_INFO_REQUEST) as? ArtistInfoRequest
            if (artistInfoRequest != null) {
                call.attributes.put(ARTIST_INFO_REQUEST_KEY, artistInfoRequest)
            }
            val playlistsRequest = request.getAttribute(PLAYLISTS_REQUEST) as? PlaylistsRequest
            if (playlistsRequest != null) {
                call.attributes.put(PLAYLISTS_REQUEST_KEY, playlistsRequest)
            }
            val albumInfoRequest = request.getAttribute(ALBUM_INFO_REQUEST) as? AlbumInfoRequest
            if (albumInfoRequest != null) {
                call.attributes.put(ALBUM_INFO_REQUEST_KEY, albumInfoRequest)
            }
            val usersRequest = request.getAttribute(USERS_REQUEST) as? UsersRequest
            if (usersRequest != null) call.attributes.put(USERS_REQUEST_KEY, usersRequest)
            val playQueueRequest = request.getAttribute(PLAY_QUEUE_REQUEST) as? PlayQueueRequest
            if (playQueueRequest != null) call.attributes.put(PLAY_QUEUE_REQUEST_KEY, playQueueRequest)
            val scrobbleRequest = request.getAttribute(SCROBBLE_REQUEST) as? ScrobbleRequest
            if (scrobbleRequest != null) call.attributes.put(SCROBBLE_REQUEST_KEY, scrobbleRequest)
            val websiteDownload = request.getAttribute(WEBSITE_DOWNLOAD_REQUEST) as? DownloadRestController.WebsiteRequest
            if (websiteDownload != null) call.attributes.put(WEBSITE_DOWNLOAD_REQUEST_KEY, websiteDownload)
            val downloadRequest = request.getAttribute(DOWNLOAD_REQUEST) as? DownloadRequest
            if (downloadRequest != null) call.attributes.put(DOWNLOAD_REQUEST_KEY, downloadRequest)
            val randomSongsRequest = request.getAttribute(RANDOM_SONGS_REQUEST) as? RandomSongsRequest
            if (randomSongsRequest != null) call.attributes.put(RANDOM_SONGS_REQUEST_KEY, randomSongsRequest)
            val musicDirectoryRequest = request.getAttribute(MUSIC_DIRECTORY_REQUEST) as? MusicDirectoryRequest
            if (musicDirectoryRequest != null) call.attributes.put(MUSIC_DIRECTORY_REQUEST_KEY, musicDirectoryRequest)
            val songRequest = request.getAttribute(SONG_REQUEST) as? SongRequest
            if (songRequest != null) call.attributes.put(SONG_REQUEST_KEY, songRequest)
            val videosRequest = request.getAttribute(VIDEOS_REQUEST) as? VideosRequest
            if (videosRequest != null) call.attributes.put(VIDEOS_REQUEST_KEY, videosRequest)
            val ratingRequest = request.getAttribute(RATING_REQUEST) as? RatingRequest
            if (ratingRequest != null) call.attributes.put(RATING_REQUEST_KEY, ratingRequest)
            val topSongsRequest = request.getAttribute(TOP_SONGS_REQUEST) as? TopSongsRequest
            if (topSongsRequest != null) call.attributes.put(TOP_SONGS_REQUEST_KEY, topSongsRequest)
            val genreSongsRequest = request.getAttribute(GENRE_SONGS_REQUEST) as? SongsByGenreRequest
            if (genreSongsRequest != null) call.attributes.put(GENRE_SONGS_REQUEST_KEY, genreSongsRequest)
            val indexesRequest = request.getAttribute(INDEXES_REQUEST) as? IndexesRequest
            if (indexesRequest != null) call.attributes.put(INDEXES_REQUEST_KEY, indexesRequest)
            val jukeboxRequest = request.getAttribute(JUKEBOX_REQUEST) as? JukeboxRequest
            if (jukeboxRequest != null) {
                call.attributes.put(JUKEBOX_REQUEST_KEY, jukeboxRequest)
            }
            val starredRequest = request.getAttribute(STARRED_REQUEST) as? StarredRequest
            if (starredRequest != null) {
                call.attributes.put(STARRED_REQUEST_KEY, starredRequest)
            }
            val starredPageRequest = request.getAttribute(StarredPageController.REQUEST_ATTRIBUTE) as? StarredPageController.Request
            if (starredPageRequest != null) call.attributes.put(STARRED_PAGE_REQUEST_KEY, starredPageRequest)
            val bookmarksRequest = request.getAttribute(BOOKMARKS_REQUEST) as? BookmarksRequest
            if (bookmarksRequest != null) {
                call.attributes.put(BOOKMARKS_REQUEST_KEY, bookmarksRequest)
            }
            val albumsRequest = request.getAttribute(ALBUMS_REQUEST) as? AlbumsRequest
            if (albumsRequest != null) {
                call.attributes.put(ALBUMS_REQUEST_KEY, albumsRequest)
            }
            val sharesRequest = request.getAttribute(SHARES_REQUEST) as? SharesRequest
            if (sharesRequest != null) {
                call.attributes.put(SHARES_REQUEST_KEY, sharesRequest)
            }
            val queueExport = request.getAttribute(QUEUE_EXPORT_REQUEST) as? LiveQueueExportController.Request
            if (queueExport != null) call.attributes.put(QUEUE_EXPORT_REQUEST_KEY, queueExport)
            val randomQueue = request.getAttribute(RANDOM_QUEUE_REQUEST) as? RandomQueueController.Request
            if (randomQueue != null) call.attributes.put(RANDOM_QUEUE_REQUEST_KEY, randomQueue)
            val liveQueue = request.getAttribute(LIVE_QUEUE_REQUEST) as? LiveQueueController.Request
            if (liveQueue != null) call.attributes.put(LIVE_QUEUE_REQUEST_KEY, liveQueue)
            val uploadStatus = request.getAttribute(UPLOAD_STATUS_REQUEST) as? UploadStatusController.Request
            if (uploadStatus != null) call.attributes.put(UPLOAD_STATUS_REQUEST_KEY, uploadStatus)
            val statistics = request.getAttribute(STATISTICS_REQUEST) as? StatisticsController.Request
            if (statistics != null) call.attributes.put(STATISTICS_REQUEST_KEY, statistics)
            val websiteSettings = request.getAttribute(WEBSITE_SETTINGS_REQUEST) as? WebsiteSettingsController.Request
            if (websiteSettings != null) call.attributes.put(WEBSITE_SETTINGS_REQUEST_KEY, websiteSettings)
            val avatar = request.getAttribute(AVATAR_REQUEST) as? AvatarRestController.Request
            if (avatar != null) call.attributes.put(AVATAR_REQUEST_KEY, avatar)
            val tagEdit = request.getAttribute(TAG_EDIT_REQUEST) as? TagEditController.Request
            if (tagEdit != null) call.attributes.put(TAG_EDIT_REQUEST_KEY, tagEdit)
            val tagEditorPage = request.getAttribute(TagEditorPageController.REQUEST_ATTRIBUTE) as? TagEditorPageController.Request
            if (tagEditorPage != null) call.attributes.put(TAG_EDITOR_PAGE_REQUEST_KEY, tagEditorPage)
            val coverActions = request.getAttribute(COVER_ACTIONS_REQUEST) as? CoverArtActionsController.Request
            if (coverActions != null) call.attributes.put(COVER_ACTIONS_REQUEST_KEY, coverActions)
            val parameters = request.getAttribute(REST_PARAMETERS) as? Parameters
            if (parameters != null) {
                call.attributes.put(REST_PARAMETERS_KEY, parameters)
            }
            val hlsSegment = request.getAttribute(HLS_SEGMENT_REQUEST) as? HlsSegmentRestController.Request
            if (hlsSegment != null) call.attributes.put(HLS_SEGMENT_REQUEST_KEY, hlsSegment)
            (request.getAttribute(SERVER_INFO_REQUEST) as? ServerInfoController.Request)?.let {
                call.attributes.put(SERVER_INFO_REQUEST_KEY, it)
            }
            (request.getAttribute(PLAY_QUEUE_PAGE_REQUEST) as? PlayQueuePageController.Request)?.let {
                call.attributes.put(PLAY_QUEUE_PAGE_REQUEST_KEY, it)
            }
            (request.getAttribute(RECOVERY_REQUEST) as? PasswordRecoveryController.Request)?.let {
                call.attributes.put(RECOVERY_REQUEST_KEY, it)
            }
            (request.getAttribute(VIDEO_PLAYER_REQUEST) as? VideoPlayerRestController.Request)?.let {
                call.attributes.put(VIDEO_PLAYER_REQUEST_KEY, it)
            }
            (request.getAttribute(CAPTIONS_REQUEST) as? CaptionsRestController.Request)?.let {
                call.attributes.put(CAPTIONS_REQUEST_KEY, it)
            }
            (request.getAttribute(PUBLIC_SHARE_REQUEST) as? PublicShareController.Request)?.let {
                call.attributes.put(PUBLIC_SHARE_REQUEST_KEY, it)
            }
            val hlsRequest = request.getAttribute(HLS_REQUEST) as? HlsRestController.Request
            if (hlsRequest != null) call.attributes.put(HLS_REQUEST_KEY, hlsRequest)
            val stream = request.getAttribute(STREAM_REQUEST) as? StreamRestController.Request
            if (stream != null) call.attributes.put(STREAM_REQUEST_KEY, stream)
        }
        application.routing {
            infrastructureRoutes(errorProperties, objectMapper)
            proxyRestController.routes(this)
            for (path in listOf("/settings", "/settings.view")) {
                get(path) { redirectSettings(call) }
                head(path) { redirectSettings(call) }
            }
            searchController.routes(this) { call -> call.attributes[SEARCH_REQUEST_KEY] }
            musicFoldersController.routes(this) { call -> call.attributes[MUSIC_FOLDERS_REQUEST_KEY] }
            genresController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            artistsController.routes(this) { call -> call.attributes[ARTISTS_REQUEST_KEY] }
            pingController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            scanController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            similarSongsController.routes(this) { call -> call.attributes[SIMILAR_SONGS_REQUEST_KEY] }
            artistInfoController.routes(this) { call -> call.attributes[ARTIST_INFO_REQUEST_KEY] }
            albumInfoController.routes(this) { call -> call.attributes[ALBUM_INFO_REQUEST_KEY] }
            playlistsController.routes(this) { call -> call.attributes[PLAYLISTS_REQUEST_KEY] }
            uploadController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            mediaFileCommentController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            playlistExportController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            playlistImportController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            randomQueueController.routes(this) { call -> call.attributes[RANDOM_QUEUE_REQUEST_KEY] }
            sharesController.routes(this) { call -> call.attributes[SHARES_REQUEST_KEY] }
            albumsController.routes(this) { call -> call.attributes[ALBUMS_REQUEST_KEY] }
            bookmarksController.routes(this) { call -> call.attributes[BOOKMARKS_REQUEST_KEY] }
            starredController.routes(this) { call -> call.attributes[STARRED_REQUEST_KEY] }
            usersController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            avatarUploadController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            dlnaSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            databaseSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            credentialsManagementController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            personalSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            playerSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            advancedSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            transcodingSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            generalSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            musicFolderSettingsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            passwordController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            playQueueController.routes(this) { call -> call.attributes[PLAY_QUEUE_REQUEST_KEY] }
            indexesController.routes(this) { call -> call.attributes[INDEXES_REQUEST_KEY] }
            songsByGenreController.routes(this) { call -> call.attributes[GENRE_SONGS_REQUEST_KEY] }
            downloadRestController.routes(this, { call -> call.attributes[DOWNLOAD_REQUEST_KEY] },
                { call -> call.attributes[WEBSITE_DOWNLOAD_REQUEST_KEY] })
            uploadStatusController.routes(this) { call -> call.attributes[UPLOAD_STATUS_REQUEST_KEY] }
            statisticsController.routes(this) { call -> call.attributes[STATISTICS_REQUEST_KEY] }
            scheduledTasksController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            pathWatchersController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            serverHealthController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            cacheStatisticsController.routes(this) { call -> call.attributes[USERS_REQUEST_KEY] }
            nowPlayingController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            scrobbleController.routes(this) { call -> call.attributes[SCROBBLE_REQUEST_KEY] }
            randomSongsController.routes(this) { call -> call.attributes[RANDOM_SONGS_REQUEST_KEY] }
            musicDirectoryController.routes(this) { call -> call.attributes[MUSIC_DIRECTORY_REQUEST_KEY] }
            songController.routes(this) { call -> call.attributes[SONG_REQUEST_KEY] }
            videosController.routes(this) { call -> call.attributes[VIDEOS_REQUEST_KEY] }
            liveQueueExportController.routes(this) { call -> call.attributes[QUEUE_EXPORT_REQUEST_KEY] }
            liveQueueController.routes(this) { call -> call.attributes[LIVE_QUEUE_REQUEST_KEY] }
            websiteSettingsController.routes(this) { call -> call.attributes[WEBSITE_SETTINGS_REQUEST_KEY] }
            tagEditController.routes(this) { call -> call.attributes[TAG_EDIT_REQUEST_KEY] }
            coverArtRestController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            avatarRestController.routes(this) { call -> call.attributes[AVATAR_REQUEST_KEY] }
            coverArtActionsController.routes(this) { call -> call.attributes[COVER_ACTIONS_REQUEST_KEY] }
            lyricsController.routes(this) { call -> call.attributes[REST_PARAMETERS_KEY] }
            ratingController.routes(this) { call -> call.attributes[RATING_REQUEST_KEY] }
            topSongsController.routes(this) { call -> call.attributes[TOP_SONGS_REQUEST_KEY] }
            jukeboxController.routes(this) { call -> call.attributes[JUKEBOX_REQUEST_KEY] }
            serverInfoController.routes(this) { call -> call.attributes[SERVER_INFO_REQUEST_KEY] }
            passwordRecoveryController.routes(this) { call -> call.attributes[RECOVERY_REQUEST_KEY] }
            videoPlayerRestController.routes(this) { call -> call.attributes[VIDEO_PLAYER_REQUEST_KEY] }
            captionsRestController.routes(this) { call -> call.attributes[CAPTIONS_REQUEST_KEY] }
            publicShareController.routes(this) { call -> call.attributes[PUBLIC_SHARE_REQUEST_KEY] }
            hlsRestController.routes(this) { call -> call.attributes[HLS_REQUEST_KEY] }
            hlsSegmentRestController.routes(this) { call -> call.attributes[HLS_SEGMENT_REQUEST_KEY] }
            streamRestController.routes(this) { call -> call.attributes[STREAM_REQUEST_KEY] }
        }
        // The servlet engine omits BaseApplicationEngine's default fallback in Ktor 3.6.0.
        // Keep this compatibility code in the temporary servlet adapter.
        application.intercept(ApplicationCallPipeline.Fallback) {
            if (!call.isHandled) {
                call.respond(
                    call.response.status()
                        ?: call.attributes.getOrNull(RoutingFailureStatusCode)
                        ?: HttpStatusCode.NotFound
                )
            }
        }
    }

    private suspend fun redirectSettings(call: ApplicationCall) {
        val user = call.attributes[USERS_REQUEST_KEY].user
        call.response.headers.append("Cache-Control", "no-store")
        if (!user.isAdminRole && !user.isSettingsRole) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }
        // Relative locations preserve the servlet context, including deployments below '/'.
        call.respondRedirect(if (user.isAdminRole) "musicFolderSettings" else "personalSettings")
    }

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        externalSecurityPipeline.service(request, response, FilterChain { securedRequest, securedResponse ->
            requestFilterPipeline.service(securedRequest as HttpServletRequest, securedResponse as HttpServletResponse, FilterChain { filteredRequest, filteredResponse ->
                serviceRequest(filteredRequest as HttpServletRequest, filteredResponse as HttpServletResponse)
            })
        })
    }

    private fun hlsLinkContext(principal: Principal): HlsLinkContext {
        val jwt = principal as? ExternalJwtPrincipal
        return HlsLinkContext(principal.name, jwt != null, jwt?.expiresAt)
    }

    private fun serviceRequest(request: HttpServletRequest, response: HttpServletResponse) {
        val path = request.servletPath
        if (isRemovedWebsitePage(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        val errorDispatch = path == errorProperties.get().path &&
            (request.dispatcherType == jakarta.servlet.DispatcherType.ERROR ||
                request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE) != null)
        if (errorDispatch && request.isAsyncSupported) {
            // Ktor's async servlet service returns before Tomcat finishes an ERROR dispatch response.
            // Force Ktor's built-in blocking service branch for this dispatch only.
            val synchronousErrorRequest = object : HttpServletRequestWrapper(request) {
                override fun isAsyncSupported(): Boolean = false
            }
            super.service(synchronousErrorRequest, response)
            return
        }
        if (path in IndexPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (securityService.getCurrentUsername(request) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            val user = requireNotNull(securityService.getCurrentUser(request))
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path in RightPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (securityService.getCurrentUsername(request) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path in StarredPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (securityService.getCurrentUsername(request) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path in TagEditorPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (securityService.getCurrentUsername(request) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == "/changeCoverArt" || path == "/changeCoverArt.view") {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            val id = request.getParameter("id")?.toIntOrNull() ?: run { response.sendError(400); return }
            val model = try {
                changeCoverArtPageController.page(username, id, request.getParameter("artist"), request.getParameter("album"))
            } catch (_: NoSuchElementException) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            } catch (_: SecurityException) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            request.setAttribute("model", model)
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == "/lyrics" || path == "/lyrics.view") {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (securityService.getCurrentUsername(request) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == BookmarksPageController.DATA_PATH || path in BookmarksPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            request.setAttribute(BookmarksPageController.REQUEST_ATTRIBUTE, BookmarksPageController.Request(username))
            if (path in BookmarksPageController.PAGE_PATHS) {
                response.setHeader("Cache-Control", "no-store")
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
        }
        if (path == PlaylistPageController.DATA_PATH || path in PlaylistPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            val rawId = request.getParameter("id") ?: run { response.sendError(400); return }
            val playlistId = rawId.toIntOrNull() ?: run { response.sendError(400); return }
            request.setAttribute(PlaylistPageController.REQUEST_ATTRIBUTE, PlaylistPageController.Request(username, playlistId))
            if (path in PlaylistPageController.PAGE_PATHS) {
                response.setHeader("Cache-Control", "no-store")
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
        }
        if (path == MainPageController.DATA_PATH || path in MainPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            val paths = org.springframework.web.bind.ServletRequestUtils.getStringParameters(request, "path")
                .map { StringUtils.split(it, ":", 2) }
                .groupBy { it[0] }
                .mapValues { (_, values) -> values.map { it.getOrNull(1) ?: "" }.toSet() }
            val ids = org.springframework.web.bind.ServletRequestUtils.getIntParameters(request, "id").toList()
            request.setAttribute(MainPageController.REQUEST_ATTRIBUTE, MainPageController.Request(username, ids, paths))
            if (path in MainPageController.PAGE_PATHS) {
                response.setHeader("Cache-Control", "no-store")
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
        }
        if (path == PlaylistsPageController.DATA_PATH || path in PlaylistsPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            request.setAttribute(PlaylistsPageController.REQUEST_ATTRIBUTE, PlaylistsPageController.Request(username))
            if (path in PlaylistsPageController.PAGE_PATHS) {
                response.setHeader("Cache-Control", "no-store")
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
        }
        if (path == LeftPageController.DATA_PATH || path == LeftPageController.PAGE_PATH || path == "/left") {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            val player = playerService.getPlayer(request, response)
            val requestData = LeftPageController.Request(user, player, localeResolver.resolveLocale(request), request.getParameter("refresh") == "true")
            request.setAttribute(LeftPageController.REQUEST_ATTRIBUTE, requestData)
            if (path != LeftPageController.DATA_PATH) {
                response.setHeader("Cache-Control", "no-store")
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
        }
        if (path == LeftPageController.SELECT_PATH) {
            if (request.method != "POST") {
                response.setHeader("Allow", "POST")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            val rawId = request.getParameter("musicFolderId")
            val folderId = if (rawId == null) null else rawId.toIntOrNull() ?: run { response.sendError(400); return }
            request.setAttribute(LeftPageController.SELECTION_ATTRIBUTE, LeftPageController.Selection(username, folderId))
        }
        if (path == MorePageController.PATH) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            val player = playerService.getPlayer(request, response)
            request.setAttribute(MorePageController.REQUEST_ATTRIBUTE,
                MorePageController.Request(user, player.isExternalWithPlaylist || player.isWeb))
        }
        if (path in MorePageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            val player = playerService.getPlayer(request, response)
            request.setAttribute(MorePageController.REQUEST_ATTRIBUTE,
                MorePageController.Request(user, player.isExternalWithPlaylist || player.isWeb))
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path in NowPlayingPageController.PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            request.setAttribute(NowPlayingPageController.REQUEST_ATTRIBUTE, nowPlayingPageController.request(request, response))
        }
        if (path in PlayQueuePageController.PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            val player = if (path in PlayQueuePageController.PLAYER_PATHS) {
                val rawId = request.getParameter("player")
                if (rawId != null) {
                    val id = rawId.toIntOrNull()
                    if (id == null) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                        return
                    }
                    val selected = playerService.getPlayerById(id)
                    if (selected == null || selected.username != user.username) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN)
                        return
                    }
                }
                playerService.getPlayer(request, response)
            } else null
            request.setAttribute(PLAY_QUEUE_PAGE_REQUEST, PlayQueuePageController.Request(user, player))
        }
        if (path in setOf("/playQueue", "/playQueue.view")) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path in ServerInfoController.PATHS || path == ServerInfoController.GETTING_STARTED_PATH) {
            val allowed = if (path == ServerInfoController.GETTING_STARTED_PATH) setOf("GET", "HEAD", "POST") else setOf("GET", "HEAD")
            if (request.method !in allowed) {
                response.setHeader("Allow", allowed.joinToString(", "))
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            request.setAttribute(SERVER_INFO_REQUEST, ServerInfoController.Request(request.servletContext.serverInfo, user.isAdminRole))
        }
        if (path == PasswordRecoveryController.PATH) {
            if (request.method !in setOf("GET", "HEAD", "POST")) {
                response.setHeader("Allow", "GET, HEAD, POST")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            request.characterEncoding = RequestEncodingPolicy.DEFAULT_ENCODING
            val csrf = request.getAttribute(org.springframework.security.web.csrf.CsrfToken::class.java.name)
                as? org.springframework.security.web.csrf.CsrfToken
            request.setAttribute(RECOVERY_REQUEST, PasswordRecoveryController.Request(
                request.getParameter("usernameOrEmail"), request.getParameter("g-recaptcha-response"),
                csrf?.headerName, csrf?.token))
        }
        if (path in setOf("/recover", "/recover.view")) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == VideoPlayerRestController.PATH) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val user = securityService.getCurrentUser(request)
            if (!user.isStreamRole) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val id = request.getParameter("id")?.toIntOrNull()
            val rawPosition = request.getParameter("position")
            val position = rawPosition?.toLongOrNull()
            val rawPlayer = request.getParameter("player")
            if (id == null || (rawPosition != null && (position == null || position < 0)) ||
                (rawPlayer != null && rawPlayer.toIntOrNull() == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val file = mediaFileService.getMediaFile(id)
            if (file == null || !file.isVideo) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            if (!securityService.isFolderAccessAllowed(file, callerContextAdapter.fromRequest(request))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            if (rawPlayer != null) {
                val explicit = playerService.getPlayerById(rawPlayer.toInt())
                if (explicit == null || explicit.username != user.username) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
            }
            val player = playerService.getPlayer(request, response)
            val csrf = request.getAttribute(org.springframework.security.web.csrf.CsrfToken::class.java.name)
                as? org.springframework.security.web.csrf.CsrfToken
            request.setAttribute(VIDEO_PLAYER_REQUEST, VideoPlayerRestController.Request(file, user, player.id,
                NetworkService.getBaseUrl(request), position, csrf?.headerName, csrf?.token))
        }
        if (path in CaptionsRestController.PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val auth = request.userPrincipal as Principal
            val id = request.getParameter("id")?.toIntOrNull()
            if (id == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val file = mediaFileService.getMediaFile(id)
            if (file == null || file.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            val jwt = auth as? ExternalJwtPrincipal
            val signed = jwt != null
            if (jwt != null) {
                val expected = jwt.signedPath
                val valid = try {
                    val params = parseQueryParams(expected)
                    // A public link grants access to this video's subtitles only.
                    params["id"]?.firstOrNull() == request.getParameter("id")
                } catch (_: IllegalArgumentException) { false }
                if (!valid) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
            }
            if (!signed && !securityService.isFolderAccessAllowed(file, callerContextAdapter.fromRequest(request))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            request.setAttribute(CAPTIONS_REQUEST, CaptionsRestController.Request(file,
                org.airsonic.player.service.NetworkService.getBaseUrl(request), if (signed) auth.name else null,
                jwt?.expiresAt,
                request.getParameter("captionId"), request.getParameter("format")))
        }
        if (path == PublicShareController.PAGE_PATH || path == PublicShareController.DATA_PATH) {
            response.setHeader("Cache-Control", "no-store")
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val name = request.pathInfo?.removePrefix("/") ?: ""
            if (!name.matches(Regex("[a-zA-Z]+"))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            val auth = request.userPrincipal as? ExternalJwtPrincipal
            if (auth == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val expiration = auth.expiresAt
            val publicRequest = PublicShareController.Request(name, playerService.getGuestPlayer(request),
                NetworkService.getBaseUrl(request), expiration)
            if (path == PublicShareController.PAGE_PATH) {
                request.setAttribute("publicSharePreview", publicShareController.preview(publicRequest))
                request.setAttribute("publicShareDataUrl", publicShareController.dataUrl(name,
                    NetworkService.getBaseUrl(request), expiration))
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
                return
            }
            request.setAttribute(PUBLIC_SHARE_REQUEST, publicRequest)
        }
        if (path in setOf("/userSettings", "/userSettings.view", "/musicFolderSettings", "/musicFolderSettings.view", "/generalSettings", "/generalSettings.view", "/transcodingSettings", "/transcodingSettings.view", "/advancedSettings", "/advancedSettings.view", "/playerSettings", "/playerSettings.view", "/personalSettings", "/personalSettings.view", "/shareSettings", "/shareSettings.view", "/credentialsSettings", "/credentialsSettings.view", "/databaseSettings", "/databaseSettings.view", "/dlnaSettings", "/dlnaSettings.view", "/top", "/top.view", "/status", "/status.view", "/importPlaylist", "/importPlaylist.view", "/createShare", "/createShare.view", "/videoPlayer", "/videoPlayer.view", "/help", "/help.view", "/gettingStarted", "/gettingStarted.view")) {
            val user = securityService.getCurrentUser(request)
            val personalPage = path in setOf("/playerSettings", "/playerSettings.view", "/personalSettings", "/personalSettings.view", "/shareSettings", "/shareSettings.view", "/credentialsSettings", "/credentialsSettings.view")
            if (path !in setOf("/top", "/top.view", "/status", "/status.view", "/importPlaylist", "/importPlaylist.view", "/createShare", "/createShare.view", "/videoPlayer", "/videoPlayer.view", "/help", "/help.view", "/gettingStarted", "/gettingStarted.view") && !user.isAdminRole && !(personalPage && user.isSettingsRole)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            if (request.method != "GET" && request.method != "HEAD") {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            if (path in setOf("/createShare", "/createShare.view", "/videoPlayer", "/videoPlayer.view", "/help", "/help.view", "/gettingStarted", "/gettingStarted.view")) response.setHeader("Cache-Control", "no-store")
            // JSP tags still need the view context while the page loads its data from Ktor REST.
            request.setAttribute("playerSettingsAdmin", user.isAdminRole)
            request.setAttribute("personalSettingsUsername", user.username)
            val page = path.removePrefix("/").removeSuffix(".view")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path.startsWith("/ext/rest-hls/")) {
            val auth = request.userPrincipal as? ExternalJwtPrincipal
            val expected = auth?.signedPath
            val actual = request.requestURI.substring(request.contextPath.length + 1) + "?" + request.queryString
            val valid = try {
                val signed = parseUriComponents(expected)
                val received = parseUriComponents(actual).withoutQueryParam("jwt")
                signed.path == received.path && signed.queryParams == received.queryParams
            } catch (_: IllegalArgumentException) { false }
            if (auth == null || !valid) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
        }
        if (path in HomePageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            if (homePageController.shouldRedirectToGettingStarted(username)) {
                response.sendRedirect("gettingStarted.view")
                return
            }
            request.setAttribute(HomePageController.REQUEST_ATTRIBUTE, HomePageController.Request(username))
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == HomePageController.DATA_PATH) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            request.setAttribute(HomePageController.REQUEST_ATTRIBUTE, HomePageController.Request(username))
        }
        if (path in SearchPageController.PAGE_PATHS) {
            if (request.method !in setOf("GET", "HEAD", "POST")) {
                response.setHeader("Allow", "GET, HEAD, POST")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            val initialQuery = if (request.method == "POST") request.getParameter("query").orEmpty() else ""
            val player = playerService.getPlayer(request, response)
            request.setAttribute(SearchPageController.REQUEST_ATTRIBUTE, SearchPageController.Request(username, player))
            request.setAttribute(SearchPageController.INITIAL_QUERY_ATTRIBUTE, initialQuery)
            response.setHeader("Cache-Control", "no-store")
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        if (path == SearchPageController.DATA_PATH) {
            if (request.method !in setOf("GET", "HEAD")) {
                response.setHeader("Allow", "GET, HEAD")
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                return
            }
            val username = securityService.getCurrentUsername(request) ?: run { response.sendError(401); return }
            request.setAttribute(SearchPageController.REQUEST_ATTRIBUTE,
                SearchPageController.Request(username, playerService.getPlayer(request, response)))
        }
        if (path in SearchController.PATHS) {
            // Capture Spring's thread-bound authentication before Ktor dispatches to a coroutine.
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val playerRequest = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(playerRequest, response)
            request.setAttribute(SEARCH_REQUEST, SearchController.Request(username, player, parameters(request)))
        } else if (path in MusicFoldersController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(
                MUSIC_FOLDERS_REQUEST,
                MusicFoldersController.Request(username, parameters(request))
            )
        } else if (path in GenresController.PATHS) {
            // Preserve the REST player creation previously performed by wrapRequest().
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ArtistsController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val rawId = if (path.substringAfterLast('/').removeSuffix(".view") == "getArtist") mapRestId(request.getParameter("id")) else null
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ARTISTS_REQUEST, ArtistsRequest(username, parameters(request), id))
        } else if (path in PingController.PATHS) {
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ScanController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in SimilarSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val id = mapRestId(request.getParameter("id"))
            val parsedId = if (id == null) null else try {
                Integer.parseInt(id)
            } catch (_: NumberFormatException) {
                // Spring MVC previously sent a container error for a malformed required integer.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerRequest = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = if (parsedId == null) null else playerService.getPlayer(playerRequest, response)
            request.setAttribute(SIMILAR_SONGS_REQUEST, SimilarSongsRequest(username, parameters(request), parsedId, player))
        } else if (path in ArtistInfoController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val id = mapRestId(request.getParameter("id"))
            val parsedId = if (id == null) null else try {
                Integer.parseInt(id)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ARTIST_INFO_REQUEST, ArtistInfoRequest(username, callerContextAdapter.fromRequest(request),
                parameters(request), parsedId, localeResolver.resolveLocale(username)))
        } else if (path in AlbumInfoController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val rawId = mapRestId(request.getParameter("id"))
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ALBUM_INFO_REQUEST, AlbumInfoRequest(parameters(request), id))
        } else if (path == "/settings" || path == "/settings.view") {
            request.setAttribute(USERS_REQUEST, UsersRequest(requireNotNull(securityService.getCurrentUser(request)), callerContextAdapter.fromRequest(request), Parameters.Empty))
        } else if (path == UploadController.PATH || path == PlaylistImportController.PATH || path == AvatarUploadController.PATH || path == DatabaseSettingsController.IMPORT_PATH) {
            // Multipart bodies belong to Ktor; capture only authentication and query parameters.
            request.setAttribute(USERS_REQUEST, UsersRequest(requireNotNull(securityService.getCurrentUser(request)), callerContextAdapter.fromRequest(request),
                parseQueryString(request.queryString.orEmpty())).apply {
                if (path == UploadController.PATH && (user.isAdminRole || user.isUploadRole))
                    transferPlayer = playerService.getPlayer(request, response, false, false)
            })
        } else if (path == MediaFileCommentController.PATH || path == PlaylistExportController.PATH || path in CacheStatisticsController.PATHS || path == ServerHealthController.PATH || path == PathWatchersController.PATH || path == ScheduledTasksController.PATH || path in UsersController.PATHS || path in UsersController.WEBSITE_PATHS || path == UsersController.WEBSITE_READ_PATH ||
            path in DatabaseSettingsController.PATHS || path in PlayerSettingsController.PATHS || path in CredentialsManagementController.PATHS ||
            path == PersonalSettingsController.READ_PATH || path == PersonalSettingsController.SAVE_PATH ||
            path == DLNASettingsController.READ_PATH || path == DLNASettingsController.SAVE_PATH ||
            path == AdvancedSettingsController.READ_PATH || path == AdvancedSettingsController.SAVE_PATH ||
            path == TranscodingSettingsController.READ_PATH || path == TranscodingSettingsController.SAVE_PATH ||
            path == GeneralSettingsController.READ_PATH || path in GeneralSettingsController.WRITE_PATHS ||
            path == MusicFolderSettingsController.READ_PATH || path in MusicFolderSettingsController.WRITE_PATHS || path in PasswordController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(USERS_REQUEST, UsersRequest(requireNotNull(securityService.getCurrentUser(request)), callerContextAdapter.fromRequest(request), parameters(request)).apply {
                if (path == DatabaseSettingsController.EXPORT_PATH && user.isAdminRole) transferPlayer = playerService.getPlayer(request, response, false, false)
            })
        } else if (path in JukeboxController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, true
            )
            val user = requireNotNull(securityService.getCurrentUser(request))
            val player = if (user.isJukeboxRole) {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            request.setAttribute(JUKEBOX_REQUEST, JukeboxRequest(user.username, user.isJukeboxRole, player, parameters(request)))
        } else if (path in StarredController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val listing = operation == "getStarred" || operation == "getStarred2"
            val player = if (listing) {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = if (listing) requireNotNull(securityService.getCurrentUsername(request))
                else requireNotNull(securityService.getCurrentUser(request)).username
            request.setAttribute(STARRED_REQUEST, StarredRequest(username, parameters(request), player))
        } else if (path == StarredPageController.DATA_PATH) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(StarredPageController.REQUEST_ATTRIBUTE,
                StarredPageController.Request(username, playerService.getPlayer(request, response)))
        } else if (path == DownloadRestController.WEBSITE_PATH && request.method in setOf("GET", "HEAD")) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            val explicit = request.getParameter("player")
            val player = if (!user.isDownloadRole) null
                else if (explicit != null) explicit.toIntOrNull()?.let { playerService.getPlayerById(it) }
                else playerService.getPlayer(request, response, false, false)
            val callerContext = callerContextAdapter.fromRequest(request)
            val webRequest = ServletWebRequest(request, response)
            request.setAttribute(WEBSITE_DOWNLOAD_REQUEST, DownloadRestController.WebsiteRequest(
                user, callerContext, player?.takeIf { it.username == user.username }, parameters(request),
                java.util.function.BiPredicate { tag, modified -> webRequest.checkNotModified(tag, modified) },
                { response.status }))
        } else if (path in DownloadRestController.PATHS) {
            val params = parameters(request)
            val rawPlaylist = params["playlist"]
            val rawPlayer = params["player"]
            val playlist = rawPlaylist?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val suppliedPlayer = rawPlayer?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val rawIndices = params.getAll("i")?.flatMap { it.split(',') }
            val indices = rawIndices?.map { it.trim().toIntOrNull() }
            if ((rawPlaylist != null && rawPlaylist.isNotEmpty() && playlist == null) ||
                (rawPlayer != null && rawPlayer.isNotEmpty() && suppliedPlayer == null) ||
                (indices != null && indices.any { it == null })) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            }
            val id = mapRestId(params["id"])?.takeIf { StringUtils.isNumeric(it) }?.let { Integer.valueOf(it) }
            val user = requireNotNull(securityService.getUserByName(request.userPrincipal.name))
            val callerContext = callerContextAdapter.fromRequest(request)
            if (!user.isDownloadRole) {
                request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, null, ErrorCode.NOT_AUTHORIZED,
                    "${user.username} is not authorized to download files."))
            } else {
                try {
                    val player = playerService.getPlayer(wrapped, response, false, false)
                    val download = downloads.prepare(user, callerContext, player, Optional.ofNullable(id), playlist,
                        playerId, indices?.filterNotNull(), null)
                    if (download != null && ServletWebRequest(wrapped, response).checkNotModified(download.size.toString(), download.changed)) return
                    val disposition = download?.let { ContentDisposition.builder("attachment").filename(it.filename, StandardCharsets.UTF_8).build().toString() }
                    val type = download?.let { StringUtil.getMimeType(FilenameUtils.getExtension(it.filename)) }
                    request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, download, contentDisposition = disposition, contentType = type))
                } catch (e: AuthorizationDeniedException) {
                    if (path.endsWith(".view")) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN)
                        return
                    }
                    request.setAttribute(DOWNLOAD_REQUEST, DownloadRequest(params, null, ErrorCode.GENERIC, "Request processing failed: $e"))
                }
            }
        } else if (path in NowPlayingController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in ScrobbleController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            request.setAttribute(SCROBBLE_REQUEST, ScrobbleRequest(player, parameters(request)))
        } else if (path in RandomSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(RANDOM_SONGS_REQUEST, RandomSongsRequest(username, player, parameters(request)))
        } else if (path in MusicDirectoryController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
             request.setAttribute(
                 MUSIC_DIRECTORY_REQUEST,
                 MusicDirectoryRequest(username, callerContextAdapter.fromRequest(request), player, parameters(request)),
             )
        } else if (path in SongController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(SONG_REQUEST, SongRequest(username, callerContextAdapter.fromRequest(request), player, parametersWithMappedId(request)))
        } else if (path in VideosController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(VIDEOS_REQUEST, VideosRequest(username, player, parameters(request)))
        } else if (path in LyricsController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parameters(request))
        } else if (path in RatingController.PATHS) {
            // The legacy wrapRequest() created a REST player for this endpoint too.
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(RATING_REQUEST, RatingRequest(username, parametersWithMappedId(request)))
        } else if (path in TopSongsController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = if (request.getParameter("artist") == null) null else playerService.getPlayer(wrapped, response)
            request.setAttribute(TOP_SONGS_REQUEST, TopSongsRequest(username, parameters(request), player))
        } else if (path in SongsByGenreController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "player") playerId?.toString() else super.getParameter(name)
            }
            val player = playerService.getPlayer(wrapped, response)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(GENRE_SONGS_REQUEST, SongsByGenreRequest(username, player, parameters(request)))
        } else if (path in IndexesController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUser(request)).username
            val rawFolderId = request.getParameter("musicFolderId")
            val folderId = if (rawFolderId == null) null else rawFolderId.toIntOrNull() ?: run {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(INDEXES_REQUEST, IndexesRequest(username, parameters(request), folderId, playerId))
        } else if (path in PlayQueueController.PATHS) {
            val playerId = playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val player = if (path.substringAfterLast('/').removeSuffix(".view") == "getPlayQueue") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            request.setAttribute(PLAY_QUEUE_REQUEST, PlayQueueRequest(username, parameters(request), player))
        } else if (path in BookmarksController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getBookmarks") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val rawId = if (operation == "getBookmarks") null else mapRestId(request.getParameter("id"))
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(BOOKMARKS_REQUEST, BookmarksRequest(username, parameters(request), id, player))
        } else if (path in AlbumsController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getAlbum" || operation == "getAlbumList") {
                val wrapped = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrapped, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val rawId = if (operation == "getAlbum") mapRestId(request.getParameter("id")) else null
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(ALBUMS_REQUEST, AlbumsRequest(username, parameters(request), id, player))
        } else if (path in SharesController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false
            )
            val player = if (operation == "getShares" || operation == "createShare" || operation == "createWebShare") {
                val wrappedRequest = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrappedRequest, response)
            } else null
            val user = requireNotNull(securityService.getCurrentUser(request))
            val id = if (operation == "updateShare" || operation == "deleteShare")
                mapRestId(request.getParameter("id")) else null
            request.setAttribute(SHARES_REQUEST, SharesRequest(parameters(request), user, callerContextAdapter.fromRequest(request), player, id,
                { share -> shareService.getShareUrl(request, share) }))
        } else if (path in PlaylistsController.PATHS) {
            val operation = path.substringAfterLast('/').removeSuffix(".view")
            val jukebox = operation == "createPlaylist" || operation == "updatePlaylist" || operation == "deletePlaylist"
            val playerId = playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, jukebox
            )
            val player = if (operation == "getPlaylist") {
                val wrappedRequest = object : HttpServletRequestWrapper(request) {
                    override fun getParameter(name: String): String? =
                        if (name == "player") playerId?.toString() else super.getParameter(name)
                }
                playerService.getPlayer(wrappedRequest, response)
            } else null
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val admin = operation == "getPlaylists" && requireNotNull(securityService.getCurrentUser(request)).isAdminRole
            val rawId = when (operation) {
                "getPlaylist", "deletePlaylist" -> mapRestId(request.getParameter("id"))
                "createPlaylist", "updatePlaylist" -> request.getParameter("playlistId")
                else -> null
            }
            val id = if (rawId == null) null else try {
                Integer.parseInt(rawId)
            } catch (_: NumberFormatException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            request.setAttribute(PLAYLISTS_REQUEST, PlaylistsRequest(username, callerContextAdapter.fromRequest(request), admin, parameters(request), id, player))
        } else if ((path in StreamRestController.PATHS || path in StreamRestController.WEBSITE_PATHS)
            && (request.method in setOf("GET", "HEAD") || (path in StreamRestController.PATHS && request.method == "POST"))) {
            val restStream = path in StreamRestController.PATHS
            val rawPlayer = if (restStream) null else request.getParameter("player")?.trim()?.takeIf { it.isNotEmpty() }
            if (rawPlayer != null && rawPlayer.toIntOrNull() == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val rawId = request.getParameter("id")?.trim()?.takeIf { it.isNotEmpty() }
            val rawBitRate = request.getParameter("maxBitRate")?.trim()?.takeIf { it.isNotEmpty() }
            val rawOffset = request.getParameter("offsetSeconds")?.trim()?.takeIf { it.isNotEmpty() }
            val id = rawId?.toIntOrNull()
            val bitRate = rawBitRate?.toIntOrNull()
            val offset = rawOffset?.toDoubleOrNull()
            if ((rawId != null && id == null) || (rawBitRate != null && bitRate == null) || (rawOffset != null && offset == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = if (restStream) playerResolver.getOrCreatePlayerId(
                request.remoteUser, request.getParameter("c"), request.remoteAddr, false) else null
            val wrapped = if (restStream) object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            } else request
            val authentication = request.userPrincipal as Principal
            val restUser = if (restStream) requireNotNull(securityService.getUserByName(authentication.name)) else null
            if (restUser != null && !restUser.isStreamRole) {
                jaxbWriter.writeErrorResponse(wrapped, response, ErrorCode.NOT_AUTHORIZED,
                    "${restUser.username} is not authorized to play files.")
                return
            }
            val player = playerService.getPlayer(wrapped, response, false, true)
            val user = restUser ?: requireNotNull(securityService.getUserByName(player.username))
            val jwt = authentication as? ExternalJwtPrincipal
            if (!restStream && jwt == null && !user.isStreamRole) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val file = request.getParameter("path")?.let { mediaFileService.getMediaFile(it) }
                ?: id?.let { mediaFileService.getMediaFile(it) }
             val callerContext = callerContextAdapter.fromRequest(request)
             if (file != null && (restStream || jwt == null)
                 && !securityService.isFolderAccessAllowed(file, callerContext)) {
                if (!restStream || path.endsWith(".view")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
                throw AuthorizationDeniedException("Access to file ${file.id} is forbidden for user ${user.username}")
            }
            val webRequest = ServletWebRequest(wrapped, response)
            request.setAttribute(STREAM_REQUEST, StreamRestController.Request(jwt != null, callerContext, player, user,
                request.getParameter("format"), request.getParameter("suffix"), bitRate, id, request.getParameter("path"), offset,
                MediaStreamService.VideoOptions(request.getParameter("maxBitRate"), request.getParameter("timeOffset"),
                    request.getParameter("duration"), request.getParameter("size")), request.getHeader("icy-metadata"),
                java.util.function.BiPredicate { tag, modified -> webRequest.checkNotModified(tag, modified) },
                { response.status }))
        } else if ((path in HlsRestController.WEBSITE_PATHS || path == "/hls/segment" || path == "/ext/hls/segment")
            && request.method in setOf("GET", "HEAD")) {
            val auth = request.userPrincipal as Principal
            val rawPlayer = request.getParameter("player")
            val id = request.getParameter("id")?.toIntOrNull()
            if (id == null || (rawPlayer != null && rawPlayer.toIntOrNull() == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val media = mediaFileService.getMediaFile(id)
            if (media == null || media.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            val player = playerService.getPlayer(request, response)
            val user = requireNotNull(securityService.getUserByName(auth.name))
            val jwt = auth as? ExternalJwtPrincipal
            val signed = jwt != null
            val callerContext = callerContextAdapter.fromRequest(request)
            if (!signed && !securityService.isFolderAccessAllowed(media, callerContext)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            if (path in HlsRestController.WEBSITE_PATHS) {
                val duration = media.duration
                if (duration == null || duration < 0.0001) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    return
                }
                request.setAttribute(HLS_REQUEST, HlsRestController.Request(hlsLinkContext(auth), media, player,
                    NetworkService.getBaseUrl(request), path.removePrefix("/").removeSuffix("/hls.m3u8"),
                    request.getParameterValues("maxBitRate"), request.getParameter("size"), false))
            } else {
                val index = request.getParameter("segmentIndex")?.toIntOrNull()
                val bitRate = request.getParameter("maxBitRate")?.toIntOrNull()
                val size = request.getParameter("size")
                val rawDuration = request.getParameter("duration")
                val duration = rawDuration?.toIntOrNull() ?: 10
                val rawAudio = request.getParameter("audioTrack")
                val audioTrack = rawAudio?.toIntOrNull()
                if (rawPlayer == null || index == null || index < 0 || bitRate == null || bitRate <= 0 ||
                    size == null || !size.matches(Regex("[1-9][0-9]*x[1-9][0-9]*")) || duration <= 0 ||
                    (rawDuration != null && rawDuration.toIntOrNull() == null) ||
                    (rawAudio != null && audioTrack == null)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                    return
                }
                request.setAttribute(HLS_SEGMENT_REQUEST, HlsSegmentRestController.Request(jwt != null, user, callerContext, player,
                    id, rawPlayer, index, bitRate, size, duration, audioTrack, false))
            }
        } else if (path in HlsRestController.PATHS && request.method in setOf("GET", "POST", "HEAD")) {
            val rawId = request.getParameter("id")
            if (rawId == null || rawId.isEmpty()) {
                jaxbWriter.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER,
                    "Required param (id) is missing")
                return
            }
            val id = rawId.toIntOrNull()
            if (id == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val playerId = if (path.startsWith("/ext/")) request.getParameter("player")?.toIntOrNull() else
                playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val wrapped = object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? = when (name) {
                    "player" -> playerId?.toString()
                    "id" -> mapRestId(super.getParameter(name))
                    else -> super.getParameter(name)
                }
            }
            val user = requireNotNull(securityService.getCurrentUser(wrapped))
            if (!user.isStreamRole) {
                jaxbWriter.writeErrorResponse(wrapped, response, ErrorCode.NOT_AUTHORIZED,
                    "${user.username} is not authorized to play files.")
                return
            }
            val authentication = request.userPrincipal as Principal
            val mediaFile = mediaFileService.getMediaFile(id)
            val player = playerService.getPlayer(wrapped, response)
            if (mediaFile == null || mediaFile.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Media file not found or incorrect type: $id")
                return
            }
            val callerContext = callerContextAdapter.fromRequest(wrapped)
            if (!securityService.isFolderAccessAllowed(mediaFile, callerContext)) {
                if (path.endsWith(".view")) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
                throw AuthorizationDeniedException("Access to file ${mediaFile.id} is forbidden for user ${player.username}")
            }
            val duration = mediaFile.duration
            if (duration == null || duration < 0.0001) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unknown duration for media file: $id")
                return
            }
            val prefix = "ext/rest-hls"
            request.setAttribute(HLS_REQUEST, HlsRestController.Request(hlsLinkContext(authentication), mediaFile, player,
                NetworkService.getBaseUrl(request), prefix, request.getParameterValues("maxBitRate"),
                request.getParameter("size")))
        } else if (path in HlsSegmentRestController.PATHS && request.method in setOf("GET", "HEAD")) {
            val auth = request.userPrincipal as Principal
            val user = securityService.getUserByName(auth.name)
            if (user == null || !user.isStreamRole) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val id = request.getParameter("id")?.toIntOrNull()
            val playerId = request.getParameter("player")
            val index = request.getParameter("segmentIndex")?.toIntOrNull()
            val bitRate = request.getParameter("maxBitRate")?.toIntOrNull()
            val size = request.getParameter("size")
            val duration = request.getParameter("duration")?.toIntOrNull() ?: 10
            val audioTrack = request.getParameter("audioTrack")?.toIntOrNull()
            if (id == null || playerId?.toIntOrNull() == null || index == null || index < 0 ||
                bitRate == null || bitRate <= 0 || size == null || !size.matches(Regex("[1-9][0-9]*x[1-9][0-9]*")) ||
                duration <= 0 || (request.getParameter("audioTrack") != null && audioTrack == null) ||
                (request.getParameter("duration") != null && request.getParameter("duration").toIntOrNull() == null)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                return
            }
            val media = mediaFileService.getMediaFile(id)
            if (media == null || media.isDirectory) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            val callerContext = callerContextAdapter.fromRequest(request)
            if (!securityService.isFolderAccessAllowed(media, callerContext)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            val player = playerService.getPlayer(request, response)
            if (player.id.toString() != playerId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
                return
            }
            request.setAttribute(HLS_SEGMENT_REQUEST, HlsSegmentRestController.Request(auth is ExternalJwtPrincipal, user, callerContext, player,
                id, playerId, index, bitRate, size, duration, audioTrack))
        } else if (path == LiveQueueExportController.PATH && request.method in setOf("GET", "HEAD")) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val explicit = request.getParameter("playerId")
            val player = if (explicit != null) explicit.toIntOrNull()?.let { playerService.getPlayerById(it) }
                else playerService.getPlayer(request, response)
            request.setAttribute(QUEUE_EXPORT_REQUEST, LiveQueueExportController.Request(
                player?.takeIf { it.username == username }, username, callerContextAdapter.fromRequest(request),
                org.airsonic.player.service.NetworkService.getBaseUrl(request), parameters(request)))
        } else if (path == RandomQueueController.PATH && request.method == "POST") {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val explicit = request.getParameter("playerId")
            val player = if (explicit != null) explicit.toIntOrNull()?.let { playerService.getPlayerById(it) }
                else playerService.getPlayer(request, response)
            request.setAttribute(RANDOM_QUEUE_REQUEST, RandomQueueController.Request(
                player?.takeIf { it.username == username }, username, parameters(request)))
        } else if (path in LiveQueueController.PATHS) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            val player = request.getParameter("playerId")?.toIntOrNull()?.let { playerService.getPlayerById(it) }
            request.setAttribute(LIVE_QUEUE_REQUEST, LiveQueueController.Request(
                player?.takeIf { it.username == username }, parameters(request), username, callerContextAdapter.fromRequest(request)))
        } else if (path in StatisticsController.PATHS) {
            requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(STATISTICS_REQUEST, StatisticsController.Request(parameters(request), localeResolver.resolveLocale(request)))
        } else if (path in UploadStatusController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(UPLOAD_STATUS_REQUEST, UploadStatusController.Request(
                user.username, user.isAdminRole || user.isUploadRole, user.isAdminRole, parameters(request)))
        } else if (path in WebsiteSettingsController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(WEBSITE_SETTINGS_REQUEST, WebsiteSettingsController.Request(user.username, parameters(request), user.isSettingsRole))
        } else if (path == RightPageController.DATA_PATH) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(RightPageController.REQUEST_ATTRIBUTE, RightPageController.Request(username))
        } else if (path == IndexPageController.DATA_PATH) {
            val username = requireNotNull(securityService.getCurrentUsername(request))
            request.setAttribute(IndexPageController.REQUEST_ATTRIBUTE, IndexPageController.Request(username))
        } else if (path in TagEditController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(TAG_EDIT_REQUEST, TagEditController.Request(
                callerContextAdapter.fromRequest(request), user.isAdminRole || user.isCoverArtRole, parameters(request)))
        } else if (path == TagEditorPageController.PATH) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(TagEditorPageController.REQUEST_ATTRIBUTE, TagEditorPageController.Request(
                user.username, callerContextAdapter.fromRequest(request), request.getParameter("id")?.toIntOrNull(), user.isAdminRole || user.isCoverArtRole))
        } else if (path in CoverArtActionsController.PATHS) {
            val user = requireNotNull(securityService.getCurrentUser(request))
            request.setAttribute(COVER_ACTIONS_REQUEST, CoverArtActionsController.Request(
                user.username, user.isAdminRole || user.isCoverArtRole, parameters(request)))
        } else if (path in AvatarRestController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            val context = request.servletContext
            request.setAttribute(AVATAR_REQUEST, AvatarRestController.Request(
                parametersWithMappedId(request), webResource = { context.getResourceAsStream(it) }))
        } else if (path in AvatarRestController.LEGACY_PATHS) {
            val context = request.servletContext
            request.setAttribute(AVATAR_REQUEST, AvatarRestController.Request(parameters(request), { context.getResourceAsStream(it) }, true))
        } else if (path in CoverArtRestController.PATHS) {
            playerResolver.getOrCreatePlayerId(request.remoteUser, request.getParameter("c"), request.remoteAddr, false)
            request.setAttribute(REST_PARAMETERS, parametersWithMappedId(request))
        } else if (path in CoverArtRestController.LEGACY_PATHS) {
            val params = parameters(request)
            if (path.startsWith("/ext/coverArt") && request.userPrincipal is ExternalJwtPrincipal) {
                val auth = request.userPrincipal as ExternalJwtPrincipal
                val expected = auth.signedPath
                val signedId = try {
                    parseQueryParams(expected)["id"]?.firstOrNull()
                } catch (_: IllegalArgumentException) { null }
                if (signedId == null || signedId != params["id"]) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)
                    return
                }
            }
            request.setAttribute(REST_PARAMETERS, params)
        }
        super.service(request, response)
    }

    private fun isRemovedWebsitePage(path: String): Boolean {
        val pagePath = path.removeSuffix(".view").ifEmpty { "/" }
        return pagePath in REMOVED_WEBSITE_PAGES
    }

    private fun parameters(request: HttpServletRequest): Parameters = Parameters.build {
        // Security filters may already have consumed a POST body. Use the servlet's parsed parameters.
        request.parameterNames.asSequence().distinct().forEach { name ->
            request.getParameterValues(name)?.let { appendAll(name, it.toList()) }
        }
    }

    /**
     * Mirrors the old REST request wrapper: its single {@code id} parameter was
     * resolved from the legacy UTF-8-hex path form before endpoint code read it.
     */
    private fun parametersWithMappedId(request: HttpServletRequest): Parameters = Parameters.build {
        request.parameterNames.asSequence().distinct().forEach { name ->
            val values = request.getParameterValues(name)?.toList().orEmpty()
            if (name == "id" && values.isNotEmpty()) {
                append(name, mapRestId(values.first()) ?: values.first())
            } else {
                appendAll(name, values)
            }
        }
    }

    private fun mapRestId(id: String?): String? {
        if (id == null || id.startsWith(CoverArtImageService.ALBUM_COVERART_PREFIX) ||
            id.startsWith(CoverArtImageService.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)
        ) return id
        return try {
            mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).id.toString()
        } catch (_: Exception) {
            id
        }
    }

    private companion object {
        val REMOVED_WEBSITE_PAGES = setOf(
            "/", "/index", "/login", "/logout", "/recover", "/accessDenied", "/notFound",
            "/home", "/main", "/left", "/right", "/more", "/search", "/starred",
            "/bookmarks", "/playlist", "/playlists", "/playQueue", "/nowPlaying",
            "/editTags", "/changeCoverArt", "/lyrics", "/createShare", "/importPlaylist",
            "/videoPlayer", "/help", "/gettingStarted", "/userSettings", "/musicFolderSettings",
            "/generalSettings", "/transcodingSettings", "/advancedSettings", "/playerSettings",
            "/personalSettings", "/shareSettings", "/credentialsSettings", "/databaseSettings",
            "/dlnaSettings", "/settings", "/top", "/status", "/ext/share",
        )
        const val STREAM_REQUEST = "org.airsonic.player.streamRequest"
        val STREAM_REQUEST_KEY = AttributeKey<StreamRestController.Request>(STREAM_REQUEST)
        const val HLS_SEGMENT_REQUEST = "org.airsonic.player.hlsSegmentRequest"
        val HLS_SEGMENT_REQUEST_KEY = AttributeKey<HlsSegmentRestController.Request>(HLS_SEGMENT_REQUEST)
        const val SERVER_INFO_REQUEST = "org.airsonic.player.serverInfoRequest"
        val SERVER_INFO_REQUEST_KEY = AttributeKey<ServerInfoController.Request>(SERVER_INFO_REQUEST)
        const val PLAY_QUEUE_PAGE_REQUEST = "org.airsonic.player.playQueuePageRequest"
        val PLAY_QUEUE_PAGE_REQUEST_KEY = AttributeKey<PlayQueuePageController.Request>(PLAY_QUEUE_PAGE_REQUEST)
        const val RECOVERY_REQUEST = "org.airsonic.player.recoveryRequest"
        val RECOVERY_REQUEST_KEY = AttributeKey<PasswordRecoveryController.Request>(RECOVERY_REQUEST)
        const val VIDEO_PLAYER_REQUEST = "org.airsonic.player.videoPlayerRequest"
        val VIDEO_PLAYER_REQUEST_KEY = AttributeKey<VideoPlayerRestController.Request>(VIDEO_PLAYER_REQUEST)
        const val CAPTIONS_REQUEST = "org.airsonic.player.captionsRequest"
        val CAPTIONS_REQUEST_KEY = AttributeKey<CaptionsRestController.Request>(CAPTIONS_REQUEST)
        const val PUBLIC_SHARE_REQUEST = "org.airsonic.player.publicShareRequest"
        val PUBLIC_SHARE_REQUEST_KEY = AttributeKey<PublicShareController.Request>(PUBLIC_SHARE_REQUEST)
        const val HLS_REQUEST = "org.airsonic.player.hlsRequest"
        val HLS_REQUEST_KEY = AttributeKey<HlsRestController.Request>(HLS_REQUEST)
        const val QUEUE_EXPORT_REQUEST = "org.airsonic.player.queueExportRequest"
        val QUEUE_EXPORT_REQUEST_KEY = AttributeKey<LiveQueueExportController.Request>(QUEUE_EXPORT_REQUEST)
        const val LIVE_QUEUE_REQUEST = "org.airsonic.player.liveQueueRequest"
        private const val RANDOM_QUEUE_REQUEST = "airsonic.randomQueueRequest"
        private val RANDOM_QUEUE_REQUEST_KEY = AttributeKey<RandomQueueController.Request>(RANDOM_QUEUE_REQUEST)
        val LIVE_QUEUE_REQUEST_KEY = AttributeKey<LiveQueueController.Request>(LIVE_QUEUE_REQUEST)
        const val STATISTICS_REQUEST = "org.airsonic.player.statisticsRequest"
        val STATISTICS_REQUEST_KEY = AttributeKey<StatisticsController.Request>(STATISTICS_REQUEST)
        const val UPLOAD_STATUS_REQUEST = "org.airsonic.player.uploadStatusRequest"
        val UPLOAD_STATUS_REQUEST_KEY = AttributeKey<UploadStatusController.Request>(UPLOAD_STATUS_REQUEST)
        const val WEBSITE_SETTINGS_REQUEST = "org.airsonic.player.websiteSettingsRequest"
        val WEBSITE_SETTINGS_REQUEST_KEY = AttributeKey<WebsiteSettingsController.Request>(WEBSITE_SETTINGS_REQUEST)
        const val AVATAR_REQUEST = "org.airsonic.player.avatarRequest"
        val AVATAR_REQUEST_KEY = AttributeKey<AvatarRestController.Request>(AVATAR_REQUEST)
        const val TAG_EDIT_REQUEST = "org.airsonic.player.tagEditRequest"
        val TAG_EDIT_REQUEST_KEY = AttributeKey<TagEditController.Request>(TAG_EDIT_REQUEST)
        val TAG_EDITOR_PAGE_REQUEST_KEY = AttributeKey<TagEditorPageController.Request>(TagEditorPageController.REQUEST_ATTRIBUTE)
        const val COVER_ACTIONS_REQUEST = "org.airsonic.player.coverActionsRequest"
        val COVER_ACTIONS_REQUEST_KEY = AttributeKey<CoverArtActionsController.Request>(COVER_ACTIONS_REQUEST)
        const val REST_PARAMETERS = "org.airsonic.player.restParameters"
        val REST_PARAMETERS_KEY = AttributeKey<Parameters>(REST_PARAMETERS)
        val NOW_PLAYING_PAGE_REQUEST_KEY = AttributeKey<NowPlayingPageController.Request>(NowPlayingPageController.REQUEST_ATTRIBUTE)
        val RIGHT_PAGE_REQUEST_KEY = AttributeKey<RightPageController.Request>(RightPageController.REQUEST_ATTRIBUTE)
        val INDEX_PAGE_REQUEST_KEY = AttributeKey<IndexPageController.Request>(IndexPageController.REQUEST_ATTRIBUTE)
        val MORE_PAGE_REQUEST_KEY = AttributeKey<MorePageController.Request>(MorePageController.REQUEST_ATTRIBUTE)
        val LEFT_PAGE_REQUEST_KEY = AttributeKey<LeftPageController.Request>(LeftPageController.REQUEST_ATTRIBUTE)
        val LEFT_PAGE_SELECTION_KEY = AttributeKey<LeftPageController.Selection>(LeftPageController.SELECTION_ATTRIBUTE)
        val BOOKMARKS_PAGE_REQUEST_KEY = AttributeKey<BookmarksPageController.Request>(BookmarksPageController.REQUEST_ATTRIBUTE)
        const val PLAYLIST_PAGE_REQUEST = PlaylistPageController.REQUEST_ATTRIBUTE
        val PLAYLIST_PAGE_REQUEST_KEY = AttributeKey<PlaylistPageController.Request>(PLAYLIST_PAGE_REQUEST)
        const val MAIN_PAGE_REQUEST = MainPageController.REQUEST_ATTRIBUTE
        val MAIN_PAGE_REQUEST_KEY = AttributeKey<MainPageController.Request>(MAIN_PAGE_REQUEST)
        const val PLAYLISTS_PAGE_REQUEST = PlaylistsPageController.REQUEST_ATTRIBUTE
        val PLAYLISTS_PAGE_REQUEST_KEY = AttributeKey<PlaylistsPageController.Request>(PLAYLISTS_PAGE_REQUEST)
        val HOME_PAGE_REQUEST_KEY = AttributeKey<HomePageController.Request>(HomePageController.REQUEST_ATTRIBUTE)
        const val SEARCH_REQUEST = "org.airsonic.player.searchRequest"
        val SEARCH_REQUEST_KEY = AttributeKey<SearchController.Request>(SEARCH_REQUEST)
        const val SEARCH_PAGE_REQUEST = SearchPageController.REQUEST_ATTRIBUTE
        val SEARCH_PAGE_REQUEST_KEY = AttributeKey<SearchPageController.Request>(SEARCH_PAGE_REQUEST)
        const val MUSIC_FOLDERS_REQUEST = "org.airsonic.player.musicFoldersRequest"
        val MUSIC_FOLDERS_REQUEST_KEY = AttributeKey<MusicFoldersController.Request>(
            MUSIC_FOLDERS_REQUEST
        )
        const val ARTISTS_REQUEST = "org.airsonic.player.artistsRequest"
        val ARTISTS_REQUEST_KEY = AttributeKey<ArtistsRequest>(ARTISTS_REQUEST)
        const val USERS_REQUEST = "org.airsonic.player.usersRequest"
        val USERS_REQUEST_KEY = AttributeKey<UsersRequest>(USERS_REQUEST)
        const val PLAY_QUEUE_REQUEST = "org.airsonic.player.playQueueRequest"
        val PLAY_QUEUE_REQUEST_KEY = AttributeKey<PlayQueueRequest>(PLAY_QUEUE_REQUEST)
        const val SCROBBLE_REQUEST = "org.airsonic.player.scrobbleRequest"
        val SCROBBLE_REQUEST_KEY = AttributeKey<ScrobbleRequest>(SCROBBLE_REQUEST)
        const val WEBSITE_DOWNLOAD_REQUEST = "org.airsonic.player.websiteDownloadRequest"
        val WEBSITE_DOWNLOAD_REQUEST_KEY = AttributeKey<DownloadRestController.WebsiteRequest>(WEBSITE_DOWNLOAD_REQUEST)
        const val DOWNLOAD_REQUEST = "org.airsonic.player.downloadRequest"
        val DOWNLOAD_REQUEST_KEY = AttributeKey<DownloadRequest>(DOWNLOAD_REQUEST)
        const val RANDOM_SONGS_REQUEST = "org.airsonic.player.randomSongsRequest"
        val RANDOM_SONGS_REQUEST_KEY = AttributeKey<RandomSongsRequest>(RANDOM_SONGS_REQUEST)
        const val MUSIC_DIRECTORY_REQUEST = "org.airsonic.player.musicDirectoryRequest"
        val MUSIC_DIRECTORY_REQUEST_KEY = AttributeKey<MusicDirectoryRequest>(MUSIC_DIRECTORY_REQUEST)
        const val SONG_REQUEST = "org.airsonic.player.songRequest"
        val SONG_REQUEST_KEY = AttributeKey<SongRequest>(SONG_REQUEST)
        const val VIDEOS_REQUEST = "org.airsonic.player.videosRequest"
        val VIDEOS_REQUEST_KEY = AttributeKey<VideosRequest>(VIDEOS_REQUEST)
        const val RATING_REQUEST = "org.airsonic.player.ratingRequest"
        val RATING_REQUEST_KEY = AttributeKey<RatingRequest>(RATING_REQUEST)
        const val TOP_SONGS_REQUEST = "org.airsonic.player.topSongsRequest"
        val TOP_SONGS_REQUEST_KEY = AttributeKey<TopSongsRequest>(TOP_SONGS_REQUEST)
        const val GENRE_SONGS_REQUEST = "org.airsonic.player.genreSongsRequest"
        val GENRE_SONGS_REQUEST_KEY = AttributeKey<SongsByGenreRequest>(GENRE_SONGS_REQUEST)
        const val INDEXES_REQUEST = "org.airsonic.player.indexesRequest"
        val INDEXES_REQUEST_KEY = AttributeKey<IndexesRequest>(INDEXES_REQUEST)
        const val JUKEBOX_REQUEST = "org.airsonic.player.jukeboxRequest"
        val JUKEBOX_REQUEST_KEY = AttributeKey<JukeboxRequest>(JUKEBOX_REQUEST)
        const val STARRED_REQUEST = "org.airsonic.player.starredRequest"
        val STARRED_REQUEST_KEY = AttributeKey<StarredRequest>(STARRED_REQUEST)
        val STARRED_PAGE_REQUEST_KEY = AttributeKey<StarredPageController.Request>(StarredPageController.REQUEST_ATTRIBUTE)
        const val BOOKMARKS_REQUEST = "org.airsonic.player.bookmarksRequest"
        val BOOKMARKS_REQUEST_KEY = AttributeKey<BookmarksRequest>(BOOKMARKS_REQUEST)
        const val ALBUMS_REQUEST = "org.airsonic.player.albumsRequest"
        val ALBUMS_REQUEST_KEY = AttributeKey<AlbumsRequest>(ALBUMS_REQUEST)
        const val ALBUM_INFO_REQUEST = "org.airsonic.player.albumInfoRequest"
        val ALBUM_INFO_REQUEST_KEY = AttributeKey<AlbumInfoRequest>(ALBUM_INFO_REQUEST)
        const val SHARES_REQUEST = "org.airsonic.player.sharesRequest"
        val SHARES_REQUEST_KEY = AttributeKey<SharesRequest>(SHARES_REQUEST)
        const val PLAYLISTS_REQUEST = "org.airsonic.player.playlistsRequest"
        val PLAYLISTS_REQUEST_KEY = AttributeKey<PlaylistsRequest>(PLAYLISTS_REQUEST)
        const val ARTIST_INFO_REQUEST = "org.airsonic.player.artistInfoRequest"
        val ARTIST_INFO_REQUEST_KEY = AttributeKey<ArtistInfoRequest>(ARTIST_INFO_REQUEST)
        const val SIMILAR_SONGS_REQUEST = "org.airsonic.player.similarSongsRequest"
        val SIMILAR_SONGS_REQUEST_KEY = AttributeKey<SimilarSongsRequest>(SIMILAR_SONGS_REQUEST)
    }
}

private data class ParsedUriComponents(
    val path: String,
    val queryParams: Map<String, List<String>>,
) {
    fun withoutQueryParam(name: String): ParsedUriComponents =
        copy(queryParams = queryParams.filterKeys { it != name })
}

private fun parseUriComponents(rawUri: String?): ParsedUriComponents {
    if (rawUri.isNullOrEmpty()) return ParsedUriComponents("", emptyMap())
    val withoutFragment = rawUri.substringBefore('#')
    val rawPath = withoutFragment.substringBefore('?')
    val rawQuery = if (withoutFragment.contains('?')) withoutFragment.substringAfter('?') else null
    val queryParams = LinkedHashMap<String, MutableList<String>>()
    if (!rawQuery.isNullOrEmpty()) {
        for (param in rawQuery.split('&')) {
            val equalsIndex = param.indexOf('=')
            val name = if (equalsIndex < 0) param else param.substring(0, equalsIndex)
            val value = if (equalsIndex < 0) "" else param.substring(equalsIndex + 1)
            queryParams.computeIfAbsent(name) { mutableListOf() }.add(value)
        }
    }
    return ParsedUriComponents(rawPath, queryParams)
}

private fun parseQueryParams(rawUri: String?): Map<String, List<String>> =
    parseUriComponents(rawUri).queryParams
