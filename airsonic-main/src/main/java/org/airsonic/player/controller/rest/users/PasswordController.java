package org.airsonic.player.controller.rest.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.UserSettingsController;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.service.*;
import org.airsonic.player.util.Util;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.*;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Users;
import java.util.*;
import static org.springframework.web.bind.ServletRequestUtils.*;
import static org.airsonic.player.security.RESTRequestParameterProcessingFilter.decrypt;

@Controller("restPasswordController")
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class PasswordController {
    private final SecurityService securityService;
    private final RestPlayerResolver playerResolver;
    private final JAXBWriter jaxbWriter;
    public PasswordController(SecurityService securityService, RestPlayerResolver playerResolver, JAXBWriter jaxbWriter) {
        this.securityService = securityService; this.playerResolver = playerResolver; this.jaxbWriter = jaxbWriter;
    }
    @RequestMapping("/changePassword")
    public void changePassword(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = getRequiredStringParameter(request, "username");
        String password = decrypt(getRequiredStringParameter(request, "password"));

        org.airsonic.player.domain.User authUser = securityService.getCurrentUser(request);

        boolean allowed = authUser.isAdminRole()
                || username.equals(authUser.getUsername()) && authUser.isSettingsRole();

        if (!allowed) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, authUser.getUsername() + " is not authorized to change password for " + username);
            return;
        }

        org.airsonic.player.domain.User user = securityService.getUserByName(username);
        UserCredential uc = new UserCredential(user.getUsername(), user.getUsername(), password, securityService.getPreferredPasswordEncoder(true), App.AIRSONIC, "Created via Subsonic REST API");
        securityService.createCredential(uc);

        writeEmptyResponse(request, response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void missing(HttpServletRequest request, HttpServletResponse response, MissingServletRequestParameterException e) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + e.getParameterName() + ") is missing");
    }
    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
        return request;
    }
    private Response createResponse() { return jaxbWriter.createResponse(true); }
    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) { jaxbWriter.writeResponse(request, response, createResponse()); }
    private void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) { jaxbWriter.writeErrorResponse(request, response, code, message); }
}
