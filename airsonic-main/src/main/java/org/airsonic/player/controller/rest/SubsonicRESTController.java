package org.airsonic.player.controller.rest;

import org.airsonic.player.ajax.lyrics.LyricsInfo;
import org.airsonic.player.ajax.lyrics.LyricsWSController;
import org.airsonic.player.ajax.playing.NowPlayingInfo;
import org.airsonic.player.controller.*;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.*;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.util.Util;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import org.subsonic.restapi.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.datatype.XMLGregorianCalendar;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static org.springframework.web.bind.ServletRequestUtils.*;

/**
 * Multi-controller used for the REST API.
 * <p/>
 * For documentation, please refer to api.jsp.
 * <p/>
 * Note: Exceptions thrown from the methods are intercepted by RESTFilter.
 */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicRESTController {
    private static final Logger LOG = LoggerFactory.getLogger(SubsonicRESTController.class);

    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final MediaFileService mediaFileService;
    private final LastFmService lastFmService;
    private final TranscodingService transcodingService;
    private final DownloadController downloadController;
    private final CoverArtController coverArtController;
    private final AvatarController avatarController;
    private final StatusService statusService;
    private final StreamController streamController;
    private final HLSController hlsController;
    private final LyricsWSController lyricsWSController;
    private final PlayQueueService playQueueService;
    private final JukeboxService jukeboxService;
    private final RatingService ratingService;
    private final SearchService searchService;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final MediaFolderService mediaFolderService;
    private final CoverArtService coverArtService;
    private final SubsonicResponseMapper responseMapper;
    private final RestPlayerResolver playerResolver;

    private final JAXBWriter jaxbWriter = new JAXBWriter();

    public SubsonicRESTController(AvatarController avatarController, SettingsService settingsService, SecurityService securityService, MediaFileDao mediaFileDao, PlayerService playerService, MediaFileService mediaFileService, LastFmService lastFmService, AlbumDao albumDao, SearchService searchService, StatusService statusService, CoverArtService coverArtService, TranscodingService transcodingService, JukeboxService jukeboxService, DownloadController downloadController, LyricsWSController lyricsWSController, RatingService ratingService, CoverArtController coverArtController, StreamController streamController, PlayQueueService playQueueService, MediaFolderService mediaFolderService, ArtistDao artistDao, HLSController hlsController, SubsonicResponseMapper responseMapper, RestPlayerResolver playerResolver) {
        this.avatarController = avatarController;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.mediaFileDao = mediaFileDao;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.albumDao = albumDao;
        this.searchService = searchService;
        this.statusService = statusService;
        this.coverArtService = coverArtService;
        this.transcodingService = transcodingService;
        this.jukeboxService = jukeboxService;
        this.downloadController = downloadController;
        this.lyricsWSController = lyricsWSController;
        this.ratingService = ratingService;
        this.coverArtController = coverArtController;
        this.streamController = streamController;
        this.playQueueService = playQueueService;
        this.mediaFolderService = mediaFolderService;
        this.artistDao = artistDao;
        this.hlsController = hlsController;
        this.responseMapper = responseMapper;
        this.playerResolver = playerResolver;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request,
                                          HttpServletResponse response,
                                          MissingServletRequestParameterException exception) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + exception.getParameterName() + ") is missing");
    }

    /**
     * CAUTION : this method is required by mobile applications and must not be removed.
     */
    @RequestMapping("/getLicense")
    public void getLicense(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        License license = new License();

        license.setEmail("airsonic@github.com");
        license.setValid(true);
        XMLGregorianCalendar farFuture = jaxbWriter.convertDate(Instant.now().plus(ChronoUnit.YEARS.getDuration().multipliedBy(100)));
        license.setLicenseExpires(farFuture);
        license.setTrialExpires(farFuture);

        Response res = createResponse();
        res.setLicense(license);
        jaxbWriter.writeResponse(request, response, res);
    }


    private <T extends ArtistID3> T createJaxbArtist(T jaxbArtist, org.airsonic.player.domain.Artist artist, String username) {
        return responseMapper.artist(jaxbArtist, artist, username);
    }

    private <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username) {
        return responseMapper.album(jaxbAlbum, album, username);
    }

    @RequestMapping("/getSong")
    public void getSong(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        MediaFile song = mediaFileDao.getMediaFile(id);
        if (song == null || song.isDirectory()) {
            error(request, response, ErrorCode.NOT_FOUND, "Song not found.");
            return;
        }
        if (!securityService.isFolderAccessAllowed(song, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        Response res = createResponse();
        res.setSong(createJaxbChild(player, song, username));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getRandomSongs")
    public void getRandomSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int size = getIntParameter(request, "size", 10);
        size = Math.max(0, Math.min(size, 500));
        String genre = getStringParameter(request, "genre");
        Integer fromYear = getIntParameter(request, "fromYear");
        Integer toYear = getIntParameter(request, "toYear");
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);
        RandomSearchCriteria criteria = new RandomSearchCriteria(size, genre, fromYear, toYear, musicFolders);

        Songs result = new Songs();
        for (MediaFile mediaFile : searchService.getRandomSongs(criteria)) {
            result.getSong().add(createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setRandomSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getVideos")
    public void getVideos(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int size = getIntParameter(request, "size", Integer.MAX_VALUE);
        int offset = getIntParameter(request, "offset", 0);
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Videos result = new Videos();
        for (MediaFile mediaFile : mediaFileDao.getVideos(size, offset, musicFolders)) {
            result.getVideo().add(createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setVideos(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getNowPlaying")
    public void getNowPlaying(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        NowPlaying result = new NowPlaying();

        Stream.concat(statusService.getActivePlays().parallelStream(),
                statusService.getInactivePlays().parallelStream())
            .map(NowPlayingInfo::fromPlayStatus)
            .forEach(s -> {
                NowPlayingEntry entry = new NowPlayingEntry();
                entry.setUsername(s.getPlayer().getUsername());
                entry.setPlayerId(s.getPlayer().getId());
                entry.setPlayerName(s.getPlayer().getName());
                entry.setMinutesAgo((int) s.getMinutesAgo());
                result.getEntry().add(createJaxbChild(entry, s.getPlayer(), s.getMediaFile(), entry.getUsername()));
            });

        Response res = createResponse();
        res.setNowPlaying(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(player, mediaFile, username);
    }

    private <T extends Child> T createJaxbChild(T child, Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(child, player, mediaFile, username);
    }

    @RequestMapping("/download")
    public ResponseEntity<Resource> download(Principal p,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) Integer playlist,
            @RequestParam(required = false) Integer player,
            @RequestParam(required = false, name = "i") List<Integer> indices,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        final Integer playerId = Optional.ofNullable(request.getParameter("player")).map(Integer::valueOf).orElse(null);
        Optional<Integer> idInt = Optional.ofNullable(id).map(this::mapId).filter(StringUtils::isNumeric).map(Integer::valueOf);

        User user = securityService.getUserByName(p.getName());
        if (!user.isDownloadRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to download files.");
        }
        return downloadController.handleRequest(p, idInt, playlist, playerId, indices,
                new ServletWebRequest(request, swr.getResponse()));
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest swr, APIException exception) {
        Entry<String, String> exceptionResponse = jaxbWriter.serializeForType(swr.getRequest(),
                jaxbWriter.createErrorResponse(exception));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(exceptionResponse.getKey()))
                .body(exceptionResponse.getValue());
    }

    @RequestMapping("/stream")
    public ResponseEntity<Resource> stream(Authentication authentication,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String suffix,
            @RequestParam Optional<Integer> maxBitRate,
            @RequestParam Optional<Integer> id,
            @RequestParam Optional<String> path,
            @RequestParam(required = false) Double offsetSeconds,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        User user = securityService.getUserByName(authentication.getName());
        if (!user.isStreamRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
        }

        return streamController.handleRequest(authentication, format, suffix, maxBitRate, id, path,
                offsetSeconds, new ServletWebRequest(request, swr.getResponse()));
    }

    @RequestMapping("/hls")
    public void hls(Authentication authentication, @RequestParam Integer id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isStreamRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
            return;
        }

        hlsController.handleHlsRequest(authentication, id, request, response);
    }

    @RequestMapping("/getCoverArt")
    public void getCoverArt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        coverArtController.handleRequest(request, response);
    }

    @RequestMapping("/getAvatar")
    public void getAvatar(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        avatarController.handleRequest(request, response);
    }

    @RequestMapping("/getLyrics")
    public void getLyrics(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        String artist = request.getParameter("artist");
        String title = request.getParameter("title");
        LyricsInfo lyrics = lyricsWSController.getLyrics(artist, title);

        Lyrics result = new Lyrics();
        result.setArtist(lyrics.getArtist());
        result.setTitle(lyrics.getTitle());
        result.setContent(lyrics.getLyrics());

        Response res = createResponse();
        res.setLyrics(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/setRating")
    public void setRating(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Integer rating = getRequiredIntParameter(request, "rating");
        if (rating == 0) {
            rating = null;
        }

        int id = getRequiredIntParameter(request, "id");
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, ErrorCode.NOT_FOUND, "File not found: " + id);
            return;
        }

        String username = securityService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        writeEmptyResponse(request, response);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        return wrapRequest(request, false);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, boolean jukebox) {
        final Integer playerId = createPlayerIfNecessary(request, jukebox);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                // Returns the correct player to be used in PlayerService.getPlayer()
                if ("player".equals(name)) {
                    return playerId == null ? null : String.valueOf(playerId);
                }

                // Support old style ID parameters.
                if ("id".equals(name)) {
                    return mapId(request.getParameter("id"));
                }

                return super.getParameter(name);
            }
        };
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) {
            return id;
        }

        try {
            String path = StringUtil.utf8HexDecode(id);
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            return String.valueOf(mediaFile.getId());
        } catch (Exception x) {
            return id;
        }
    }

    private Response createResponse() {
        return jaxbWriter.createResponse(true);
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }

    public void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }

    private Integer createPlayerIfNecessary(HttpServletRequest request, boolean jukebox) {
        return playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"),
                request.getRemoteAddr(), jukebox);
    }
}
