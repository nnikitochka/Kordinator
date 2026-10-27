package org.airsonic.player.controller;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import org.airsonic.player.controller.captions.CaptionInfo;
import org.airsonic.player.controller.captions.CaptionsController;
import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.NetworkService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller for the page used to play videos.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/videoPlayer")
public class VideoPlayerController {
    public static final int DEFAULT_BIT_RATE = 1500;
    public static final Set<Integer> BIT_RATES = ImmutableSet.of(200, 300, 400, 500, 700, 1000, 1200, DEFAULT_BIT_RATE, 2000, 3000, 5000);
    private static final Set<String> STREAMABLE_FORMATS = ImmutableSet.of("mp4", "m4v");
    private static final Set<String> CASTABLE_FORMATS = ImmutableSet.of("mp4", "m4v", "mkv");

    private final MediaFileService mediaFileService;
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final CaptionsController captionsController;
    private final JWTSecurityService jwtSecurityService;
    private final BookmarkService bookmarkService;
    private final SettingsService settingsService;

    public VideoPlayerController(MediaFileService mediaFileService, PlayerService playerService, SecurityService securityService, CaptionsController captionsController, JWTSecurityService jwtSecurityService, BookmarkService bookmarkService, SettingsService settingsService) {
        this.mediaFileService = mediaFileService;
        this.playerService = playerService;
        this.securityService = securityService;
        this.captionsController = captionsController;
        this.jwtSecurityService = jwtSecurityService;
        this.bookmarkService = bookmarkService;
        this.settingsService = settingsService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        User user = securityService.getCurrentUser(request);
        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        MediaFile file = mediaFileService.getMediaFile(id);
        mediaFileService.populateStarredDate(file, user.getUsername());

        Long position = ServletRequestUtils.getLongParameter(request, "position");
        if (position == null) {
            Bookmark bookmark = bookmarkService.getBookmark(user.getUsername(), id);
            if (bookmark != null) {
                position = bookmark.getPositionMillis();
            } else {
                position = 0L;
            }
        }
        UserSettings settings = settingsService.getUserSettings(user.getUsername());

        Integer playerId = playerService.getPlayer(request, response).getId();
        String url = NetworkService.getBaseUrl(request);
        boolean streamable = isStreamable(file);
        boolean castable = isCastable(file);

        Pair<String, Map<String, String>> streamUrls = getStreamUrls(file, user, url, streamable, playerId);
        List<CaptionInfo> captions = captionsController.listCaptions(file, NetworkService.getBaseUrl(request));

        Map<String, Object> map = new HashMap<>();
        map.put("video", file);
        map.put("position", position);
        map.put("autoBookmark", settings.getAutoBookmark());
        map.put("videoBookmarkFrequency", settings.getVideoBookmarkFrequency());
        map.put("streamable", streamable);
        map.put("castable", castable);
        map.put("captions", captions);
        map.put("streamUrls", streamUrls.getRight());
        map.put("contentType", !streamable ? "application/x-mpegurl"
                : StringUtil.getMimeType(MoreFiles.getFileExtension(file.getRelativePath())));
        map.put("remoteStreamUrl", streamUrls.getRight().get("remoteStreamUrl"));
        map.put("remoteCoverArtUrl", url + jwtSecurityService.addJWTToken(user.getUsername(), "ext/coverArt.view?id=" + file.getId()));
        map.put("remoteCaptionsUrl", url + jwtSecurityService.addJWTToken(user.getUsername(), "ext/captions/list?id=" + file.getId()));
        // map.put("bitRates", BIT_RATES);
        map.put("defaultBitRate", streamUrls.getLeft());
        map.put("user", user);

        return new ModelAndView("videoPlayer", "model", map);
    }

    public Pair<String, Map<String, String>> getStreamUrls(MediaFile file, User user, String baseUrl,
            boolean streamable, int playerId) {
        Map<String, String> streamUrls;
        String defaultBitRate;
        if (streamable) {
            String streamUrlWithoutBitrates = baseUrl + "stream?id=" + file.getId() + "&player=" + playerId;
            streamUrls = Stream
                    .concat(Stream.of(Pair.of("Original", streamUrlWithoutBitrates + "&format=raw")),
                            BIT_RATES.stream()
                                .map(b -> Pair.of(b + " Kbps", streamUrlWithoutBitrates + "&format=mp4&maxBitRate=" + b)))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (a, b) -> a, LinkedHashMap::new));
            streamUrls.put("remoteStreamUrl", baseUrl + jwtSecurityService.addJWTToken(user.getUsername(),
                    "ext/stream?id=" + file.getId() + "&player=" + playerId + "&format=raw"));
            defaultBitRate = "Original";
        } else {
            String streamUrlWithoutBitrates = baseUrl + "hls/hls.m3u8?id=" + file.getId() + "&player=" + playerId;
            streamUrls = BIT_RATES.stream()
                    .map(b -> Pair.of(b + " Kbps", streamUrlWithoutBitrates + "&maxBitRate=" + b))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (a, b) -> a, LinkedHashMap::new));
            streamUrls.put("remoteStreamUrl", baseUrl + jwtSecurityService.addJWTToken(user.getUsername(),
                    "ext/hls/hls.m3u8?id=" + file.getId() + "&player=" + playerId)); //+ "&maxBitRate=" + DEFAULT_BIT_RATE));
            defaultBitRate = DEFAULT_BIT_RATE + " Kbps";
        }
        return Pair.of(defaultBitRate, streamUrls);
    }

    public boolean isStreamable(MediaFile file) {
        if (!STREAMABLE_FORMATS.contains(StringUtils.lowerCase(file.getFormat())))
            return false;

        MetaData metaData = captionsController.getVideoMetaData(file);
        if (metaData == null) return true;

        if (!metaData.getVideoTracks().isEmpty() && !metaData.getVideoTracks().getFirst().isStreamable())
            return false;

        return metaData.getAudioTracks().isEmpty() || metaData.getAudioTracks().getFirst().isStreamable();
    }

    public static boolean isCastable(MediaFile file) {
        return CASTABLE_FORMATS.contains(StringUtils.lowerCase(file.getFormat()));
    }
}
