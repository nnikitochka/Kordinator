package org.airsonic.player.ajax.tag;

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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@MessageMapping("/tags")
public class TagWSController {
    private static final Logger LOG = LoggerFactory.getLogger(TagWSController.class);

    private MetaDataParserFactory metaDataParserFactory;
    private MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;

    public TagWSController(MetaDataParserFactory metaDataParserFactory, MediaFileService mediaFileService, MediaFolderService mediaFolderService) {
        this.metaDataParserFactory = metaDataParserFactory;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
    }

    /**
     * Updated tags for a given music file.
     *
     * @return "UPDATED" if the new tags were updated, "SKIPPED" if no update was necessary.
     *         Otherwise the error message is returned.
     */
    @MessageMapping("/edit")
    @SendToUser(broadcast = false)
    public String setTags(@Validated TagData data) {
        try {
            MediaFile file = mediaFileService.getMediaFile(data.getMediaFileId());
            MusicFolder folder = mediaFolderService.getMusicFolderById(file.getFolderId());

            MetaDataParser parser = metaDataParserFactory.getParser(file.getFullPath(folder.getPath()));

            if (!parser.isEditingSupported()) {
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

        } catch (Exception x) {
            LOG.warn("Failed to update tags for {}", data.getMediaFileId(), x);
            return x.getMessage();
        }
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setMetaDataParserFactory(MetaDataParserFactory metaDataParserFactory) {
        this.metaDataParserFactory = metaDataParserFactory;
    }
}
