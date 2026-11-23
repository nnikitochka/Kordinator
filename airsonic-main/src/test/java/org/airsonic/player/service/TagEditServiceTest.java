package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.security.CallerContext;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TagEditServiceTest {
    @Test public void supportedParserWritesTagsAndRefreshesFileAndParent() {
        MetaDataParserFactory factory = mock(MetaDataParserFactory.class);
        MediaFileService files = mock(MediaFileService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        SecurityService security = mock(SecurityService.class);
        MediaFile file = mock(MediaFile.class);
        MediaFile parent = mock(MediaFile.class);
        MusicFolder folder = mock(MusicFolder.class);
        MetaDataParser parser = mock(MetaDataParser.class);
        MetaData metadata = mock(MetaData.class);
        Path filePath = Path.of("/music/album/track.mp3");
        TrackTagUpdate data = new TrackTagUpdate(1, 3, "Artist", "Album", "Title", 2024, "Rock");
        CallerContext caller = new CallerContext("admin", Set.of(), Set.of(2), true);
        when(files.getMediaFile(1)).thenReturn(file);
        when(file.isFile()).thenReturn(true);
        when(file.getFolderId()).thenReturn(2);
        when(file.getArtist()).thenReturn("Old Artist");
        when(file.getAlbumName()).thenReturn("Old Album");
        when(file.getTitle()).thenReturn("Old Title");
        when(file.getYear()).thenReturn(2020);
        when(file.getGenre()).thenReturn("Pop");
        when(file.getTrackNumber()).thenReturn(1);
        when(file.getFullPath(any())).thenReturn(filePath);
        when(files.getParentOf(file)).thenReturn(parent);
        when(security.isFolderAccessAllowed(file, caller)).thenReturn(true);
        when(folders.getMusicFolderById(2)).thenReturn(folder);
        when(folder.getPath()).thenReturn(Path.of("/music"));
        when(factory.getParser(filePath)).thenReturn(parser);
        when(parser.isEditingSupported()).thenReturn(true);
        when(parser.getMetaData(filePath)).thenReturn(metadata);

        TagEditService service = new TagEditService(factory, files, folders, security);

        assertEquals("UPDATED", service.setTags(data, caller));
        verify(metadata).setArtist("Artist");
        verify(metadata).setAlbumName("Album");
        verify(metadata).setTitle("Title");
        verify(metadata).setYear(2024);
        verify(metadata).setGenre("Rock");
        verify(metadata).setTrackNumber(3);
        verify(parser).setMetaData(file, metadata);
        verify(files).refreshMediaFile(file, folder);
        verify(files).refreshMediaFile(parent, folder);
    }

    @Test public void unsupportedParserDoesNotWriteOrRefresh() {
        MetaDataParserFactory factory = mock(MetaDataParserFactory.class);
        MediaFileService files = mock(MediaFileService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        SecurityService security = mock(SecurityService.class);
        MediaFile file = mock(MediaFile.class);
        MusicFolder folder = mock(MusicFolder.class);
        MetaDataParser parser = mock(MetaDataParser.class);
        TrackTagUpdate data = new TrackTagUpdate(1, null, null, null, null, null, null);
        when(files.getMediaFile(1)).thenReturn(file);
        when(file.isFile()).thenReturn(true);
        when(file.getPath()).thenReturn("test.wav");
        when(file.getFolderId()).thenReturn(2);
        CallerContext caller = new CallerContext("editor", Set.of(), Set.of(2), true);
        when(security.isFolderAccessAllowed(file, caller)).thenReturn(true);
        when(folders.getMusicFolderById(2)).thenReturn(folder);
        when(folder.getPath()).thenReturn(Path.of("/music"));
        when(file.getFullPath(any())).thenReturn(Path.of("/music/test.wav"));
        when(factory.getParser(any())).thenReturn(parser);
        TagEditService service = new TagEditService(factory, files, folders, security);
        assertEquals("Tag editing of wav files is not supported.", service.setTags(data, caller));
        verify(parser, never()).setMetaData(any(), any());
        verify(files, never()).refreshMediaFile(any(), any());
    }

    @Test public void deniesFolderBeforeOpeningOrWritingFile() {
        MetaDataParserFactory factory = mock(MetaDataParserFactory.class);
        MediaFileService files = mock(MediaFileService.class);
        MediaFolderService folders = mock(MediaFolderService.class);
        SecurityService security = mock(SecurityService.class);
        MediaFile file = mock(MediaFile.class);
        TrackTagUpdate data = new TrackTagUpdate(1, null, null, null, null, null, null);
        CallerContext caller = new CallerContext("reader", Set.of(), Set.of(), true);
        when(files.getMediaFile(1)).thenReturn(file);
        TagEditService service = new TagEditService(factory, files, folders, security);
        assertThrows(SecurityException.class, () -> service.setTags(data, caller));
        verifyNoInteractions(factory, folders);
    }
}
