/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.spring;

import org.springframework.core.Ordered;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Preserves the legacy Subsonic {@code .view} endpoint suffix.
 *
 * <p>Spring Framework 6 removed suffix-pattern matching, while Airsonic's UI and
 * Subsonic clients still use URLs such as {@code /musicFolderSettings.view} and
 * {@code /rest/ping.view}. This mapping forwards the request internally to its
 * unsuffixed counterpart before resource handling can turn it into a 404.</p>
 */
public class LegacyViewSuffixHandlerMapping extends AbstractHandlerMapping {

    private static final String VIEW_SUFFIX = ".view";

    private static final HttpRequestHandler HANDLER = new LegacyViewSuffixHandler();

    public LegacyViewSuffixHandlerMapping() {
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = getRequestUri(request).substring(contextPath.length());
        return path.endsWith(VIEW_SUFFIX) ? HANDLER : null;
    }

    private static String getRequestUri(HttpServletRequest request) {
        Object includeRequestUri = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);
        return includeRequestUri == null ? request.getRequestURI() : includeRequestUri.toString();
    }

    private static class LegacyViewSuffixHandler implements HttpRequestHandler {

        @Override
        public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, jakarta.servlet.ServletException {
            boolean included = request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null;
            String path = getRequestUri(request).substring(request.getContextPath().length());
            String target = path.substring(0, path.length() - VIEW_SUFFIX.length());
            RequestDispatcher dispatcher = request.getRequestDispatcher(target);
            if (included) {
                dispatcher.include(request, response);
            } else {
                dispatcher.forward(request, response);
            }
        }
    }
}
