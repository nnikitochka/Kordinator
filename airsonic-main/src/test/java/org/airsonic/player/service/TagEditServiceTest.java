package org.airsonic.player.service;

import org.airsonic.player.ajax.tag.TagData;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TagEditServiceTest {
    @Test public void unsupportedParserDoesNotWriteOrRefresh() {
        MetaDataParserFactory factory = mock(MetaDataParserFactory.class);
        MediaFileService files = mock(MediaFileService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        SecurityService security = mock(SecurityService.class);
        MediaFile file = mock(MediaFile.class);
        MusicFolder folder = mock(MusicFolder.class);
        MetaDataParser parser = mock(MetaDataParser.class);
        TagData data = new TagData();
        data.setMediaFileId(1);
        when(files.getMediaFile(1)).thenReturn(file);
        when(file.isFile()).thenReturn(true);
        when(file.getPath()).thenReturn("test.wav");
        when(file.getFolderId()).thenReturn(2);
        when(security.isFolderAccessAllowed(file, "editor")).thenReturn(true);
        when(folders.getMusicFolderById(2)).thenReturn(folder);
        when(folder.getPath()).thenReturn(Path.of("/music"));
        when(file.getFullPath(any())).thenReturn(Path.of("/music/test.wav"));
        when(factory.getParser(any())).thenReturn(parser);
        TagEditService service = new TagEditService(factory, files, folders, security);
        assertEquals("Tag editing of wav files is not supported.", service.setTags(data, "editor"));
        verify(parser, never()).setMetaData(any(), any());
        verify(files, never()).refreshMediaFile(any(), any());
    }

    @Test public void deniesFolderBeforeOpeningOrWritingFile() {
        MetaDataParserFactory factory = mock(MetaDataParserFactory.class);
        MediaFileService files = mock(MediaFileService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        SecurityService security = mock(SecurityService.class);
        MediaFile file = mock(MediaFile.class);
        TagData data = new TagData();
        data.setMediaFileId(1);
        when(files.getMediaFile(1)).thenReturn(file);
        TagEditService service = new TagEditService(factory, files, folders, security);
        assertThrows(SecurityException.class, () -> service.setTags(data, "reader"));
        verifyNoInteractions(factory, folders);
    }
}
