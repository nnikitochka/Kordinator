package org.airsonic.player.service;

import org.airsonic.player.dao.ShareDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Share;
import org.airsonic.player.domain.User;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * Provides services for sharing media.
 *
 * @author Sindre Mehus
 * @see Share
 */
@Service
public class ShareService {

    private static final Logger LOG = LoggerFactory.getLogger(ShareService.class);

    private ShareDao shareDao;
    private SecurityService securityService;
    private MediaFileService mediaFileService;
    private JWTSecurityService jwtSecurityService;

    public ShareService(ShareDao shareDao, SecurityService securityService,
                        MediaFileService mediaFileService, JWTSecurityService jwtSecurityService) {
        this.shareDao = shareDao;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.jwtSecurityService = jwtSecurityService;
    }

    public List<Share> getAllShares() {
        return shareDao.getAllShares();
    }

    public List<Share> getSharesForUser(User user) {
        return getAllShares().stream().filter(share -> user.isAdminRole() || ObjectUtils.equals(user.getUsername(), share.getUsername())).collect(toList());
    }

    public Share getShareById(int id) {
        return shareDao.getShareById(id);
    }

    public Share getShareByName(String name) {
        return shareDao.getShareByName(name);
    }

    public List<MediaFile> getSharedFiles(int id, List<MusicFolder> musicFolders) {
        return shareDao.getSharedFiles(id, musicFolders).stream().map(mediaFileService::getMediaFile).filter(Objects::nonNull).collect(toList());
    }

    public Share createShare(HttpServletRequest request, List<MediaFile> files) {

        Share share = new Share();
        share.setName(RandomStringUtils.random(5, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        share.setCreated(Instant.now());
        share.setUsername(securityService.getCurrentUsername(request));

        share.setExpires(Instant.now().plus(ChronoUnit.YEARS.getDuration()));

        shareDao.createShare(share);
        shareDao.createSharedFiles(share.getId(), files.stream().map(f -> f.getId()).collect(toList()));
        LOG.info("Created share '{}' with {} file(s).", share.getName(), files.size());

        return share;
    }

    public void updateShare(Share share) {
        shareDao.updateShare(share);
    }

    public void deleteShare(int id) {
        shareDao.deleteShare(id);
    }

    public String getShareUrl(HttpServletRequest request, Share share) {
        String shareUrl = "ext/share/" + share.getName();
        return NetworkService.getBaseUrl(request) + jwtSecurityService
                .addJWTToken(User.USERNAME_ANONYMOUS, UriComponentsBuilder.fromUriString(shareUrl), share.getExpires())
                .build().toUriString();
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setShareDao(ShareDao shareDao) {
        this.shareDao = shareDao;
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setJwtSecurityService(JWTSecurityService jwtSecurityService) {
        this.jwtSecurityService = jwtSecurityService;
    }
}
