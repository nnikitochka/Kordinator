package org.airsonic.player.controller.rest;

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
    private final CoverArtController coverArtController;
    private final AvatarController avatarController;
    private final StreamController streamController;
    private final HLSController hlsController;
    private final PlayQueueService playQueueService;
    private final JukeboxService jukeboxService;
    private final RatingService ratingService;
    private final MediaFileDao mediaFileDao;
    private final ArtistDao artistDao;
    private final AlbumDao albumDao;
    private final MediaFolderService mediaFolderService;
    private final CoverArtService coverArtService;
    private final SubsonicResponseMapper responseMapper;
    private final RestPlayerResolver playerResolver;

    private final JAXBWriter jaxbWriter = new JAXBWriter();

    public SubsonicRESTController(AvatarController avatarController, SettingsService settingsService, SecurityService securityService, MediaFileDao mediaFileDao, PlayerService playerService, MediaFileService mediaFileService, LastFmService lastFmService, AlbumDao albumDao, CoverArtService coverArtService, TranscodingService transcodingService, JukeboxService jukeboxService, RatingService ratingService, CoverArtController coverArtController, StreamController streamController, PlayQueueService playQueueService, MediaFolderService mediaFolderService, ArtistDao artistDao, HLSController hlsController, SubsonicResponseMapper responseMapper, RestPlayerResolver playerResolver) {
        this.avatarController = avatarController;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.mediaFileDao = mediaFileDao;
        this.playerService = playerService;
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.albumDao = albumDao;
        this.coverArtService = coverArtService;
        this.transcodingService = transcodingService;
        this.jukeboxService = jukeboxService;
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

    private Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(player, mediaFile, username);
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
