package org.airsonic.player.controller.rest.starred;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.*;
import java.util.List;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller("restStarredController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class StarredController {
    private final MediaFileDao mediaFileDao;
    private final AlbumDao albumDao;
    private final ArtistDao artistDao;
    private final MediaFolderService mediaFolderService;
    private final MediaFileService mediaFileService;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper responseMapper;
    private final JAXBWriter jaxbWriter;

    public StarredController(MediaFileDao mediaFileDao, AlbumDao albumDao, ArtistDao artistDao,
                             MediaFolderService mediaFolderService, MediaFileService mediaFileService,
                             PlayerService playerService, SecurityService securityService, RestPlayerResolver playerResolver,
                             SubsonicResponseMapper responseMapper, JAXBWriter jaxbWriter) {
        this.mediaFileDao = mediaFileDao; this.albumDao = albumDao; this.artistDao = artistDao;
        this.mediaFolderService = mediaFolderService; this.mediaFileService = mediaFileService;
        this.playerService = playerService; this.securityService = securityService; this.playerResolver = playerResolver;
        this.responseMapper = responseMapper; this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/star")
    public void star(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, true);
    }

    @RequestMapping("/unstar")
    public void unstar(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, false);
    }

    private void starOrUnstar(HttpServletRequest request, HttpServletResponse response, boolean star) {
        request = wrapRequest(request);

        String username = securityService.getCurrentUser(request).getUsername();
        for (int id : getIntParameters(request, "id")) {
            MediaFile mediaFile = mediaFileDao.getMediaFile(id);
            if (mediaFile == null) {
                error(request, response, ErrorCode.NOT_FOUND, "Media file not found: " + id);
                return;
            }
            if (star) {
                mediaFileDao.starMediaFile(id, username);
            } else {
                mediaFileDao.unstarMediaFile(id, username);
            }
        }
        for (int albumId : getIntParameters(request, "albumId")) {
            Album album = albumDao.getAlbum(albumId);
            if (album == null) {
                error(request, response, ErrorCode.NOT_FOUND, "Album not found: " + albumId);
                return;
            }
            if (star) {
                albumDao.starAlbum(albumId, username);
            } else {
                albumDao.unstarAlbum(albumId, username);
            }
        }
        for (int artistId : getIntParameters(request, "artistId")) {
            org.airsonic.player.domain.Artist artist = artistDao.getArtist(artistId);
            if (artist == null) {
                error(request, response, ErrorCode.NOT_FOUND, "Artist not found: " + artistId);
                return;
            }
            if (star) {
                artistDao.starArtist(artistId, username);
            } else {
                artistDao.unstarArtist(artistId, username);
            }
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping("/getStarred")
    public void getStarred(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred result = new Starred();
        for (MediaFile artist : mediaFileDao.getStarredDirectories(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(createJaxbArtist(artist, username));
        }
        for (MediaFile album : mediaFileDao.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(createJaxbChild(player, album, username));
        }
        for (MediaFile song : mediaFileDao.getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping("/getStarred2")
    public void getStarred2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Player player = playerService.getPlayer(request, response);
        String username = securityService.getCurrentUsername(request);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred2 result = new Starred2();
        for (org.airsonic.player.domain.Artist artist : artistDao.getStarredArtists(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(createJaxbArtist(new ArtistID3(), artist, username));
        }
        for (Album album : albumDao.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(createJaxbAlbum(new AlbumID3(), album, username));
        }
        for (MediaFile song : mediaFileDao.getStarredFiles(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : String.valueOf(playerId);
                if ("id".equals(name)) {
                    String id = request.getParameter("id");
                    if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                            id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) return id;
                    try { return String.valueOf(mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).getId()); }
                    catch (Exception e) { return id; }
                }
                return super.getParameter(name);
            }
        };
    }
    private org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) { return responseMapper.artist(artist, username); }
    private <T extends ArtistID3> T createJaxbArtist(T artist, org.airsonic.player.domain.Artist source, String username) { return responseMapper.artist(artist, source, username); }
    private <T extends AlbumID3> T createJaxbAlbum(T album, Album source, String username) { return responseMapper.album(album, source, username); }
    private Child createJaxbChild(Player player, MediaFile file, String username) { return responseMapper.child(player, file, username); }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) { jaxbWriter.writeResponse(request, response, createResponse()); }
    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) { jaxbWriter.writeErrorResponse(request, response, code, message); }
}
