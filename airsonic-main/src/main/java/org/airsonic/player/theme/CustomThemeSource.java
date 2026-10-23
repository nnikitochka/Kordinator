package org.airsonic.player.theme;

import org.airsonic.player.domain.Theme;
import org.airsonic.player.service.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.ui.context.support.ResourceBundleThemeSource;
import org.springframework.ui.context.support.UiApplicationContextUtils;

/**
 * Theme source implementation which uses two resource bundles: the
 * theme specific (e.g., barents.properties), and the default (default.properties).
 */
@Component(UiApplicationContextUtils.THEME_SOURCE_BEAN_NAME)
public class CustomThemeSource extends ResourceBundleThemeSource {

    private SettingsService settingsService;
    private String basenamePrefix;

    @NotNull
    @Override
    protected MessageSource createMessageSource(@NotNull String basename) {
        ResourceBundleMessageSource messageSource = (ResourceBundleMessageSource) super.createMessageSource(basename);

        // Create parent theme recursively.
        for (Theme theme : settingsService.getAvailableThemes()) {
            if ((basenamePrefix + theme.getId()).equals(basename) && theme.getParent() != null) {
                String parent = basenamePrefix + theme.getParent();
                messageSource.setParentMessageSource(createMessageSource(parent));
                break;
            }
        }
        return messageSource;
    }

    @Autowired
    @Value("org.airsonic.player.theme.")
    @Override
    public void setBasenamePrefix(String basenamePrefix) {
        this.basenamePrefix = basenamePrefix;
        super.setBasenamePrefix(basenamePrefix);
    }

    @Autowired
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
}
