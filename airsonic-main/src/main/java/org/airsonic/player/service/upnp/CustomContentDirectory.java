package org.airsonic.player.service.upnp;

import com.google.common.collect.Lists;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.BrowseResult;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.SortCriterion;
import org.seamless.util.MimeType;

/**
 * @version $Id: TagBasedContentDirectory.java 3739 2013-12-03 11:55:01Z sindre_mehus $
 */
public abstract class CustomContentDirectory extends AbstractContentDirectoryService {

    protected static final String CONTAINER_ID_ROOT = "0";

    protected SettingsService settingsService;
    private PlayerService playerService;
    private TranscodingService transcodingService;
    protected JWTSecurityService jwtSecurityService;

    public CustomContentDirectory() {
        super(Lists.newArrayList("*"), Lists.newArrayList());
    }

    protected Res createResourceForSong(MediaFile song) {
        Player player = playerService.getGuestPlayer();

        StringBuilder path = new StringBuilder("ext/stream?id=")
                .append(song.getId())
                .append("&player=")
                .append(player.getId());
        if (song.isVideo()) {
            path.append("&format=").append(TranscodingService.FORMAT_RAW);
        }

        String signedPath = jwtSecurityService.addJWTToken(User.USERNAME_ANONYMOUS, path.toString());
        String url = getBaseUrl() + signedPath;

        String suffix = song.isVideo() ? FilenameUtils.getExtension(song.getPath()) : transcodingService.getSuffix(player, song, null);
        String mimeTypeString = StringUtil.getMimeType(suffix);
        MimeType mimeType = mimeTypeString == null ? null : MimeType.valueOf(mimeTypeString);

        Res res = new Res(mimeType, null, url);
        res.setDuration(formatDuration(song.getDuration()));
        return res;
    }

    private String formatDuration(Double seconds) {
        if (seconds == null) {
            return null;
        }
        return StringUtil.formatDuration((long) (seconds * 1000), true);
    }

    protected String getBaseUrl() {
        String dlnaBaseLANURL = settingsService.getDlnaBaseLANURL();
        if (StringUtils.isBlank(dlnaBaseLANURL)) {
            throw new RuntimeException("DLNA Base LAN URL is not set correctly");
        }
        return StringUtils.appendIfMissing(dlnaBaseLANURL, "/");
    }

    protected BrowseResult createBrowseResult(DIDLContent didl, int count, int totalMatches) throws Exception {
        return new BrowseResult(new DIDLParser().generate(didl), count, totalMatches);
    }

    @Override
    public BrowseResult search(String containerId,
                               String searchCriteria, String filter,
                               long firstResult, long maxResults,
                               SortCriterion[] orderBy) throws ContentDirectoryException {
        // You can override this method to implement searching!
        return super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
    }

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setTranscodingService(TranscodingService transcodingService) {
        this.transcodingService = transcodingService;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setJwtSecurityService(JWTSecurityService jwtSecurityService) {
        this.jwtSecurityService = jwtSecurityService;
    }
}
