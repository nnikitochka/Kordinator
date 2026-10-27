package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.artists.ArtistsController;
import org.airsonic.player.controller.rest.genres.GenresController;
import org.airsonic.player.controller.rest.musicfolders.MusicFoldersController;
import org.airsonic.player.controller.rest.search.SearchController;
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
            SearchController searchController,
            MusicFoldersController musicFoldersController,
            GenresController genresController,
            ArtistsController artistsController,
            RestPlayerResolver playerResolver,
            PlayerService playerService,
            SecurityService securityService
    ) {
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(
                        new AirsonicKtorServlet(searchController, musicFoldersController, genresController,
                                artistsController, playerResolver, playerService, securityService),
                        "/internal/ktor/*");
        registration.addUrlMappings(SearchController.PATHS);
        registration.addUrlMappings(MusicFoldersController.PATHS);
        registration.addUrlMappings(GenresController.PATHS);
        registration.addUrlMappings(ArtistsController.PATHS);
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
