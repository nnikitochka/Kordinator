package org.airsonic.player.controller.rest.stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.airsonic.player.controller.StreamController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import java.util.Optional;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class StreamRestController {
    private final StreamController streamController;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter writer = new JAXBWriter();

    public StreamRestController(StreamController streamController, SecurityService securityService,
            MediaFileService mediaFileService, RestPlayerResolver playerResolver) {
        this.streamController = streamController;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.playerResolver = playerResolver;
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest request, APIException exception) {
        var result = writer.serializeForType(request.getRequest(), writer.createErrorResponse(exception));
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(result.getKey()))
                .body(result.getValue());
    }

    @RequestMapping("/stream")
    public ResponseEntity<Resource> stream(Authentication authentication,
            @RequestParam(required = false) String format, @RequestParam(required = false) String suffix,
            @RequestParam Optional<Integer> maxBitRate, @RequestParam Optional<Integer> id,
            @RequestParam Optional<String> path, @RequestParam(required = false) Double offsetSeconds,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = swr.getRequest();
        Integer playerId = playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"),
                request.getRemoteAddr(), false);
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override public String getParameter(String name) {
                if ("player".equals(name)) return playerId == null ? null : playerId.toString();
                if ("id".equals(name)) return mapId(super.getParameter(name));
                return super.getParameter(name);
            }
        };
        User user = securityService.getUserByName(authentication.getName());
        if (!user.isStreamRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
        }
        return streamController.handleRequest(authentication, format, suffix, maxBitRate, id, path,
                offsetSeconds, new ServletWebRequest(wrapped, swr.getResponse()));
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
