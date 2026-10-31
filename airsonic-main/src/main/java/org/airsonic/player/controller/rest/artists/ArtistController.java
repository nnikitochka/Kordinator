package org.airsonic.player.controller.rest.artists;

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
import org.airsonic.player.domain.Album;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistWithAlbumsID3;
import org.subsonic.restapi.Response;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class ArtistController {
    private final ArtistDao artists;
    private final AlbumDao albums;
    private final MediaFolderService folders;
    private final MediaFileService mediaFiles;
    private final SecurityService security;
    private final RestPlayerResolver players;
    private final SubsonicResponseMapper mapper;
    private final JAXBWriter writer;

    public ArtistController(ArtistDao artists, AlbumDao albums, MediaFolderService folders, MediaFileService mediaFiles,
                            SecurityService security, RestPlayerResolver players, SubsonicResponseMapper mapper, JAXBWriter writer) {
        this.artists = artists; this.albums = albums; this.folders = folders; this.mediaFiles = mediaFiles;
        this.security = security; this.players = players; this.mapper = mapper; this.writer = writer;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        writer.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + e.getParameterName() + ") is missing");
    }

    @RequestMapping("/getArtist")
    public void getArtist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        players.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        request = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                String value = super.getParameter(name);
                if (!"id".equals(name) || value == null || value.startsWith(CoverArtController.ALBUM_COVERART_PREFIX)
                        || value.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(value)) return value;
                try { return String.valueOf(mediaFiles.getMediaFile(StringUtil.utf8HexDecode(value)).getId()); }
                catch (Exception e) { return value; }
            }
        };
        String username = security.getCurrentUsername(request);
        int id = getRequiredIntParameter(request, "id");
        org.airsonic.player.domain.Artist artist = artists.getArtist(id);
        if (artist == null) {
            writer.writeErrorResponse(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }
        ArtistWithAlbumsID3 result = mapper.artist(new ArtistWithAlbumsID3(), artist, username);
        for (Album album : albums.getAlbumsForArtist(artist.getName(), folders.getMusicFoldersForUser(username))) {
            result.getAlbum().add(mapper.album(new AlbumID3(), album, username));
        }
        Response resultResponse = writer.createResponse(true);
        resultResponse.setArtist(result);
        writer.writeResponse(request, response, resultResponse);
    }
}
