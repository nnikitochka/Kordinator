package org.airsonic.player.service.hls;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Payload;
import org.airsonic.player.service.VideoPlaybackService;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.security.JWTAuthenticationToken;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.TranscodingService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Builds playlists shared by the website and the REST API. */
@Service
public class HlsPlaylistService {
    private static final int SEGMENT_DURATION = 10;
    private static final Pattern BITRATE_PATTERN = Pattern.compile("(\\d+)(@(\\d+)x(\\d+))?");
    private final JWTSecurityService jwtSecurityService;

    public HlsPlaylistService(JWTSecurityService jwtSecurityService) {
        this.jwtSecurityService = jwtSecurityService;
    }

    public String generate(Authentication authentication, MediaFile mediaFile, Player player,
            String basePath, String prefixPath, String[] requestedBitRates, String size) {
        return generate(authentication, mediaFile, player, basePath, prefixPath, requestedBitRates, size, false);
    }

    public String generate(Authentication authentication, MediaFile mediaFile, Player player,
            String basePath, String prefixPath, String[] requestedBitRates, String size, boolean signedRestUrls) {
        List<Pair<Integer, Dimension>> bitRates = parseBitRates(requestedBitRates, size).stream()
                .map(b -> b.getRight() != null ? b
                        : Pair.of(b.getLeft(), TranscodingService.getSuitableVideoSize(mediaFile.getWidth(),
                                mediaFile.getHeight(), b.getLeft())))
                .collect(Collectors.toList());
        if (bitRates.isEmpty())
            bitRates = Collections.singletonList(
                    Pair.of(VideoPlaybackService.DEFAULT_BIT_RATE, TranscodingService.getSuitableVideoSize(
                            mediaFile.getWidth(), mediaFile.getHeight(), VideoPlaybackService.DEFAULT_BIT_RATE)));
        UriComponentsBuilder prefix = UriComponentsBuilder.fromUriString(prefixPath);
        Instant linkExpiration = expiration(authentication, signedRestUrls, mediaFile.getDuration());
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        if (bitRates.size() > 1) {
            generateVariantPlaylist(authentication, basePath, prefix, mediaFile.getId(), player, bitRates, writer, signedRestUrls, linkExpiration);
        } else {
            generateNormalPlaylist(authentication, basePath, prefix, mediaFile.getId(), player, bitRates.getFirst(),
                    Math.round(mediaFile.getDuration()), writer, signedRestUrls, linkExpiration);
        }
        writer.flush();
        return body.toString();
    }

    private List<Pair<Integer, Dimension>> parseBitRates(String[] bitRates, String globalSize) throws IllegalArgumentException {
        List<Pair<Integer, Dimension>> result = new ArrayList<>();
        if (bitRates != null) {
            for (String bitRate : bitRates) {
                result.add(parseBitRate(bitRate, globalSize));
            }
        }
        return result;
    }

