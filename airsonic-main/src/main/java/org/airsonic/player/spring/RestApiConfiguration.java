package org.airsonic.player.spring;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.controller.rest.SubsonicResponseMapper;
import org.airsonic.player.controller.rest.artists.ArtistsController;
import org.airsonic.player.controller.rest.genres.GenresController;
import org.airsonic.player.controller.rest.genres.GenresService;
import org.airsonic.player.controller.rest.musicfolders.MusicFoldersController;
import org.airsonic.player.controller.rest.musicfolders.MusicFoldersService;
import org.airsonic.player.controller.rest.search.SearchController;
import org.airsonic.player.controller.rest.search.SearchService;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.RatingService;
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
    public GenresService subsonicGenresService(MediaFileDao mediaFileDao) {
        return new GenresService(() -> mediaFileDao.getGenres(false));
    }

    @Bean
    public GenresController subsonicGenresController(
            GenresService genresService, JAXBWriter subsonicJaxbWriter) {
        return new GenresController(genresService, subsonicJaxbWriter);
    }

    @Bean
    public ArtistsController subsonicArtistsController(
            ArtistDao artistDao, MusicIndexService musicIndexService, SettingsService settingsService,
            MediaFolderService mediaFolderService, SubsonicResponseMapper subsonicResponseMapper,
            JAXBWriter subsonicJaxbWriter) {
        return new ArtistsController(artistDao, musicIndexService, settingsService, mediaFolderService,
                subsonicResponseMapper, subsonicJaxbWriter);
    }

    @Bean
    public MusicFoldersService subsonicMusicFoldersService(MediaFolderService mediaFolderService) {
        return new MusicFoldersService(mediaFolderService::getMusicFoldersForUser);
    }

    @Bean
    public MusicFoldersController subsonicMusicFoldersController(
            MusicFoldersService musicFoldersService, JAXBWriter subsonicJaxbWriter) {
        return new MusicFoldersController(musicFoldersService, subsonicJaxbWriter);
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
    public SearchController subsonicSearchController(SearchService searchService,
                                                     JAXBWriter subsonicJaxbWriter) {
        return new SearchController(searchService, subsonicJaxbWriter);
    }

    @Bean
    public SearchService subsonicSearchService(org.airsonic.player.service.SearchService searchService,
                                               MediaFolderService mediaFolderService,
                                               SubsonicResponseMapper subsonicResponseMapper,
                                               JAXBWriter subsonicJaxbWriter) {
        return new SearchService(searchService, mediaFolderService, subsonicResponseMapper, subsonicJaxbWriter);
    }
}
