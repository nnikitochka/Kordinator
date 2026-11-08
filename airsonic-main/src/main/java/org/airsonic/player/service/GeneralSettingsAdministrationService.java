package org.airsonic.player.service;

import org.airsonic.player.command.GeneralSettingsCommand;
import org.airsonic.player.domain.Theme;

import java.util.Locale;

/**
 * Reads and applies general server settings.
 *
 * @author Sindre Mehus
 */
public class GeneralSettingsAdministrationService {

    private final SettingsService settingsService;
    private final PlaylistService playlistService;

    public GeneralSettingsAdministrationService(SettingsService settingsService, PlaylistService playlistService) {
        this.settingsService = settingsService;
        this.playlistService = playlistService;
    }

    public Locale[] getAvailableLocales() {
        return settingsService.getAvailableLocales();
    }

    public GeneralSettingsCommand getSettings() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setCoverArtFileTypes(settingsService.getCoverArtFileTypes());
        command.setCoverArtSource(settingsService.getCoverArtSource());
        command.setCoverArtConcurrency(settingsService.getCoverArtConcurrency());
        command.setCoverArtQuality(settingsService.getCoverArtQuality());
        command.setIgnoredArticles(settingsService.getIgnoredArticles());
        command.setGenreSeparators(settingsService.getGenreSeparators());
        command.setShortcuts(settingsService.getShortcuts());
        command.setIndex(settingsService.getIndexString());
        command.setPlaylistFolder(settingsService.getPlaylistFolder());
        command.setMusicFileTypes(settingsService.getMusicFileTypes());
        command.setVideoFileTypes(settingsService.getVideoFileTypes());
        command.setSortAlbumsByYear(settingsService.isSortAlbumsByYear());
        command.setGettingStartedEnabled(settingsService.isGettingStartedEnabled());
        command.setWelcomeTitle(settingsService.getWelcomeTitle());
        command.setWelcomeSubtitle(settingsService.getWelcomeSubtitle());
        command.setWelcomeMessage(settingsService.getWelcomeMessage());
        command.setLoginMessage(settingsService.getLoginMessage());
        command.setSessionDuration(settingsService.getSessionDuration());

        Theme[] themes = settingsService.getAvailableThemes();
        command.setThemes(themes);
        String currentThemeId = settingsService.getThemeId();
        for (int i = 0; i < themes.length; i++) {
            if (currentThemeId.equals(themes[i].getId())) {
                command.setThemeIndex(String.valueOf(i));
                break;
            }
        }

        Locale currentLocale = settingsService.getLocale();
        Locale[] locales = settingsService.getAvailableLocales();
        String[] localeStrings = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            localeStrings[i] = locales[i].getDisplayName(locales[i]);

            if (currentLocale.equals(locales[i])) {
                command.setLocaleIndex(String.valueOf(i));
            }
        }
        command.setLocales(localeStrings);

        return command;
    }

    public void save(GeneralSettingsCommand command) {
        int themeIndex = Integer.parseInt(command.getThemeIndex());
        Theme theme = settingsService.getAvailableThemes()[themeIndex];

        int localeIndex = Integer.parseInt(command.getLocaleIndex());
        Locale locale = settingsService.getAvailableLocales()[localeIndex];

        // if cover art source is changing then we need to do at least one full scan
        if (settingsService.getCoverArtSource() != command.getCoverArtSource() && !settingsService.getFullScan()) {
            settingsService.setFullScan(true);
            settingsService.setClearFullScanSettingAfterScan(true);
        }

        settingsService.setIndexString(command.getIndex());
        settingsService.setIgnoredArticles(command.getIgnoredArticles());
        settingsService.setGenreSeparators(command.getGenreSeparators());
        settingsService.setShortcuts(command.getShortcuts());
        settingsService.setPlaylistFolder(command.getPlaylistFolder());
        playlistService.addPlaylistFolderWatcher();
        playlistService.importPlaylists();
        settingsService.setMusicFileTypes(command.getMusicFileTypes());
        settingsService.setVideoFileTypes(command.getVideoFileTypes());
        settingsService.setCoverArtFileTypes(command.getCoverArtFileTypes());
        settingsService.setCoverArtSource(command.getCoverArtSource());
        settingsService.setCoverArtConcurrency(command.getCoverArtConcurrency());
        settingsService.setCoverArtQuality(command.getCoverArtQuality());
        settingsService.setSortAlbumsByYear(command.isSortAlbumsByYear());
        settingsService.setGettingStartedEnabled(command.isGettingStartedEnabled());
        settingsService.setWelcomeTitle(command.getWelcomeTitle());
        settingsService.setWelcomeSubtitle(command.getWelcomeSubtitle());
        settingsService.setWelcomeMessage(command.getWelcomeMessage());
        settingsService.setLoginMessage(command.getLoginMessage());
        settingsService.setSessionDuration(command.getSessionDuration());
        settingsService.setThemeId(theme.getId());
        settingsService.setLocale(locale);
        settingsService.save();
    }

}
