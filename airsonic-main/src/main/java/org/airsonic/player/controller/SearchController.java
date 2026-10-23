package org.airsonic.player.controller;

import org.airsonic.player.command.SearchCommand;
import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.search.IndexType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

/**
 * Controller for the search page.
 */
@Controller
@RequestMapping("/search")
public class SearchController {
    private final SecurityService securityService;
    private final SettingsService settingsService;
    private final PlayerService playerService;
    private final SearchService searchService;
    private final MediaFolderService mediaFolderService;
    private final MediaFileService mediaFileService;
    private final AlbumDao albumDao;

    public SearchController(SecurityService securityService, SettingsService settingsService, PlayerService playerService, SearchService searchService, MediaFolderService mediaFolderService, MediaFileService mediaFileService, AlbumDao albumDao) {
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.playerService = playerService;
        this.searchService = searchService;
        this.mediaFolderService = mediaFolderService;
        this.mediaFileService = mediaFileService;
        this.albumDao = albumDao;
    }

    @GetMapping
    protected String displayForm() {
        return "search";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) {
        model.addAttribute("command",new SearchCommand());
    }

    @PostMapping
    protected String onSubmit(HttpServletRequest request, HttpServletResponse response,@ModelAttribute("command") SearchCommand command, Model model) throws Exception {

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = settingsService.getUserSettings(user.getUsername());
        command.setUser(user);
        command.setPartyModeEnabled(userSettings.getPartyModeEnabled());

        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(user.getUsername());
        String query = StringUtils.trimToNull(command.getQuery());

        if (query != null) {

            SearchCriteria criteria = new SearchCriteria();
            criteria.setCount(userSettings.getSearchCount());
            criteria.setQuery(query);

            SearchResult artists = searchService.search(criteria, musicFolders, IndexType.ARTIST);
            SearchResult artistsId3 = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
            artistsId3.getArtists().stream().map(Artist::getName).flatMap(ar ->
                    albumDao.getAlbumsForArtist(ar, musicFolders).stream().map(al ->
                                    mediaFileService.getMediaFile(al.getPath(), al.getFolderId())
                            )
                            .filter(Objects::nonNull)
                            .map(MediaFile::getId)
                            .map(m -> Pair.of(ar, m))
            ).collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toSet())));

            artists.getMediaFiles().stream().map(file ->
                    Pair.of(
                            Optional.ofNullable(file.getArtist()).or(() -> Optional.ofNullable(file.getAlbumArtist())).orElse("(Unknown)"),
                            file.getId()
                    )
            );

            command.setArtists(Stream.concat(
                    artistsId3.getArtists().stream()
                        .map(Artist::getName)
                        .flatMap(ar -> albumDao.getAlbumsForArtist(ar, musicFolders).stream()
                                .map(al -> mediaFileService.getMediaFile(al.getPath(), al.getFolderId()))
                                    .filter(Objects::nonNull)
                                .map(MediaFile::getId)
                                .map(m -> Pair.of(ar, m))),
                    artists.getMediaFiles().stream()
                        .map(m -> Pair.of(
                                Optional.ofNullable(m.getArtist())
                                    .or(() -> Optional.ofNullable(m.getAlbumArtist()))
                                    .orElse("(Unknown)"),
                                m.getId())))
                .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toSet()))));

            SearchResult albums = searchService.search(criteria, musicFolders, IndexType.ALBUM);
            SearchResult albumsId3 = searchService.search(criteria, musicFolders, IndexType.ALBUM_ID3);
            command.setAlbums(Stream
                    .concat(albumsId3.getAlbums().stream()
                            .map(a -> Optional.ofNullable(mediaFileService.getMediaFile(a.getPath(), a.getFolderId()))
                                    .map(m -> Pair.of(Pair.of(a.getName(), a.getArtist()), m.getId())).orElse(null))
                            .filter(Objects::nonNull),
                            albums.getMediaFiles().stream()
                                    .map(m -> Pair.of(Pair.of(m.getAlbumName(), m.getAlbumArtist()), m.getId())))
                    .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toSet()))));

            SearchResult songs = searchService.search(criteria, musicFolders, IndexType.SONG);
            command.setSongs(songs.getMediaFiles());

            command.setPlayer(playerService.getPlayer(request, response));
        }

        return "search";
    }
}
