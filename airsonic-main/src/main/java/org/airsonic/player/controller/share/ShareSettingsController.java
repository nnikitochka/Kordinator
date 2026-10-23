package org.airsonic.player.controller.share;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Share;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.ShareService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Controller for the page used to administrate the set of shared media.
 */
@Controller
@RequestMapping("/shareSettings")
public class ShareSettingsController {
    private final ShareService shareService;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;

    public ShareSettingsController(ShareService shareService, SecurityService securityService, MediaFileService mediaFileService, MediaFolderService mediaFolderService) {
        this.shareService = shareService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
    }

    @GetMapping
    public String doGet(HttpServletRequest request, Model model) {

        Map<String, Object> map = new HashMap<>();
        map.put("shareInfos", getShareInfos(request));
        map.put("user", securityService.getCurrentUser(request));

        model.addAttribute("model", map);
        return "shareSettings";
    }

    @PostMapping
    public String doPost(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        handleParameters(request);

        redirectAttributes.addFlashAttribute("settings_toast", true);

        return "redirect:shareSettings.view";
    }

    private void handleParameters(HttpServletRequest request) {
        User user = securityService.getCurrentUser(request);
        for (Share share : shareService.getSharesForUser(user)) {
            int id = share.getId();

            String description = getParameter(request, "description", id);
            boolean delete = getParameter(request, "delete", id) != null;
            String expireIn = getParameter(request, "expireIn", id);

            if (delete) {
                shareService.deleteShare(id);
            } else {
                if (expireIn != null) {
                    share.setExpires(parseExpireIn(expireIn));
                }
                share.setDescription(description);
                shareService.updateShare(share);
            }
        }

        boolean deleteExpired = ServletRequestUtils.getBooleanParameter(request, "deleteExpired", false);
        if (deleteExpired) {
            Instant now = Instant.now();
            for (Share share : shareService.getSharesForUser(user)) {
                Instant expires = share.getExpires();
                if (expires != null && expires.isBefore(now)) {
                    shareService.deleteShare(share.getId());
                }
            }
        }
    }

    private List<ShareInfo> getShareInfos(HttpServletRequest request) {
        List<ShareInfo> result = new ArrayList<>();
        User user = securityService.getCurrentUser(request);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(user.getUsername());

        for (Share share : shareService.getSharesForUser(user)) {
            List<MediaFile> files = shareService.getSharedFiles(share.getId(), musicFolders);
            if (!files.isEmpty()) {
                MediaFile file = files.getFirst();
                result.add(new ShareInfo(shareService.getShareUrl(request, share), share, file.isDirectory() ? file :
                        mediaFileService
                        .getParentOf(file)));
            }
        }
        return result;
    }


    private String getParameter(HttpServletRequest request, String name, int id) {
        return StringUtils.trimToNull(request.getParameter(name + "[" + id + "]"));
    }

    private Instant parseExpireIn(String expireIn) {
        long days = Long.parseLong(expireIn);
        if (days == 0L) {
            return null;
        }

        return Instant.now().plus(days, ChronoUnit.DAYS);
    }
}
