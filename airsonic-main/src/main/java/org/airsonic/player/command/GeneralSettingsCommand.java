package org.airsonic.player.command;

import org.airsonic.player.controller.GeneralSettingsController;
import org.airsonic.player.domain.CoverArtSource;
import org.airsonic.player.domain.Theme;

/**
 * Command used in {@link GeneralSettingsController}.
 *
 * @author Sindre Mehus
 */
public class GeneralSettingsCommand {

    private String playlistFolder;
    private String musicFileTypes;
    private String videoFileTypes;
    private String coverArtFileTypes;
    private CoverArtSource coverArtSource;
    private Integer coverArtQuality;
    private Integer coverArtConcurrency;
    private String index;
    private String ignoredArticles;
    private String genreSeparators;
    private String shortcuts;
    private boolean sortAlbumsByYear;
    private boolean gettingStartedEnabled;
    private String welcomeTitle;
    private String welcomeSubtitle;
    private String welcomeMessage;
    private String loginMessage;
    private String sessionDuration;
    private String localeIndex;
    private String[] locales;
    private String themeIndex;
    private Theme[] themes;

    public String getPlaylistFolder() {
        return playlistFolder;
    }

    public void setPlaylistFolder(String playlistFolder) {
        this.playlistFolder = playlistFolder;
    }

    public String getMusicFileTypes() {
        return musicFileTypes;
    }

    public void setMusicFileTypes(String musicFileTypes) {
        this.musicFileTypes = musicFileTypes;
    }

    public String getVideoFileTypes() {
        return videoFileTypes;
    }

    public void setVideoFileTypes(String videoFileTypes) {
        this.videoFileTypes = videoFileTypes;
    }

    public String getCoverArtFileTypes() {
        return coverArtFileTypes;
    }

    public void setCoverArtFileTypes(String coverArtFileTypes) {
        this.coverArtFileTypes = coverArtFileTypes;
    }

    public CoverArtSource getCoverArtSource() {
        return coverArtSource;
    }

    public void setCoverArtSource(CoverArtSource coverArtSource) {
        this.coverArtSource = coverArtSource;
    }

    public Integer getCoverArtQuality() {
        return coverArtQuality;
    }

    public void setCoverArtQuality(Integer coverArtQuality) {
        this.coverArtQuality = coverArtQuality;
    }

    public Integer getCoverArtConcurrency() {
        return coverArtConcurrency;
    }

    public void setCoverArtConcurrency(Integer coverArtConcurrency) {
        this.coverArtConcurrency = coverArtConcurrency;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIgnoredArticles() {
        return ignoredArticles;
    }

    public void setIgnoredArticles(String ignoredArticles) {
        this.ignoredArticles = ignoredArticles;
    }

    public String getGenreSeparators() {
        return genreSeparators;
    }

    public void setGenreSeparators(String genreSeparators) {
        this.genreSeparators = genreSeparators;
    }

    public String getShortcuts() {
        return shortcuts;
    }

    public void setShortcuts(String shortcuts) {
        this.shortcuts = shortcuts;
    }

    public String getWelcomeTitle() {
        return welcomeTitle;
    }

    public void setWelcomeTitle(String welcomeTitle) {
        this.welcomeTitle = welcomeTitle;
    }

    public String getWelcomeSubtitle() {
        return welcomeSubtitle;
    }

    public void setWelcomeSubtitle(String welcomeSubtitle) {
        this.welcomeSubtitle = welcomeSubtitle;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getLoginMessage() {
        return loginMessage;
    }

    public void setLoginMessage(String loginMessage) {
        this.loginMessage = loginMessage;
    }

    public String getSessionDuration() {
        return sessionDuration;
    }

    public void setSessionDuration(String sessionDuration) {
        this.sessionDuration = sessionDuration;
    }

    public String getLocaleIndex() {
        return localeIndex;
    }

    public void setLocaleIndex(String localeIndex) {
        this.localeIndex = localeIndex;
    }

    public String[] getLocales() {
        return locales;
    }

    public void setLocales(String[] locales) {
        this.locales = locales;
    }

    public String getThemeIndex() {
        return themeIndex;
    }

    public void setThemeIndex(String themeIndex) {
        this.themeIndex = themeIndex;
    }

    public Theme[] getThemes() {
        return themes;
    }

    public void setThemes(Theme[] themes) {
        this.themes = themes;
    }

    public void setReloadNeeded(boolean reloadNeeded) {
    }

    public boolean isSortAlbumsByYear() {
        return sortAlbumsByYear;
    }

    public void setSortAlbumsByYear(boolean sortAlbumsByYear) {
        this.sortAlbumsByYear = sortAlbumsByYear;
    }

    public boolean isGettingStartedEnabled() {
        return gettingStartedEnabled;
    }

    public void setGettingStartedEnabled(boolean gettingStartedEnabled) {
        this.gettingStartedEnabled = gettingStartedEnabled;
    }

}
