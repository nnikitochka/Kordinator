package org.airsonic.player.controller;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.StatusService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

/**
 * Controller for showing what's currently playing.
 */
@Controller
@RequestMapping("/nowPlaying")
public class NowPlayingController {
    private final PlayerService playerService;
    private final StatusService statusService;
    private final MediaFileService mediaFileService;

    public NowPlayingController(PlayerService playerService, StatusService statusService, MediaFileService mediaFileService) {
        this.playerService = playerService;
        this.statusService = statusService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Player player = playerService.getPlayer(request, response);
        TransferStatus status = statusService.getStreamStatusesForPlayer(player).stream().findFirst()
                .orElseGet(() -> statusService.getInactiveStreamStatusForPlayer(player));

        String url = Optional.ofNullable(status)
                .map(s -> {
                    if (s.getMediaFile() != null) {
                        return s.getMediaFile();
                    }
                    return mediaFileService.getMediaFile(s.getExternalFile());
                })
                .map(mediaFileService::getParentOf)
                .filter(dir -> !mediaFileService.isRoot(dir))
                .map(dir -> "main.view?id=" + dir.getId())
                .orElse("home.view");

        return new ModelAndView(new RedirectView(url));
    }
}
