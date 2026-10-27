package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for updating music file metadata.
 */
@Controller
@RequestMapping("/setMusicFileInfo")
public class SetMusicFileInfoController {
    private final MediaFileService mediaFileService;

    public SetMusicFileInfoController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    @PostMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) throws Exception {
        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        String action = request.getParameter("action");

        MediaFile mediaFile = mediaFileService.getMediaFile(id);

        if ("comment".equals(action)) {
            mediaFile.setComment(StringEscapeUtils.escapeHtml(request.getParameter("comment")));
            mediaFileService.updateMediaFile(mediaFile);
        }

        String url = "main.view?id=" + id;
        return new ModelAndView(new RedirectView(url));
    }
}
