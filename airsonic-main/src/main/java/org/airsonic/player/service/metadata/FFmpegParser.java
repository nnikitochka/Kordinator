package org.airsonic.player.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Parses meta data from video files using FFmpeg (http://ffmpeg.org/).
 * <p/>
 * Currently duration, bitrate and dimension are supported.
 */
@Service("ffmpegParser")
public class FFmpegParser extends MetaDataParser {
    private static final Logger LOG = LoggerFactory.getLogger(FFmpegParser.class);
    private static final String[] FFPROBE_OPTIONS = {
        "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams"
    };

    private TranscodingService transcodingService;
    private final MediaFolderService mediaFolderService;

    public FFmpegParser(TranscodingService transcodingService, MediaFolderService mediaFolderService) {
        this.transcodingService = transcodingService;
        this.mediaFolderService = mediaFolderService;
    }

    /**
     * Parses meta data for the given music file. No guessing or reformatting is done.
     *
     *
     * @param file The music file to parse.
     * @return Meta data for the file.
     */
    @Override
    public MetaData getRawMetaData(Path file) {

        MetaData metaData = new MetaData();

        try {
            // Use `ffprobe` in the transcode directory if it exists, otherwise let the system sort it out.
            String ffprobe = SettingsService.resolveTranscodeExecutable("ffprobe", "ffprobe");

            List<String> command = new ArrayList<>();
            command.add(ffprobe);
            command.addAll(Arrays.asList(FFPROBE_OPTIONS));
            command.add(file.toAbsolutePath().toString());

            Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
            JsonNode result;
            try (
                    var in = process.getInputStream();
                    var bin = new BufferedInputStream(in)
            ) {
                result = Util.getObjectMapper().readTree(bin);
            }

            metaData.setDuration(result.at("/format/duration").asDouble());
            // Bitrate is in Kb/s
            metaData.setBitRate(result.at("/format/bit_rate").asInt() / 1000);

            metaData.setAlbumArtist(getData(result, "album_artist"));
            metaData.setArtist(getData(result, "artist"));
            metaData.setAlbumName(getData(result, "album"));
            metaData.setGenre(getData(result, "genre"));
            metaData.setTitle(getData(result, "title"));

            String data = getData(result, "track");
            if (NumberUtils.isCreatable(data)) {
                metaData.setTrackNumber(NumberUtils.createInteger(data));
            }

            data = getData(result, "date");
            if (NumberUtils.isCreatable(data)) {
                metaData.setYear(NumberUtils.createInteger(data));
            }

            // Find the first (if any) stream that has dimensions and use those.
            // 'width' and 'height' are display dimensions; compare to 'coded_width', 'coded_height'.
            for (JsonNode stream : result.at("/streams")) {
                Track track = new Track(stream.get("index").asInt(), stream.get("codec_type").asText(), stream.at("/tags/language").asText(), stream.get("codec_name").asText());
                metaData.addTrack(track);

                if (track.isVideo() && stream.has("width") && stream.has("height")) {
                    metaData.setWidth(stream.get("width").asInt());
                    metaData.setHeight(stream.get("height").asInt());
                }
            }
        } catch (Throwable x) {
            LOG.warn("Error when parsing metadata in {}", file, x);
        }

        return metaData;
    }

    private static String getData(JsonNode node, String keyName) {
        List<String> keys = ImmutableList.of("/tags/" + keyName, "/tags/" + keyName.toUpperCase(), "/tags/" + keyName.toLowerCase());
        Optional<String> nonNullKey = keys.stream().map(k -> node.at("/format" + k).asText()).filter(StringUtils::isNotBlank).findFirst();
        if (nonNullKey.isPresent()) {
            return nonNullKey.get();
        } else {
            for (JsonNode stream : node.at("/streams")) {
                // skip coverart streams
                if (stream.hasNonNull("codec_name") && stream.get("codec_name").asText().equalsIgnoreCase("mjpeg")) {
                    continue;
                }
                nonNullKey = keys.stream().map(k -> stream.at(k).asText()).filter(StringUtils::isNotBlank).findFirst();
                if (nonNullKey.isPresent()) {
                    return nonNullKey.get();
                }
            }
        }

        return null;
    }

    /**
     * Not supported.
     */
    @Override
    public void setMetaData(MediaFile file, MetaData metaData) {
        throw new RuntimeException("setMetaData() not supported in " + getClass().getSimpleName());
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

    public void setTranscodingService(TranscodingService transcodingService) {
        this.transcodingService = transcodingService;
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    MediaFolderService getMediaFolderService() {
        return mediaFolderService;
    }
}
