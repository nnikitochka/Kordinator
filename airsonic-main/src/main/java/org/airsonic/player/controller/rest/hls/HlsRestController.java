package org.airsonic.player.controller.rest.hls;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.HLSController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class HlsRestController {
    private final HLSController hlsController;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter writer = new JAXBWriter();

    public HlsRestController(HLSController hlsController, SecurityService securityService,
            MediaFileService mediaFileService, RestPlayerResolver playerResolver) {
        this.hlsController = hlsController;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.playerResolver = playerResolver;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missingParameter(HttpServletRequest request, HttpServletResponse response,
            MissingServletRequestParameterException exception) {
        writer.writeErrorResponse(request, response, ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping("/hls")
    public void hls(Authentication authentication, @RequestParam Integer id,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"),
                request.getRemoteAddr(), false);
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : playerId.toString();
                if ("id".equals(name)) return mapId(super.getParameter(name));
                return super.getParameter(name);
            }
        };
        User user = securityService.getCurrentUser(wrapped);
        if (!user.isStreamRole()) {
            writer.writeErrorResponse(wrapped, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to play files.");
            return;
        }
        hlsController.handleHlsRequest(authentication, id, wrapped, response);
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX)
                || id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) return id;
        try {
            return String.valueOf(mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).getId());
        } catch (Exception e) {
            return id;
        }
    }
}
