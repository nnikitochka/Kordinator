package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for changing cover art.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/changeCoverArt")
public class ChangeCoverArtController {
    private final MediaFileService mediaFileService;

    public ChangeCoverArtController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        String artist = request.getParameter("artist");
        String album = request.getParameter("album");
        MediaFile dir = mediaFileService.getMediaFile(id);

        if (StringUtils.isBlank(artist)) {
            artist = dir.getArtist();
        }
        if (StringUtils.isBlank(album)) {
            album = dir.getAlbumName();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("artist", artist);
        map.put("album", album);


        return new ModelAndView("changeCoverArt","model",map);
    }

}
