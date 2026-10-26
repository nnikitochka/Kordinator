package org.airsonic.player.spring;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.search.RestPlayerResolver;
import org.airsonic.player.controller.rest.search.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.search.SubsonicSearchService;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.TranscodingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The temporary Spring composition root for REST components that can later be reused by Ktor. */
@Configuration
public class RestApiConfiguration {

    @Bean
    public JAXBWriter subsonicJaxbWriter() {
        return new JAXBWriter();
    }

    @Bean
    public RestPlayerResolver restPlayerResolver(PlayerService playerService) {
        return new RestPlayerResolver(playerService);
    }

    @Bean
    public SubsonicResponseMapper subsonicResponseMapper(
            MediaFileService mediaFileService, MediaFileDao mediaFileDao, RatingService ratingService,
            CoverArtService coverArtService, TranscodingService transcodingService, AlbumDao albumDao,
            ArtistDao artistDao, JAXBWriter subsonicJaxbWriter) {
        return new SubsonicResponseMapper(mediaFileService, mediaFileDao, ratingService, coverArtService,
                transcodingService, albumDao, artistDao, subsonicJaxbWriter);
    }

    @Bean
    public SubsonicSearchService subsonicSearchService(SearchService searchService,
                                                        MediaFolderService mediaFolderService,
                                                        SubsonicResponseMapper subsonicResponseMapper,
                                                        JAXBWriter subsonicJaxbWriter) {
        return new SubsonicSearchService(searchService, mediaFolderService, subsonicResponseMapper, subsonicJaxbWriter);
    }
}
