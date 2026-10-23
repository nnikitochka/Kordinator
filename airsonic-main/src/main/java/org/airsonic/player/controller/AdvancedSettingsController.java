package org.airsonic.player.controller;

import org.airsonic.player.command.AdvancedSettingsCommand;
import org.airsonic.player.service.SettingsService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the page used to administrate advanced settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/advancedSettings")
public class AdvancedSettingsController {
    private final SettingsService settingsService;

    public AdvancedSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    protected String formBackingObject(Model model) {
        AdvancedSettingsCommand command = new AdvancedSettingsCommand();
        command.setDownloadLimit(String.valueOf(settingsService.getDownloadBitrateLimit()));
        command.setUploadLimit(String.valueOf(settingsService.getUploadBitrateLimit()));
        command.setLdapEnabled(settingsService.isLdapEnabled());
        command.setLdapUrl(settingsService.getLdapUrl());
        command.setLdapSearchFilter(settingsService.getLdapSearchFilter());
        command.setLdapManagerDn(settingsService.getLdapManagerDn());
        command.setLdapAutoShadowing(settingsService.isLdapAutoShadowing());
        command.setBrand(settingsService.getBrand());

        command.setSmtpServer(settingsService.getSmtpServer());
        command.setSmtpEncryption(settingsService.getSmtpEncryption());
        command.setSmtpPort(settingsService.getSmtpPort());
        command.setSmtpUser(settingsService.getSmtpUser());
        command.setSmtpFrom(settingsService.getSmtpFrom());

        command.setCaptchaEnabled(settingsService.isCaptchaEnabled());
        command.setRecaptchaSiteKey(settingsService.getRecaptchaSiteKey());

        model.addAttribute("command", command);
        return "advancedSettings";
    }

    @PostMapping
    protected String doSubmitAction(@ModelAttribute AdvancedSettingsCommand command, RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute("settings_toast", true);

        try {
            settingsService.setDownloadBitrateLimit(Long.parseLong(command.getDownloadLimit()));
        } catch (NumberFormatException x) { /* Intentionally ignored. */ }
        try {
            settingsService.setUploadBitrateLimit(Long.parseLong(command.getUploadLimit()));
        } catch (NumberFormatException x) { /* Intentionally ignored. */ }

        settingsService.setLdapEnabled(command.isLdapEnabled());
        settingsService.setLdapUrl(command.getLdapUrl());
        settingsService.setLdapSearchFilter(command.getLdapSearchFilter());
        settingsService.setLdapManagerDn(command.getLdapManagerDn());
        settingsService.setLdapAutoShadowing(command.isLdapAutoShadowing());

        if (StringUtils.isNotEmpty(command.getLdapManagerPassword())) {
            settingsService.setLdapManagerPassword(command.getLdapManagerPassword());
        }

        settingsService.setSmtpServer(command.getSmtpServer());
        settingsService.setSmtpEncryption(command.getSmtpEncryption());
        settingsService.setSmtpPort(command.getSmtpPort());
        settingsService.setSmtpUser(command.getSmtpUser());
        settingsService.setSmtpFrom(command.getSmtpFrom());

        if (StringUtils.isNotEmpty(command.getSmtpPassword())) {
            settingsService.setSmtpPassword(command.getSmtpPassword());
        }

        settingsService.setCaptchaEnabled(command.isCaptchaEnabled());
        settingsService.setRecaptchaSiteKey(command.getRecaptchaSiteKey());
        if (StringUtils.isNotEmpty(command.getRecaptchaSecretKey())) {
            settingsService.setRecaptchaSecretKey(command.getRecaptchaSecretKey());
        }

        settingsService.save();

        return "redirect:advancedSettings.view";
    }

}
