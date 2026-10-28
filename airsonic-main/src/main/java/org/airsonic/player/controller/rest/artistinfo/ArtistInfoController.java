package org.airsonic.player.controller.rest.artistinfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistInfo;
import org.subsonic.restapi.ArtistInfo2;
import org.subsonic.restapi.Response;
import java.util.List;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class ArtistInfoController {
    private final MediaFileService mediaFileService;
    private final ArtistDao artistDao;
    private final LastFmService lastFmService;
    private final MediaFolderService mediaFolderService;
    private final SecurityService securityService;
    private final LocaleResolver localeResolver;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public ArtistInfoController(MediaFileService mediaFileService, ArtistDao artistDao, LastFmService lastFmService,
            MediaFolderService mediaFolderService, SecurityService securityService, LocaleResolver localeResolver,
            RestPlayerResolver playerResolver, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.mediaFileService = mediaFileService;
        this.artistDao = artistDao;
        this.lastFmService = lastFmService;
        this.mediaFolderService = mediaFolderService;
        this.securityService = securityService;
        this.localeResolver = localeResolver;
        this.playerResolver = playerResolver;
        this.mapper = mapper;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missingParameter(HttpServletRequest request, HttpServletResponse response,
                                 MissingServletRequestParameterException exception) {
        writer.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping("/getArtistInfo")
    public void getArtistInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo result = new ArtistInfo();

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarArtists = lastFmService.getSimilarArtists(mediaFile, count, includeNotPresent, musicFolders);
        for (MediaFile similarArtist : similarArtists) {
            result.getSimilarArtist().add(mapper.artist(similarArtist, username));
        }
        ArtistBio artistBio = lastFmService.getArtistBio(mediaFile, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
            result.setSmallImageUrl(artistBio.getSmallImageUrl());
            result.setMediumImageUrl(artistBio.getMediumImageUrl());
            result.setLargeImageUrl(artistBio.getLargeImageUrl());
        }

        Response res = writer.createResponse(true);
        res.setArtistInfo(result);
        writer.writeResponse(request, response, res);
    }

    @RequestMapping("/getArtistInfo2")
    public void getArtistInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo2 result = new ArtistInfo2();

        org.airsonic.player.domain.Artist artist = artistDao.getArtist(id);
        if (artist == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<org.airsonic.player.domain.Artist> similarArtists = lastFmService.getSimilarArtists(artist, count, includeNotPresent, musicFolders);
        for (org.airsonic.player.domain.Artist similarArtist : similarArtists) {
            result.getSimilarArtist().add(mapper.artist(new ArtistID3(), similarArtist, username));
        }
        ArtistBio artistBio = lastFmService.getArtistBio(artist, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
            result.setSmallImageUrl(artistBio.getSmallImageUrl());
            result.setMediumImageUrl(artistBio.getMediumImageUrl());
            result.setLargeImageUrl(artistBio.getLargeImageUrl());
        }

        Response res = writer.createResponse(true);
        res.setArtistInfo2(result);
        writer.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"),
                request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : String.valueOf(playerId);
                if ("id".equals(name)) return mapId(request.getParameter("id"));
                return super.getParameter(name);
            }
        };
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX)
                || id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) return id;
        try {
            return String.valueOf(mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).getId());
        } catch (Exception ignored) {
            return id;
        }
    }
}
