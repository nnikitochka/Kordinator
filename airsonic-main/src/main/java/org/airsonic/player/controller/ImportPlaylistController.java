package org.airsonic.player.controller;

import org.airsonic.player.domain.Playlist;
import org.airsonic.player.service.PlaylistService;
import org.airsonic.player.service.SecurityService;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.jakarta.JakartaServletDiskFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/importPlaylist")
public class ImportPlaylistController {

    private static final long MAX_PLAYLIST_SIZE_MB = 5L;

    private final SecurityService securityService;
    private final PlaylistService playlistService;

    public ImportPlaylistController(SecurityService securityService, PlaylistService playlistService) {
        this.securityService = securityService;
        this.playlistService = playlistService;
    }

    @PostMapping
    protected String handlePost(
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        Map<String, Object> map = new HashMap<>();

        try {
            if (JakartaServletDiskFileUpload.isMultipartContent(request)) {

                JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload();
                List<DiskFileItem> items = upload.parseRequest(request);
                for (DiskFileItem item : items) {

                    if ("file".equals(item.getFieldName()) && !StringUtils.isBlank(item.getName())) {
                        if (item.getSize() > MAX_PLAYLIST_SIZE_MB * 1024L * 1024L) {
                            throw new Exception("The playlist file is too large. Max file size is " + MAX_PLAYLIST_SIZE_MB + " MB.");
                        }
                        String playlistName = FilenameUtils.getBaseName(item.getName());
                        String fileName = FilenameUtils.getName(item.getName());
                        String username = securityService.getCurrentUsername(request);
                        Playlist playlist = playlistService.importPlaylist(username, playlistName, fileName, null, item.getInputStream(), null);
                        map.put("playlist", playlist);
                    }
                }
            }
        } catch (Exception e) {
            map.put("error", e.getMessage());
        }

        redirectAttributes.addFlashAttribute("model", map);
        return "redirect:importPlaylist";
    }

    @GetMapping
    public String handleGet() {
        return "importPlaylist";
    }

}
