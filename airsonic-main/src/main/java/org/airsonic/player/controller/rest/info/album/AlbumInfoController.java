package org.airsonic.player.controller.rest.info.album;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.AlbumNotes;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumInfo;
import org.subsonic.restapi.Response;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class AlbumInfoController {
    private final MediaFileService mediaFileService;
    private final AlbumDao albumDao;
    private final LastFmService lastFmService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter writer;

    public AlbumInfoController(MediaFileService mediaFileService, AlbumDao albumDao, LastFmService lastFmService,
                               RestPlayerResolver playerResolver, JAXBWriter writer) {
        this.mediaFileService = mediaFileService;
        this.albumDao = albumDao;
        this.lastFmService = lastFmService;
        this.playerResolver = playerResolver;
        this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missingParameter(HttpServletRequest request, HttpServletResponse response,
                                 MissingServletRequestParameterException exception) {
        writer.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping(path = "/getAlbumInfo")
    public void getAlbumInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotes(mediaFile);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = writer.createResponse(true);
        res.setAlbumInfo(result);
        writer.writeResponse(request, response, res);
    }

    @RequestMapping(path = "/getAlbumInfo2")
    public void getAlbumInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        Album album = this.albumDao.getAlbum(id);
        if (album == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotes(album);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = writer.createResponse(true);
        res.setAlbumInfo(result);
        writer.writeResponse(request, response, res);
    }

    private AlbumInfo getAlbumInfoInternal(AlbumNotes albumNotes) {
        AlbumInfo result = new AlbumInfo();
        if (albumNotes != null) {
            result.setNotes(albumNotes.getNotes());
            result.setMusicBrainzId(albumNotes.getMusicBrainzId());
            result.setLastFmUrl(albumNotes.getLastFmUrl());
            result.setSmallImageUrl(albumNotes.getSmallImageUrl());
            result.setMediumImageUrl(albumNotes.getMediumImageUrl());
            result.setLargeImageUrl(albumNotes.getLargeImageUrl());
        }
        return result;
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        return wrapRequest(request, false);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, boolean jukebox) {
        final Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), jukebox);
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

}
