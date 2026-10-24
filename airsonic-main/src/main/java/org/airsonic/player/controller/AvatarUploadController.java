package org.airsonic.player.controller;

import org.airsonic.player.domain.Avatar;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.jakarta.JakartaServletDiskFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller which receives uploaded avatar images.
 */
@Controller
@RequestMapping("/avatarUpload")
public class AvatarUploadController {
    private static final Logger LOG = LoggerFactory.getLogger(AvatarUploadController.class);
    private static final int MAX_AVATAR_SIZE = 64;

    private final SettingsService settingsService;
    private final SecurityService securityService;

    public AvatarUploadController(SettingsService settingsService, SecurityService securityService) {
        this.settingsService = settingsService;
        this.securityService = securityService;
    }

    @PostMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request) throws Exception {
        String username = securityService.getCurrentUsername(request);

        // Check that we have a file upload request.
        if (!JakartaServletDiskFileUpload.isMultipartContent(request)) {
            throw new Exception("Illegal request.");
        }

        Map<String, Object> map = new HashMap<>();
        JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload();
        List<DiskFileItem> items = upload.parseRequest(request);

        // Look for file items.
        for (DiskFileItem item : items) {
            if (!item.isFormField()) {
                String fileName = item.getName();
                byte[] data = item.get();

                if (StringUtils.isNotBlank(fileName) && data.length > 0) {
                    createAvatar(fileName, data, username, map);
                } else {
                    map.put("error", new Exception("Missing file."));
                    LOG.warn("Failed to upload personal image. No file specified.");
                }
                break;
            }
        }

        map.put("username", username);
        map.put("avatar", settingsService.getCustomAvatar(username));
        return new ModelAndView("avatarUploadResult", "model", map);
    }

    private void createAvatar(String fileName, byte[] data, String username, Map<String, Object> map) {

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new IOException("Failed to decode incoming image: " + fileName + " (" + data.length + " bytes).");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            String mimeType = StringUtil.getMimeType(FilenameUtils.getExtension(fileName));
            Path folder = SettingsService.getAirsonicHome().resolve("avatars").resolve(username);
            Files.createDirectories(folder);
            Path fileOnDisk = folder.resolve(fileName + "." + StringUtils.substringAfter(mimeType, "/"));
            // Scale down image if necessary.
            if (width > MAX_AVATAR_SIZE || height > MAX_AVATAR_SIZE) {
                double scaleFactor = MAX_AVATAR_SIZE / (double)Math.max(width, height);
                height = (int) (height * scaleFactor);
                width = (int) (width * scaleFactor);
                image = CoverArtController.scale(image, width, height);
                mimeType = StringUtil.getMimeType("jpeg");
                fileOnDisk = folder.resolve(fileName + ".jpeg");
                ImageIO.write(image, "jpeg", fileOnDisk.toFile());

                map.put("resized", true);
            } else {
                Files.copy(new ByteArrayInputStream(data), fileOnDisk, StandardCopyOption.REPLACE_EXISTING);
            }
            Avatar avatar = new Avatar(0, fileName, Instant.now(), mimeType, width, height, fileOnDisk);
            settingsService.setCustomAvatar(avatar, username);
            LOG.info("Created avatar '{}' ({} bytes) for user {}", fileName, FileUtil.size(fileOnDisk), username);

        } catch (IOException x) {
            LOG.warn("Failed to upload personal image", x);
            map.put("error", x);
        }
    }


}
