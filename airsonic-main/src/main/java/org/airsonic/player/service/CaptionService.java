package org.airsonic.player.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import org.airsonic.player.controller.captions.CaptionInfo;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.io.InputStreamReaderThread;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CaptionService {
    private static final Logger LOG = LoggerFactory.getLogger(CaptionService.class);

    private static final String CAPTION_FORMAT_VTT = "vtt";
    private static final String CAPTION_FORMAT_SRT = "srt";
    private static final Set<String> CAPTIONS_FORMATS = ImmutableSet.of(CAPTION_FORMAT_VTT, CAPTION_FORMAT_SRT);

    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final SettingsService settingsService;
    private final MetaDataParserFactory metaDataParserFactory;
    private final JWTSecurityService jwtSecurityService;

    public CaptionService(MediaFileService mediaFileService, MediaFolderService mediaFolderService, SettingsService settingsService, MetaDataParserFactory metaDataParserFactory, JWTSecurityService jwtSecurityService) {
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.settingsService = settingsService;
        this.metaDataParserFactory = metaDataParserFactory;
        this.jwtSecurityService = jwtSecurityService;
    }

    public InputStream openConverted(Path inputFile, String identifier, String format) throws IOException {
        String[] split = StringUtils.split(settingsService.getSubtitlesExtractionCommand());
        List<String> command = Arrays.stream(split).sequential()
                .map(i -> {
                    if (i.equals(split[0])) {
                        return SettingsService.resolveTranscodeExecutable(i, i);
                    }
                    if (i.contains("%s")) {
                        return i.replace("%s", inputFile.toString());
                    }
                    if (i.contains("%i")) {
                        return i.replace("%i", identifier);
                    }
                    if (i.contains("%f")) {
                        return i.replace("%f", getForceFormat(format));
                    }

                    return i;
                }).collect(Collectors.toList());
        Process process = new ProcessBuilder(command).start();

        // Must read stderr from the process, otherwise it may block.
        new InputStreamReaderThread(process.getErrorStream(), "subs-extraction-error-stream-" + inputFile.toString(), true).start();
        return new FilterInputStream(process.getInputStream()) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { process.destroy(); }
            }
        };
    }

    public static String getForceFormat(String format) {
        if (format.equals("vtt")) return "webvtt";
        return format;
    }

    public static String getDisplayFormat(String format) {
        return switch (format) {
            case "webvtt" -> "vtt";
            case "subrip" -> "srt";
            default -> format;
        };
    }

    public List<CaptionInfo> listCaptions(MediaFile video, String basePath) {
        return listCaptions(video, basePath, null, null);
    }

    public List<CaptionInfo> listCaptions(MediaFile video, String basePath, String externalUser, Instant externalExpiration) {
        MetaData metaData = getVideoMetaData(video);
        Stream<CaptionInfo> internalCaptions;
        if (metaData == null || metaData.getSubtitleTracks().isEmpty()) {
            internalCaptions = Stream.empty();
        } else {
            internalCaptions = metaData.getSubtitleTracks().stream()
                    .map(c -> new CaptionInfo(
                            String.valueOf(c.getId()),
                            CaptionInfo.Location.INTERNAL,
                            getDisplayFormat(c.getCodec()),
                            c.getLanguage(),
                            getUrl(basePath, externalUser, externalExpiration, video.getId(),
                                    String.valueOf(c.getId()))));
        }

        Stream<CaptionInfo> externalCaptions = findExternalCaptionsForVideo(video).stream()
                .map(c -> new CaptionInfo(c.toString(), // leaks internal structure for now
                        CaptionInfo.Location.EXTERNAL,
                        MoreFiles.getFileExtension(c),
                        c.getFileName().toString(),
                        getUrl(basePath, externalUser, externalExpiration, video.getId(),
                                URLEncoder.encode(c.toString(), StandardCharsets.UTF_8))));

        return Stream.concat(internalCaptions, externalCaptions).collect(Collectors.toList());
    }

    public MetaData getVideoMetaData(MediaFile video) {
        Path videoFullPath = video.getFullPath(mediaFolderService.getMusicFolderById(video.getFolderId()).getPath());
        MetaDataParser parser = this.metaDataParserFactory.getParser(videoFullPath);
        return (parser != null) ? parser.getMetaData(videoFullPath) : null;
    }

    public String getUrl(String basePath, String externalUser, Instant externalExpiration, int mediaId, String captionId) {
        boolean ext = !StringUtils.isBlank(externalUser);
        String url = (ext ? "ext/" : "") + "rest/captions?id=" + mediaId + "&captionId=" + captionId;
        if (ext) {
            url = jwtSecurityService.addJWTToken(externalUser, url, externalExpiration);
        }
        return basePath + url;
    }

    public InputStream openExternal(Path captionsFile, String format) throws IOException {
        InputStream input = Files.newInputStream(captionsFile);
        return "vtt".equals(format) ? input : new BOMInputStream(input);
    }

    public List<Path> findExternalCaptionsForVideo(MediaFile video) {
        MediaFile parent = mediaFileService.getParentOf(video);
        if (parent == null) {
            return Collections.emptyList();
        }
        Path parentPath = parent.getFullPath(mediaFolderService.getMusicFolderById(parent.getFolderId()).getPath());

        try (Stream<Path> children = Files.walk(parentPath)) {
            return children.parallel()
                    .filter(Files::isRegularFile)
                    .filter(c -> CAPTIONS_FORMATS.contains(MoreFiles.getFileExtension(c)))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.warn("Could not retrieve directory list for {} to find subtitle files for {}", parentPath, video, e);

            return Collections.emptyList();
        }
    }

}
