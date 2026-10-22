package org.airsonic.player.ajax.artist;

import org.airsonic.player.ajax.media.MediaFileEntry;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@MessageMapping("/artist")
public class ArtistWSController {
    private final MediaFileService mediaFileService;
    private final LastFmService lastFmService;
    private final MediaFolderService mediaFolderService;
    private final LocaleResolver localeResolver;

    public ArtistWSController(
            MediaFileService mediaFileService,
            LastFmService lastFmService,
            MediaFolderService mediaFolderService,
            LocaleResolver localeResolver
    ) {
        this.mediaFileService = mediaFileService;
        this.lastFmService = lastFmService;
        this.mediaFolderService = mediaFolderService;
        this.localeResolver = localeResolver;
    }

    @MessageMapping("/info")
    @SendToUser(broadcast = false)
    public ArtistInfo getArtistInfo(Principal principal, ArtistInfoRequest req) {
        MediaFile mediaFile = mediaFileService.getMediaFile(req.getMediaFileId());
        List<SimilarArtist> similarArtists = getSimilarArtists(principal.getName(), req.getMediaFileId(), req.getMaxSimilarArtists());
        ArtistBio artistBio = lastFmService.getArtistBio(mediaFile, localeResolver.resolveLocale(principal.getName()));
        List<MediaFileEntry> topSongs = getTopSongs(principal.getName(), mediaFile, req.getMaxTopSongs());

        return new ArtistInfo(similarArtists, artistBio, topSongs);
    }

    private List<MediaFileEntry> getTopSongs(String username, MediaFile mediaFile, int limit) {
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        return mediaFileService.toMediaFileEntryList(lastFmService.getTopSongs(mediaFile, limit, musicFolders), username, true, true, null, null, null);
    }

    private List<SimilarArtist> getSimilarArtists(String username, int mediaFileId, int limit) {
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        MediaFile artist = mediaFileService.getMediaFile(mediaFileId);
        return lastFmService.getSimilarArtists(artist, limit, false, musicFolders).parallelStream()
                .map(similarArtist -> new SimilarArtist(similarArtist.getId(), similarArtist.getName()))
                .collect(Collectors.toList());
    }
}
