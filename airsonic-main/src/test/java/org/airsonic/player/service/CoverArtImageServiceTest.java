package org.airsonic.player.service;

import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.util.HomeRule;
import org.jaudiotagger.tag.images.Artwork;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CoverArtImageServiceTest {
    @ClassRule public static final HomeRule home = new HomeRule();

    @Test public void extractsEmbeddedImageWithoutResizing() throws Exception {
        MediaFileService files = mock(MediaFileService.class);
        CoverArtService artService = mock(CoverArtService.class);
        JaudiotaggerParser parser = mock(JaudiotaggerParser.class);
        SettingsService settings = mock(SettingsService.class);
        MediaFile directory = mock(MediaFile.class);
        CoverArt art = new CoverArt(3, CoverArt.EntityType.MEDIA_FILE, "song.mp3", 1, false);
        Path path = Path.of("song.mp3");
        byte[] bytes = new byte[]{1, 2, 3, 4};
        Artwork embedded = mock(Artwork.class);
        when(files.getMediaFile(3)).thenReturn(directory);
        when(directory.getId()).thenReturn(3);
        when(directory.isDirectory()).thenReturn(true);
        when(artService.get(CoverArt.EntityType.MEDIA_FILE, 3)).thenReturn(art);
        when(artService.getFullPath(art)).thenReturn(path);
        when(parser.isApplicable(path)).thenReturn(true);
        when(embedded.getBinaryData()).thenReturn(bytes);
        when(embedded.getMimeType()).thenReturn("image/png");
        CoverArtImageService service = create(files, artService, parser, settings, mock(TranscodingService.class));
        try (MockedStatic<JaudiotaggerParser> tagger = mockStatic(JaudiotaggerParser.class)) {
            tagger.when(() -> JaudiotaggerParser.getArtwork(path)).thenReturn(embedded);
            try (var result = service.getImage("3", null, null)) {
                assertEquals("image/png", result.contentType());
                assertArrayEquals(bytes, result.stream().readAllBytes());
            }
        }
    }

    @Test public void passesVideoSizeAndOffsetToTranscoder() throws Exception {
        MediaFileService files = mock(MediaFileService.class);
        SettingsService settings = mock(SettingsService.class);
        TranscodingService transcoder = mock(TranscodingService.class);
        MediaFile video = mock(MediaFile.class);
        when(files.getMediaFile(4)).thenReturn(video);
        when(video.isVideo()).thenReturn(true);
        when(video.getFolderId()).thenReturn(1);
        when(video.getPath()).thenReturn("sample-video-for-test.mp4");
        when(video.getChanged()).thenReturn(Instant.now());
        when(settings.getCoverArtConcurrency()).thenReturn(1);
        when(settings.getCoverArtQuality()).thenReturn(80);
        when(settings.getVideoImageCommand()).thenReturn("ffmpeg");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(56, 32, BufferedImage.TYPE_INT_RGB), "png", encoded);
        when(transcoder.getTranscodedInputStream(any())).thenReturn(new ByteArrayInputStream(encoded.toByteArray()));
        CoverArtImageService service = create(files, mock(CoverArtService.class), mock(JaudiotaggerParser.class), settings, transcoder);
        service.init();
        try (var result = service.getImage("4", 32, "123")) {
            BufferedImage image = ImageIO.read(result.stream());
            assertNotNull(image);
            assertEquals(56, image.getWidth());
            assertEquals(32, image.getHeight());
        }
        ArgumentCaptor<TranscodingService.Parameters> parameters = ArgumentCaptor.forClass(TranscodingService.Parameters.class);
        verify(transcoder).getTranscodedInputStream(parameters.capture());
        assertEquals(56, parameters.getValue().getVideoTranscodingSettings().getWidth());
        assertEquals(32, parameters.getValue().getVideoTranscodingSettings().getHeight());
        assertEquals(123, parameters.getValue().getVideoTranscodingSettings().getTimeOffset());
    }

    private CoverArtImageService create(MediaFileService files, CoverArtService art, JaudiotaggerParser parser,
            SettingsService settings, TranscodingService transcoder) {
        return new CoverArtImageService(files, art, transcoder, settings, mock(PlaylistService.class),
                mock(ArtistDao.class), mock(AlbumDao.class), parser);
    }
}