    /**
     * Parses a string containing the bitrate and an optional width/height, e.g., 1200@640x480
     */
    public Pair<Integer, Dimension> parseBitRate(String bitRate, String defaultSize) throws IllegalArgumentException {

        Matcher matcher = BITRATE_PATTERN.matcher(bitRate);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid bitrate specification: " + bitRate);
        }
        int kbps = Integer.parseInt(matcher.group(1));
        if (matcher.group(3) == null) {
            if (defaultSize == null) {
                return Pair.of(kbps, null);
            }
            String[] dims = StringUtils.split(defaultSize, "x");
            int width = (Integer.parseInt(dims[0]) / 2) * 2;
            int height = (Integer.parseInt(dims[1]) / 2) * 2;
            return Pair.of(kbps, new Dimension(width, height));
        } else {
            int width = (Integer.parseInt(matcher.group(3)) / 2) * 2;
            int height = (Integer.parseInt(matcher.group(4)) / 2) * 2;
            return Pair.of(kbps, new Dimension(width, height));
        }
    }

    private void generateVariantPlaylist(Authentication authentication, String basePath, UriComponentsBuilder prefix,
            int id, Player player, List<Pair<Integer, Dimension>> bitRates, PrintWriter writer, boolean signedRestUrls, Instant linkExpiration) {
        writer.println("#EXTM3U");
        writer.println("#EXT-X-VERSION:1");
//        writer.println("#EXT-X-TARGETDURATION:" + SEGMENT_DURATION);

        for (Pair<Integer, Dimension> bitRate : bitRates) {
            Integer kbps = bitRate.getLeft();
            int averageVideoBitRate = TranscodingService.getAverageVideoBitRate(kbps);
            writer.println("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=" + (kbps * 1000L) + ",AVERAGE-BANDWIDTH=" + (averageVideoBitRate * 1000L));
            UriComponentsBuilder url = prefix.cloneBuilder()
                    .pathSegment("hls.m3u8")
                    .queryParam("id", id)
                    .queryParam("player", player.getId())
                    .queryParam("maxBitRate", kbps + Optional.ofNullable(bitRate.getRight()).map(d -> "@" + d.width + "x" + d.height).orElse(""));
            if (signedRestUrls || authentication instanceof JWTAuthenticationToken) {
                url = jwtSecurityService.addJWTToken(authentication.getName(), url, linkExpiration);
            }

            writer.print(basePath + url.toUriString());
            writer.println();
        }
//        writer.println("#EXT-X-ENDLIST");
    }

    private void generateNormalPlaylist(Authentication authentication, String basePath, UriComponentsBuilder prefix,
            int id, Player player, Pair<Integer, Dimension> bitRate, long totalDuration, PrintWriter writer, boolean signedRestUrls, Instant linkExpiration) {
        writer.println("#EXTM3U");
        writer.println("#EXT-X-VERSION:1");
        writer.println("#EXT-X-TARGETDURATION:" + SEGMENT_DURATION);

        for (int i = 0; i < totalDuration / SEGMENT_DURATION; i++) {
            writer.println("#EXTINF:" + SEGMENT_DURATION + ",");
            writer.println(createStreamUrl(authentication, basePath, prefix, player, id, i, SEGMENT_DURATION, bitRate, signedRestUrls, linkExpiration));
        }

        long remainder = totalDuration % SEGMENT_DURATION;
        if (remainder > 0) {
            writer.println("#EXTINF:" + remainder + ",");
            writer.println(
                    createStreamUrl(authentication, basePath, prefix, player, id,
                            (int) (totalDuration / SEGMENT_DURATION),
                            SEGMENT_DURATION, bitRate, signedRestUrls, linkExpiration));
        }
        writer.println("#EXT-X-ENDLIST");
    }

    private Instant expiration(Authentication authentication, boolean signedRestUrls, double duration) {
        if (authentication instanceof JWTAuthenticationToken) {
            DecodedJWT token = (DecodedJWT) authentication.getDetails();
            return Optional.ofNullable(token).map(Payload::getExpiresAt).map(Date::toInstant).orElse(null);
        }
        return signedRestUrls ? Instant.now().plusSeconds((long) Math.ceil(duration) + 3600) : null;
    }

    private String createStreamUrl(Authentication authentication,
            String basePath, UriComponentsBuilder prefix,
            Player player,
            int id,
            int segmentIndex,
            long duration,
            Pair<Integer, Dimension> bitRate, boolean signedRestUrls, Instant linkExpiration) {
        UriComponentsBuilder builder = prefix.cloneBuilder()
                .pathSegment("segment", "segment.ts")
                .queryParam("id", id)
                .queryParam("hls", "true")
                .queryParam("segmentIndex", segmentIndex)
                .queryParam("player", player.getId())
                .queryParam("duration", duration)
                .queryParam("maxBitRate", bitRate.getLeft())
                .queryParam("size", bitRate.getRight().width + "x" + bitRate.getRight().height);

        if (signedRestUrls || authentication instanceof JWTAuthenticationToken) {
            builder = jwtSecurityService.addJWTToken(authentication.getName(), builder, linkExpiration);
        }
        return basePath + builder.toUriString();
    }

}
