package org.airsonic.player.service;

import org.airsonic.player.ajax.tag.TagData;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TagEditService {
    private static final Logger LOG = LoggerFactory.getLogger(TagEditService.class);

    private MetaDataParserFactory metaDataParserFactory;
    private MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final SecurityService securityService;

    public TagEditService(MetaDataParserFactory metaDataParserFactory, MediaFileService mediaFileService, MediaFolderService mediaFolderService, SecurityService securityService) {
        this.metaDataParserFactory = metaDataParserFactory;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.securityService = securityService;
    }

    /**
     * Updated tags for a given music file.
     *
     * @return "UPDATED" if the new tags were updated, "SKIPPED" if no update was necessary.
     *         Otherwise, the error message is returned.
     */
    public String setTags(TagData data, String username) {
        try {
            MediaFile file = mediaFileService.getMediaFile(data.getMediaFileId());
            if (file == null) {
                throw new IllegalArgumentException("Media file not found");
            }
            if (!securityService.isFolderAccessAllowed(file, username)) {
                throw new SecurityException("Access denied");
            }
            if (!file.isFile()) {
                throw new IllegalArgumentException("Media file is not a file");
            }
            MusicFolder folder = mediaFolderService.getMusicFolderById(file.getFolderId());

            MetaDataParser parser = metaDataParserFactory.getParser(file.getFullPath(folder.getPath()));

            if (parser == null || !parser.isEditingSupported()) {
                return "Tag editing of " + FilenameUtils.getExtension(file.getPath()) + " files is not supported.";
            }

            if (StringUtils.equals(data.getArtist(), file.getArtist())
                    && StringUtils.equals(data.getAlbum(), file.getAlbumName())
                    && StringUtils.equals(data.getTitle(), file.getTitle())
                    && ObjectUtils.equals(data.getYear(), file.getYear())
                    && StringUtils.equals(data.getGenre(), file.getGenre())
                    && ObjectUtils.equals(data.getTrack(), file.getTrackNumber())) {
                return "SKIPPED";
            }

            MetaData newMetaData = parser.getMetaData(file.getFullPath(folder.getPath()));

            // Note: album artist is intentionally not set, as it is not user-changeable.
            newMetaData.setArtist(data.getArtist());
            newMetaData.setAlbumName(data.getAlbum());
            newMetaData.setTitle(data.getTitle());
            newMetaData.setYear(data.getYear());
            newMetaData.setGenre(data.getGenre());
            newMetaData.setTrackNumber(data.getTrack());
            parser.setMetaData(file, newMetaData);

            mediaFileService.refreshMediaFile(file, folder);
            mediaFileService.refreshMediaFile(mediaFileService.getParentOf(file), folder);
            return "UPDATED";

        } catch (SecurityException x) {
            throw x;
        } catch (Exception x) {
            LOG.warn("Failed to update tags for {}", data.getMediaFileId(), x);
            return x.getMessage() == null ? "Tag editing failed" : x.getMessage();
        }
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setMetaDataParserFactory(MetaDataParserFactory metaDataParserFactory) {
        this.metaDataParserFactory = metaDataParserFactory;
    }
}
