package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.musicfolders.SubsonicMusicFoldersController;
import org.airsonic.player.controller.rest.search.SubsonicSearchController;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects Ktor to the existing servlet container during the migration. */
@Configuration
public class KtorConfiguration {

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration(
            SubsonicSearchController searchController,
            SubsonicMusicFoldersController musicFoldersController,
            RestPlayerResolver playerResolver,
            PlayerService playerService,
            SecurityService securityService
    ) {
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(
                        new AirsonicKtorServlet(searchController, musicFoldersController, playerResolver,
                                playerService, securityService),
                        "/internal/ktor/*");
        registration.addUrlMappings(SubsonicSearchController.PATHS);
        registration.addUrlMappings(SubsonicMusicFoldersController.PATHS);
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
