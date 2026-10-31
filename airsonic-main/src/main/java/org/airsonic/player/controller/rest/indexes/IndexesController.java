package org.airsonic.player.controller.rest.indexes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.LeftController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolderContent;
import org.airsonic.player.domain.MusicIndex;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Index;
import org.subsonic.restapi.Indexes;
import org.subsonic.restapi.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.springframework.web.bind.ServletRequestUtils.*;

@Controller("restIndexesController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class IndexesController {
    private final SecurityService securityService;
    private final SettingsService settingsService;
    private final MusicIndexService musicIndexService;
    private final MediaFolderService mediaFolderService;
    private final MediaFileDao mediaFileDao;
    private final RatingService ratingService;
    private final LeftController leftController;
    private final PlayerService playerService;
    private final RestPlayerResolver playerResolver;
    private final SubsonicResponseMapper responseMapper;
    private final JAXBWriter jaxbWriter;
    public IndexesController(SecurityService securityService, SettingsService settingsService, MusicIndexService musicIndexService,
                             MediaFolderService mediaFolderService, MediaFileDao mediaFileDao, RatingService ratingService,
                             LeftController leftController, PlayerService playerService, RestPlayerResolver playerResolver,
                             SubsonicResponseMapper responseMapper, JAXBWriter jaxbWriter) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.musicIndexService = musicIndexService;
        this.mediaFolderService = mediaFolderService;
        this.mediaFileDao = mediaFileDao;
        this.ratingService = ratingService;
        this.leftController = leftController;
        this.playerService = playerService;
        this.playerResolver = playerResolver;
        this.responseMapper = responseMapper;
        this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/getIndexes")
    public void getIndexes(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Response res = createResponse();
        String username = securityService.getCurrentUser(request).getUsername();

        long ifModifiedSince = getLongParameter(request, "ifModifiedSince", 0L);
        long lastModified = leftController.getLastModified(request);

        if (lastModified <= ifModifiedSince) {
            jaxbWriter.writeResponse(request, response, res);
            return;
        }

        Indexes indexes = new Indexes();
        indexes.setLastModified(lastModified);
        indexes.setIgnoredArticles(settingsService.getIgnoredArticles());

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        if (musicFolderId != null) {
            for (org.airsonic.player.domain.MusicFolder musicFolder : musicFolders) {
                if (musicFolderId.equals(musicFolder.getId())) {
                    musicFolders = Collections.singletonList(musicFolder);
                    break;
                }
            }
        }

        for (MediaFile shortcut : musicIndexService.getShortcuts(musicFolders)) {
            indexes.getShortcut().add(createJaxbArtist(shortcut, username));
        }

        MusicFolderContent musicFolderContent = musicIndexService.getMusicFolderContent(musicFolders, false);

        for (Map.Entry<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> entry : musicFolderContent.getIndexedArtists().entrySet()) {
            Index index = new Index();
            indexes.getIndex().add(index);
            index.setName(entry.getKey().getIndex());

            for (MusicIndex.SortableArtistWithMediaFiles artist : entry.getValue()) {
                for (MediaFile mediaFile : artist.getMediaFiles()) {
                    if (mediaFile.isDirectory()) {
                        Instant starredDate = mediaFileDao.getMediaFileStarredDate(mediaFile.getId(), username);
                        org.subsonic.restapi.Artist a = new org.subsonic.restapi.Artist();
                        index.getArtist().add(a);
                        a.setId(String.valueOf(mediaFile.getId()));
                        a.setName(artist.getName());
                        a.setStarred(jaxbWriter.convertDate(starredDate));

                        if (mediaFile.isAlbum()) {
                            a.setAverageRating(ratingService.getAverageRating(mediaFile));
                            a.setUserRating(ratingService.getRatingForUser(username, mediaFile));
                        }
                    }
                }
            }
        }

        // Add children
        Player player = playerService.getPlayer(request, response);

        for (MediaFile singleSong : musicFolderContent.getSingleSongs()) {
            indexes.getChild().add(createJaxbChild(player, singleSong, username));
        }

        res.setIndexes(indexes);
        jaxbWriter.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        return new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                return "player".equals(name) ? (playerId == null ? null : playerId.toString()) : super.getParameter(name);
            }
        };
    }
    private org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) { return responseMapper.artist(artist, username); }
    private org.subsonic.restapi.Child createJaxbChild(Player player, MediaFile file, String username) { return responseMapper.child(player, file, username); }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
}
