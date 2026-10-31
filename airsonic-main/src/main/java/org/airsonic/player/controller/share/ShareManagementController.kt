package org.airsonic.player.controller.share;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Share;
import org.airsonic.player.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;

/**
 * Controller for sharing music on Twitter, Facebook etc.
 */
@Controller
@RequestMapping("/createShare")
public class ShareManagementController {
    private final MediaFileService mediaFileService;
    private final ShareService shareService;
    private final PlayerService playerService;
    private final PlaylistService playlistService;
    private final SecurityService securityService;

    public ShareManagementController(MediaFileService mediaFileService, ShareService shareService, PlayerService playerService, PlaylistService playlistService, SecurityService securityService) {
        this.mediaFileService = mediaFileService;
        this.shareService = shareService;
        this.playerService = playerService;
        this.playlistService = playlistService;
        this.securityService = securityService;
    }

    @GetMapping
    public ModelAndView createShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<MediaFile> files = getMediaFiles(request);
        MediaFile dir = null;
        if (!files.isEmpty()) {
            dir = files.getFirst();
            if (!dir.isAlbum()) {
                dir = mediaFileService.getParentOf(dir);
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("dir", dir);
        map.put("user", securityService.getCurrentUser(request));

        Share share = shareService.createShare(request, files);
        String description = getDescription(request);
        if (description != null) {
            share.setDescription(description);
            shareService.updateShare(share);
        }

        map.put("playUrl", shareService.getShareUrl(request, share));

        return new ModelAndView("createShare", "model", map);
    }

    private String getDescription(HttpServletRequest request) throws ServletRequestBindingException {
        Integer playlistId = ServletRequestUtils.getIntParameter(request, "playlist");
        return playlistId == null ? null : playlistService.getPlaylist(playlistId).getName();
    }

    private List<MediaFile> getMediaFiles(HttpServletRequest request) throws Exception {
        Integer id = ServletRequestUtils.getIntParameter(request, "id");
        Integer playerId = ServletRequestUtils.getIntParameter(request, "player");
        Integer playlistId = ServletRequestUtils.getIntParameter(request, "playlist");

        List<MediaFile> result = new ArrayList<>();

        if (id != null) {
            MediaFile album = mediaFileService.getMediaFile(id);
            int[] indexes = ServletRequestUtils.getIntParameters(request, "i");
            if (indexes.length == 0) {
                return Collections.singletonList(album);
            }
            List<MediaFile> children = mediaFileService.getChildrenOf(album, true, false, true);
            for (int index : indexes) {
                result.add(children.get(index));
            }
        } else if (playerId != null) {
            Player player = playerService.getPlayerById(playerId);
            PlayQueue playQueue = player.getPlayQueue();
            result = playQueue.getFiles();
        } else if (playlistId != null) {
            result = playlistService.getFilesInPlaylist(playlistId);
        }

        return result;
    }
}
