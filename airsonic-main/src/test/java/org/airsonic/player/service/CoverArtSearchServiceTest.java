package org.airsonic.player.service;

import com.sun.net.httpserver.HttpServer;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CoverArtSearchServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void savesBacksUpAndDoesNotOverwriteOnHttpError() throws Exception {
        Path root = temp.newFolder("music").toPath();
        Path album = Files.createDirectory(root.resolve("album"));
        Path target = album.resolve("cover.png");
        Files.writeString(target, "old");
        MusicFolder folder = new MusicFolder(5, root, "music", MusicFolder.Type.MEDIA, true, Instant.now());
        MediaFile file = mock(MediaFile.class);
        when(file.isDirectory()).thenReturn(true);
        when(file.getFolderId()).thenReturn(5);
        when(file.getId()).thenReturn(7);
        when(file.getFullPath(root)).thenReturn(album);
        MediaFileService files = mock(MediaFileService.class);
        when(files.getMediaFile(7)).thenReturn(file);
        MediaFolderService folders = mock(MediaFolderService.class);
        when(folders.getMusicFoldersForUser("owner")).thenReturn(List.of(folder));
        when(folders.getMusicFolderById(5)).thenReturn(folder);
        SecurityService security = mock(SecurityService.class);
        when(security.isWriteAllowed(any(), eq(folder))).thenReturn(true);
        CoverArtService cover = mock(CoverArtService.class);
        CoverArtSearchService service = new CoverArtSearchService(security, files, mock(LastFmService.class), folders, cover);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/good.png", exchange -> {
            byte[] bytes = "new".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/bad.png", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            service.setCoverArtImage(7, base + "/good.png", "owner");
            assertEquals("new", Files.readString(target));
            assertEquals("old", Files.readString(album.resolve("cover.png.backup")));
            verify(cover).upsert(CoverArt.EntityType.MEDIA_FILE, 7, "album/cover.png", 5, true);
            assertThrows(Exception.class, () -> service.setCoverArtImage(7, base + "/bad.png", "owner"));
            assertEquals("new", Files.readString(target));
            assertThrows(SecurityException.class, () -> service.setCoverArtImage(7, base + "/good.png", "reader"));
            verify(cover, times(1)).upsert(any(), anyInt(), anyString(), anyInt(), anyBoolean());
        } finally { server.stop(0); }
    }
}
