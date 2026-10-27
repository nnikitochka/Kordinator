package org.airsonic.player.controller.cover.request;

import org.airsonic.player.controller.cover.CoverArtController;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

public class PlaylistCoverArtRequest extends CoverArtRequest {
    private final CoverArtController coverArtController;
    private final Playlist playlist;

    public PlaylistCoverArtRequest(CoverArtController coverArtController, Playlist playlist) {
        super(coverArtController, null, () -> CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.getId(), playlist::getChanged);
        this.coverArtController = coverArtController;
        this.playlist = playlist;
    }

    @Override
    public String getAlbum() {
        return null;
    }

    @Override
    public String getArtist() {
        return playlist.getName();
    }

    @Override
    public String toString() {
        return "Playlist " + playlist.getId() + " - " + playlist.getName();
    }

    @Override
    public BufferedImage createImage(int size) {
        List<MediaFile> albums = getRepresentativeAlbums();
        if (albums.isEmpty()) {
            return createAutoCover(size, size);
        }
        if (albums.size() < 4) {
            return new MediaFileCoverArtRequest(coverArtController, albums.getFirst()).createImage(size);
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        int half = size / 2;
        graphics.drawImage(new MediaFileCoverArtRequest(coverArtController, albums.get(0)).createImage(half), null, 0, 0);
        graphics.drawImage(new MediaFileCoverArtRequest(coverArtController, albums.get(1)).createImage(half), null, half, 0);
        graphics.drawImage(new MediaFileCoverArtRequest(coverArtController, albums.get(2)).createImage(half), null, 0, half);
        graphics.drawImage(new MediaFileCoverArtRequest(coverArtController, albums.get(3)).createImage(half), null, half, half);
        graphics.dispose();
        return image;
    }

    private List<MediaFile> getRepresentativeAlbums() {
        return coverArtController.playlistService.getFilesInPlaylist(playlist.getId())
                .parallelStream()
                .map(coverArtController.mediaFileService::getParentOf)
                .filter(album -> album != null && !coverArtController.mediaFileService.isRoot(album))
                .distinct()
                .collect(Collectors.toList());
    }
}
