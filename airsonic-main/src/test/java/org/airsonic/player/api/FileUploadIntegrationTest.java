package org.airsonic.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFolderService;
import java.util.List;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/airsonic")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FileUploadIntegrationTest {
    @ClassRule public static final HomeRule home = new HomeRule();
    @LocalServerPort private int port;
    @Autowired private SecurityService security;
    @Autowired private MediaFolderService folders;
    @Autowired private ApplicationContext context;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired private org.airsonic.player.service.StatusService statuses;
    @Test public void uploadsFilesAndArchivesWithPermissionsAndCleanup() throws Exception {
        assertFalse(context.containsBean("uploadController"));
        User uploader = new User("file-uploader", null); uploader.setRoles(Set.of(User.Role.UPLOAD));
        security.createUser(uploader, "secret", "test");
        User denied = new User("file-denied", null); security.createUser(denied, "secret", "test");
        var root = java.nio.file.Files.createTempDirectory("upload-library-");
        var outside = java.nio.file.Files.createTempDirectory("upload-outside-");
        var folder = new org.airsonic.player.domain.MusicFolder(root, "Uploads", org.airsonic.player.domain.MusicFolder.Type.MEDIA, true, java.time.Instant.now());
        folders.createMusicFolder(folder);
        try {
            var browser = login(uploader.getUsername(), "secret"); var admin = login("admin", "admin");
            String csrf = csrf(browser);
            byte[] bytes = "Музыка & <track>".getBytes(StandardCharsets.UTF_8);
            String dir = root.resolve("новая папка").toString();
            var html = get(browser, "/more.view"); assertEquals(html.body(), 200, html.statusCode());
            assertTrue(html.body().contains("/airsonic/rest/upload")); assertTrue(html.body().contains("bindFileUpload("));
            assertFalse(html.body().contains("upload.view"));
            assertEquals(403, upload(client, null, field("dir", dir), file("anonymous.txt", bytes)).statusCode());
            assertError(post(browser, "/rest/upload?f=json", "_csrf=" + encode(csrf)), 0);
            assertEquals(403, upload(browser, null, field("dir", dir), file("test.txt", bytes)).statusCode());
            // Capture a separate denied session/token pair.
            var blocked = login(denied.getUsername(), "secret");
            assertError(upload(blocked, csrf(blocked), field("dir", dir), file("denied.txt", bytes)), 50);
            assertFalse(java.nio.file.Files.exists(root.resolve("новая папка/denied.txt")));
            assertEquals(405, get(browser, "/rest/upload?f=json").statusCode());
            assertEquals(404, get(admin, "/upload.view").statusCode());
            for (String method : List.of("PUT", "DELETE", "PATCH")) {
                assertEquals(405, browser.send(HttpRequest.newBuilder(URI.create(base() + "/rest/upload")).header("X-CSRF-TOKEN", csrf)
                    .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            var tempBefore = temporaryUploads();
            long before = security.getUserByName(uploader.getUsername()).getBytesUploaded();
            // Files may precede fields. Windows paths are reduced to their basename as well.
            var result = parse(upload(browser, csrf, file("C:\\fakepath\\трек & test.txt", bytes), file("empty.txt", new byte[0]), field("dir", dir)));
            assertEquals(result.toString(), "ok", result.path("status").asText());
            assertEquals(2, result.path("uploadResult").path("uploadedFile").size());
            assertTrue(result.toString(), java.nio.file.Files.exists(Path.of(dir, "трек & test.txt")));
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(Path.of(dir, "трек & test.txt")));
            assertEquals(0, java.nio.file.Files.size(Path.of(dir, "empty.txt")));
            assertEquals(before + bytes.length, security.getUserByName(uploader.getUsername()).getBytesUploaded());
            assertTrue(statuses.getAllUploadStatuses().isEmpty()); assertEquals(tempBefore, temporaryUploads());
            // Existing files are protected for ordinary uploaders; a later valid file still succeeds.
            result = parse(upload(browser, csrf, field("dir", dir), file("трек & test.txt", new byte[]{0}), file("second.txt", bytes)));
            assertEquals("failed", result.path("status").asText());
            assertEquals(1, result.path("uploadResult").path("uploadedFile").size());
            assertTrue(result.toString(), java.nio.file.Files.exists(Path.of(dir, "трек & test.txt")));
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(Path.of(dir, "трек & test.txt")));
            assertTrue(java.nio.file.Files.exists(Path.of(dir, "second.txt")));
            assertError(upload(browser, csrf, file("missing-dir.txt", bytes)), 0);
            assertError(upload(browser, csrf, field("dir", dir)), 0);
            assertError(upload(browser, csrf, field("dir", dir), field("dir", outside.toString()), file("duplicate-dir.txt", bytes)), 0);
            assertError(upload(browser, csrf, field("dir", outside.toString()), file("outside.txt", bytes)), 50);
            assertFalse(java.nio.file.Files.exists(outside.resolve("outside.txt")));
            java.nio.file.Files.createSymbolicLink(root.resolve("link"), outside);
            assertError(upload(browser, csrf, field("dir", root.resolve("link").toString()), file("escape.txt", bytes)), 50);
            assertFalse(java.nio.file.Files.exists(outside.resolve("escape.txt")));
            // ZIP extraction preserves nested entries and rejects traversal and symlink parents.
            var archive = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.ZipOutputStream(archive)) {
                for (String name : List.of("nested/трек.txt", "../outside.txt", "link/escape.txt")) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry();
                }
            }
            result = parse(upload(browser, csrf, field("dir", root.toString()), field("unzip", "on"), file("songs.zip", archive.toByteArray())));
            assertEquals("failed", result.path("status").asText());
            assertEquals(1, result.path("uploadResult").path("unzippedFile").size());
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(root.resolve("nested/трек.txt")));
            assertFalse(java.nio.file.Files.exists(outside.resolve("escape.txt")));
            assertFalse(java.nio.file.Files.exists(root.resolve("songs.zip")));
            // An archive without unzip remains intact; invalid archives report extraction errors.
            result = parse(upload(browser, csrf, field("dir", dir), file("keep.zip", archive.toByteArray())));
            assertEquals("ok", result.path("status").asText());
            assertArrayEquals(archive.toByteArray(), java.nio.file.Files.readAllBytes(Path.of(dir, "keep.zip")));
            assertError(upload(browser, csrf, field("dir", dir), field("unzip", "on"), file("bad.zip", bytes)), 0);
            for (String format : List.of("jar", "tar", "cpio", "7z")) {
                byte[] data = archive(format, "track-" + format + ".txt", bytes);
                result = parse(upload(browser, csrf, field("dir", dir), field("unzip", "on"), file("songs." + format, data)));
                assertEquals(result.toString(), "ok", result.path("status").asText());
                assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(Path.of(dir, "track-" + format + ".txt")));
            }
            // Admin uploads inside airsonic.home keep the previous replacement exception and XML default.
            var homeDir = Path.of(TestCaseUtils.airsonicHomePathForTest(), "upload-test");
            String adminCsrf = csrf(admin);
            for (byte[] data : List.of(bytes, new byte[]{1, 2})) {
                var xml = uploadXml(admin, adminCsrf, field("dir", homeDir.toString()), file("test.txt", data));
                assertTrue(xml.body(), xml.body().contains("status=\"ok\""));
                assertArrayEquals(data, java.nio.file.Files.readAllBytes(homeDir.resolve("test.txt")));
            }
            assertTrue(statuses.getAllUploadStatuses().isEmpty()); assertEquals(tempBefore, temporaryUploads());
        } finally {
            folders.deleteMusicFolder(folder.getId());
            try (var paths = java.nio.file.Files.walk(root)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path); }
            try (var paths = java.nio.file.Files.walk(outside)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path); }
        }
    }
    private byte[] archive(String format, String name, byte[] bytes) throws Exception {
        if (format.equals("7z")) {
            var temp = java.nio.file.Files.createTempFile("archive-test-", ".7z");
            try {
                try (var out = new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(temp.toFile())) {
                    var entry = new org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry(); entry.setName(name); entry.setSize(bytes.length);
                    out.putArchiveEntry(entry); out.write(bytes); out.closeArchiveEntry();
                }
                return java.nio.file.Files.readAllBytes(temp);
            } finally { java.nio.file.Files.delete(temp); }
        }
        var bytesOut = new java.io.ByteArrayOutputStream();
        try (var out = new org.apache.commons.compress.archivers.ArchiveStreamFactory().createArchiveOutputStream(format, bytesOut)) {
            org.apache.commons.compress.archivers.ArchiveEntry entry = switch (format) {
                case "jar" -> new org.apache.commons.compress.archivers.jar.JarArchiveEntry(name);
                case "tar" -> { var e = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(name); e.setSize(bytes.length); yield e; }
                default -> new org.apache.commons.compress.archivers.cpio.CpioArchiveEntry(name, bytes.length);
            };
            out.putArchiveEntry(entry); out.write(bytes); out.closeArchiveEntry();
        }
        return bytesOut.toByteArray();
    }
    private Set<Path> temporaryUploads() throws Exception {
        try (var files = java.nio.file.Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("airsonic-upload-")).collect(java.util.stream.Collectors.toSet());
        }
    }
    private String csrf(HttpClient browser) throws Exception {
        Matcher token = Pattern.compile("csrfToken: \"([^\"]+)\"").matcher(get(browser, "/top.view").body()); assertTrue(token.find()); return token.group(1);
    }
    private byte[] field(String name, String value) { return ("--UploadBoundary\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8); }
    private byte[] file(String name, byte[] bytes) throws Exception {
        name = name.replace("\\", "\\\\").replace("\"", "\\\"");
        var out = new java.io.ByteArrayOutputStream();
        out.write(("--UploadBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes); out.write("\r\n".getBytes(StandardCharsets.UTF_8)); return out.toByteArray();
    }
    private HttpResponse<String> upload(HttpClient browser, String token, byte[]... parts) throws Exception { return uploadRaw(browser, token, "?f=json", parts); }
    private HttpResponse<String> uploadXml(HttpClient browser, String token, byte[]... parts) throws Exception { return uploadRaw(browser, token, "", parts); }
    private HttpResponse<String> uploadRaw(HttpClient browser, String token, String query, byte[]... parts) throws Exception {
        var out = new java.io.ByteArrayOutputStream(); for (var part : parts) out.write(part); out.write("--UploadBoundary--\r\n".getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(base() + "/rest/upload" + query)).header("Content-Type", "multipart/form-data; boundary=UploadBoundary");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return browser.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode parse(HttpResponse<String> response) throws Exception { assertEquals(response.body(), 200, response.statusCode()); return mapper.readTree(response.body()).path("subsonic-response"); }
    private void assertError(HttpResponse<String> response, int code) throws Exception { var data = parse(response); assertEquals(data.toString(), "failed", data.path("status").asText()); assertEquals(data.toString(), code, data.path("error").path("code").asInt()); }
    private HttpClient login(String username, String password) throws Exception {
        HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(browser, "/login").body());
        assertTrue(token.find());
        assertEquals(302, post(browser, "/login", "j_username=" + username + "&j_password=" + password + "&_csrf=" + encode(token.group(1))).statusCode());
        return browser;
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient browser, String path, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder().timeout(java.time.Duration.ofSeconds(30)).uri(URI.create(base() + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + port + "/airsonic"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
