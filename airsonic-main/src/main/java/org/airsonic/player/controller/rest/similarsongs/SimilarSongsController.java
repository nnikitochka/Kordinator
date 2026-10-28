package org.airsonic.player.controller.rest.similarsongs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.SimilarSongs;
import org.subsonic.restapi.SimilarSongs2;

import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SimilarSongsController {
    private final MediaFileService mediaFiles;
    private final ArtistDao artists;
    private final LastFmService lastFm;
    private final MediaFolderService folders;
    private final PlayerService players;
    private final SecurityService security;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public SimilarSongsController(MediaFileService mediaFiles, ArtistDao artists, LastFmService lastFm,
                                  MediaFolderService folders, PlayerService players, SecurityService security,
                                  RestPlayerResolver playerResolver, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.mediaFiles = mediaFiles;
        this.artists = artists;
        this.lastFm = lastFm;
        this.folders = folders;
        this.players = players;
        this.security = security;
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

    @RequestMapping("/getSimilarSongs")
    public void getSimilarSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = security.getCurrentUsername(request);
        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);
        MediaFile mediaFile = mediaFiles.getMediaFile(id);
        if (mediaFile == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<MusicFolder> musicFolders = folders.getMusicFoldersForUser(username);
        List<MediaFile> songs = lastFm.getSimilarSongs(mediaFile, count, musicFolders);
        Player player = players.getPlayer(request, response);
        SimilarSongs result = new SimilarSongs();
        for (MediaFile song : songs) result.getSong().add(mapper.child(player, song, username));
        Response res = writer.createResponse(true);
        res.setSimilarSongs(result);
        writer.writeResponse(request, response, res);
    }

    @RequestMapping("/getSimilarSongs2")
    public void getSimilarSongs2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = security.getCurrentUsername(request);
        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);
        var artist = artists.getArtist(id);
        if (artist == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }
        List<MusicFolder> musicFolders = folders.getMusicFoldersForUser(username);
        List<MediaFile> songs = lastFm.getSimilarSongs(artist, count, musicFolders);
        Player player = players.getPlayer(request, response);
        SimilarSongs2 result = new SimilarSongs2();
        for (MediaFile song : songs) result.getSong().add(mapper.child(player, song, username));
        Response res = writer.createResponse(true);
        res.setSimilarSongs2(result);
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
            return String.valueOf(mediaFiles.getMediaFile(StringUtil.utf8HexDecode(id)).getId());
        } catch (Exception ignored) {
            return id;
        }
    }
}
