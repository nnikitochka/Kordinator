package org.airsonic.player.service.metadata;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFolderService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses meta data by guessing artist, album and song title based on the path of the file.
 *
 * @author Sindre Mehus
 */
@Service
public class DefaultMetaDataParser extends MetaDataParser {

    private MediaFolderService mediaFolderService;

    public DefaultMetaDataParser(MediaFolderService mediaFolderService) {
        this.mediaFolderService = mediaFolderService;
    }

    /**
     * Parses meta data for the given file.
     *
     * @param file The file to parse.
     * @return Meta data for the file.
     */
    @Override
    public MetaData getRawMetaData(Path file) {
        MetaData metaData = new MetaData();
        String artist = guessArtist(file);
        metaData.setArtist(artist);
        metaData.setAlbumArtist(artist);
        metaData.setAlbumName(guessAlbum(file, artist));
        metaData.setTitle(guessTitle(file));
        return metaData;
    }

    /**
     * Updates the given file with the given meta data.
     * This method has no effect.
     *
     * @param file     The file to update.
     * @param metaData The new meta data.
     */
    @Override
    public void setMetaData(MediaFile file, MetaData metaData) {
    }

    /**
     * Returns whether this parser supports tag editing (using the {@link #setMetaData} method).
     *
     * @return Always false.
     */
    @Override
    public boolean isEditingSupported() {
        return false;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    MediaFolderService getMediaFolderService() {
        return mediaFolderService;
    }

    /**
     * Returns whether this parser is applicable to the given file.
     *
     * @param path The path to file in question.
     * @return Whether this parser is applicable to the given file.
     */
    @Override
    public boolean isApplicable(Path path) {
        return Files.isRegularFile(path);
    }
}