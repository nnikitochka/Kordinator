package org.airsonic.player.controller.rest.download;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.airsonic.player.controller.DownloadController;
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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class DownloadRestController {
    private final RestPlayerResolver resolver;
    private final MediaFileService mediaFileService;
    private final SecurityService securityService;
    private final DownloadController downloadController;
    private final JAXBWriter writer;

    public DownloadRestController(RestPlayerResolver resolver, MediaFileService mediaFileService, SecurityService securityService,
            DownloadController downloadController, JAXBWriter writer) {
        this.resolver = resolver;
        this.mediaFileService = mediaFileService;
        this.securityService = securityService;
        this.downloadController = downloadController;
        this.writer = writer;
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest request, APIException exception) {
        Map.Entry<String, String> result = writer.serializeForType(request.getRequest(), writer.createErrorResponse(exception));
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(result.getKey())).body(result.getValue());
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) return id;
        try {
            return String.valueOf(mediaFileService.getMediaFile(StringUtil.utf8HexDecode(id)).getId());
        } catch (Exception e) {
            return id;
        }
    }

    @RequestMapping("/download")
    public ResponseEntity<Resource> download(Principal p,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) Integer playlist,
            @RequestParam(required = false) Integer player,
            @RequestParam(required = false, name = "i") List<Integer> indices,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest original = swr.getRequest();
        Integer resolvedPlayerId = resolver.getOrCreatePlayerId(original.getRemoteUser(), original.getParameter("c"), original.getRemoteAddr(), false);
        HttpServletRequest request = new HttpServletRequestWrapper(original) {
            @Override public String getParameter(String name) {
                if ("player".equals(name)) return resolvedPlayerId == null ? null : resolvedPlayerId.toString();
                if ("id".equals(name)) return mapId(super.getParameter(name));
                return super.getParameter(name);
            }
        };
        final Integer playerId = Optional.ofNullable(request.getParameter("player")).map(Integer::valueOf).orElse(null);
        Optional<Integer> idInt = Optional.ofNullable(id).map(this::mapId).filter(StringUtils::isNumeric).map(Integer::valueOf);

        User user = securityService.getUserByName(p.getName());
        if (!user.isDownloadRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to download files.");
        }
        return downloadController.handleRequest(p, idInt, playlist, playerId, indices,
                new ServletWebRequest(request, swr.getResponse()));
    }

}
