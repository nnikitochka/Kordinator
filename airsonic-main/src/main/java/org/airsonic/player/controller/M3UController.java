package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.NetworkService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.util.List;

/**
 * Controller which produces the M3U playlist.
 */
@Controller
@RequestMapping("/play.m3u")
public class M3UController {
    private final PlayerService playerService;
    private final TranscodingService transcodingService;
    private final JWTSecurityService jwtSecurityService;

    public M3UController(PlayerService playerService, TranscodingService transcodingService, JWTSecurityService jwtSecurityService) {
        this.playerService = playerService;
        this.transcodingService = transcodingService;
        this.jwtSecurityService = jwtSecurityService;
    }

    @GetMapping
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("audio/x-mpegurl");
        response.setCharacterEncoding(StringUtil.ENCODING_UTF8);

        Player player = playerService.getPlayer(request, response);

        String baseUrl = NetworkService.getBaseUrl(request);
        String prefix = "ext/stream?";

        if (player.isExternalWithPlaylist()) {
            createClientSidePlaylist(response.getWriter(), player, baseUrl, prefix);
        } else {
            createServerSidePlaylist(response.getWriter(), player, baseUrl, prefix);
        }
        return null;
    }

    private void createClientSidePlaylist(PrintWriter out, Player player, String baseUrl, String prefix) {
        if (player.getM3uBomEnabled()) {
            out.print("\ufeff");
        }
        out.println("#EXTM3U");
        List<MediaFile> result;
        synchronized (player.getPlayQueue()) {
            result = player.getPlayQueue().getFiles();
        }
        for (MediaFile mediaFile : result) {
            Double duration = mediaFile.getDuration();
            if (duration == null) {
                duration = -1.0;
            }
            out.println("#EXTINF:" + duration + "," + mediaFile.getArtist() + " - " + mediaFile.getTitle());

            String urlNoAuth = prefix + "player=" + player.getId() + "&id=" + mediaFile.getId() + "&suffix=." +
                    transcodingService.getSuffix(player, mediaFile, null);
            String urlWithAuth = jwtSecurityService.addJWTToken(player.getUsername(), urlNoAuth);
            out.println(baseUrl + urlWithAuth);
        }
    }

    private void createServerSidePlaylist(PrintWriter out, Player player, String baseUrl, String prefix) {

        String url = prefix + "player=" + player.getId();

        // Get suffix of current file, e.g., ".mp3".
        String suffix = getSuffix(player);
        if (suffix != null) {
            url += "&suffix=." + suffix;
        }

        if (player.getM3uBomEnabled()) {
            out.print("\ufeff");
        }
        out.println("#EXTM3U");
        out.println("#EXTINF:-1,Airsonic");
        out.println(baseUrl + jwtSecurityService.addJWTToken(player.getUsername(), url));
    }

    private String getSuffix(Player player) {
        PlayQueue playQueue = player.getPlayQueue();
        return playQueue.isEmpty() ? null : transcodingService.getSuffix(player, playQueue.getFile(0), null);
    }

}
