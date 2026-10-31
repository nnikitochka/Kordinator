package org.airsonic.player.controller.rest.albums;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.*;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.AlbumWithSongsID3;
import org.subsonic.restapi.AlbumList;
import org.subsonic.restapi.AlbumList2;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.Response;
import java.util.List;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class AlbumsController {
    private final AlbumDao albumDao;
    private final MediaFileDao mediaFileDao;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final RatingService ratingService;
    private final SearchService searchService;
    private final SecurityService securityService;
    private final PlayerService playerService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper responseMapper;
    private final JAXBWriter jaxbWriter;

    public AlbumsController(AlbumDao albumDao, MediaFileDao mediaFileDao, MediaFileService mediaFileService,
                            MediaFolderService mediaFolderService, RatingService ratingService, SearchService searchService,
                            SecurityService securityService, PlayerService playerService, RestPlayerResolver playerResolver,
                            SubsonicResponseMapper responseMapper, JAXBWriter jaxbWriter) {
        this.albumDao = albumDao;
        this.mediaFileDao = mediaFileDao;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.ratingService = ratingService;
        this.searchService = searchService;
        this.securityService = securityService;
        this.playerService = playerService;
        this.playerResolver = playerResolver;
        this.responseMapper = responseMapper;
        this.jaxbWriter = jaxbWriter;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missingParameter(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + e.getParameterName() + ") is missing");
    }

    @RequestMapping("/getAlbum")
    public void getAlbum(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        Album album = albumDao.getAlbum(id);
        if (album == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }

        AlbumWithSongsID3 result = createJaxbAlbum(new AlbumWithSongsID3(), album, username);
        for (MediaFile mediaFile : mediaFileDao.getSongsForAlbum(album.getArtist(), album.getName())) {
            result.getSong().add(createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setAlbum(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getAlbumList")
    public void getAlbumList(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        size = Math.clamp(size, 0, 500);
        String type = getRequiredStringParameter(request, "type");

        List<MediaFile> albums = switch (type) {
            case "highest" -> ratingService.getHighestRatedAlbums(offset, size, musicFolders);
            case "frequent" -> mediaFileService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
            case "recent" -> mediaFileService.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
            case "newest" -> mediaFileService.getNewestAlbums(offset, size, musicFolders);
            case "starred" -> mediaFileService.getStarredAlbums(offset, size, username, musicFolders);
            case "alphabeticalByArtist" -> mediaFileService.getAlphabeticalAlbums(offset, size, true, musicFolders);
            case "alphabeticalByName" -> mediaFileService.getAlphabeticalAlbums(offset, size, false, musicFolders);
            case "byGenre" -> mediaFileService.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
            case "byYear" -> mediaFileService.getAlbumsByYear(
                    offset, size, getRequiredIntParameter(request, "fromYear"),
                    getRequiredIntParameter(request, "toYear"), musicFolders
            );
            case "random" -> searchService.getRandomAlbums(size, musicFolders);
            default -> throw new Exception("Invalid list type: " + type);
        };

        AlbumList result = new AlbumList();
        for (MediaFile album : albums) {
            result.getAlbum().add(createJaxbChild(player, album, username));
        }

        Response res = createResponse();
        res.setAlbumList(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getAlbumList2")
    public void getAlbumList2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        size = Math.max(0, Math.min(size, 500));
        String type = getRequiredStringParameter(request, "type");
        String username = securityService.getCurrentUsername(request);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        List<Album> albums = switch (type) {
            case "frequent" -> albumDao.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
            case "recent" -> albumDao.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
            case "newest" -> albumDao.getNewestAlbums(offset, size, musicFolders);
            case "alphabeticalByArtist" -> albumDao.getAlphabeticalAlbums(offset, size, true, false, musicFolders);
            case "alphabeticalByName" -> albumDao.getAlphabeticalAlbums(offset, size, false, false, musicFolders);
            case "byGenre" -> albumDao.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
            case "byYear" -> albumDao.getAlbumsByYear(
                    offset, size, getRequiredIntParameter(request, "fromYear"),
                    getRequiredIntParameter(request, "toYear"), musicFolders
            );
            case "starred" -> albumDao.getStarredAlbums(offset, size, securityService.getCurrentUser(request).getUsername(), musicFolders);
            case "random" -> searchService.getRandomAlbumsId3(size, musicFolders);
            default -> throw new Exception("Invalid list type: " + type);
        };
        AlbumList2 result = new AlbumList2();
        for (Album album : albums) {
            result.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, username));
        }
        Response res = createResponse();
        res.setAlbumList2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private <T extends AlbumID3> T createJaxbAlbum(T target, Album album, String username) {
        return responseMapper.album(target, album, username);
    }
    private Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return responseMapper.child(player, mediaFile, username);
    }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }
    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : String.valueOf(playerId);
                if ("id".equals(name)) return mapId(request.getParameter("id"));
                return super.getParameter(name);
            }
        };
    }
    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) return id;
        try { return String.valueOf(mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).getId()); }
        catch (Exception e) { return id; }
    }
}
