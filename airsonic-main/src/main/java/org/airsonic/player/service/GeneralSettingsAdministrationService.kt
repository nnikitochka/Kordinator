package org.airsonic.player.service

import org.airsonic.player.command.GeneralSettingsCommand
import org.airsonic.player.domain.Theme
import java.util.Locale

/** Reads and applies general server settings. */
class GeneralSettingsAdministrationService(
    private val settingsService: SettingsService,
    private val playlistService: PlaylistService,
) {
    val availableLocales: Array<Locale>
        get() = settingsService.availableLocales

    val settings: GeneralSettingsCommand
        get() {
            val command = GeneralSettingsCommand()
            command.setCoverArtFileTypes(settingsService.coverArtFileTypes)
            command.setCoverArtSource(settingsService.coverArtSource)
            command.setCoverArtConcurrency(settingsService.coverArtConcurrency)
            command.setCoverArtQuality(settingsService.coverArtQuality)
            command.setIgnoredArticles(settingsService.ignoredArticles)
            command.setGenreSeparators(settingsService.genreSeparators)
            command.setShortcuts(settingsService.shortcuts)
            command.setIndex(settingsService.indexString)
            command.setPlaylistFolder(settingsService.playlistFolder)
            command.setMusicFileTypes(settingsService.musicFileTypes)
            command.setVideoFileTypes(settingsService.videoFileTypes)
            command.setSortAlbumsByYear(settingsService.isSortAlbumsByYear())
            command.setGettingStartedEnabled(settingsService.isGettingStartedEnabled())
            command.setWelcomeTitle(settingsService.welcomeTitle)
            command.setWelcomeSubtitle(settingsService.welcomeSubtitle)
            command.setWelcomeMessage(settingsService.welcomeMessage)
            command.setLoginMessage(settingsService.loginMessage)
            command.setSessionDuration(settingsService.sessionDuration)

            val themes = settingsService.availableThemes
            command.setThemes(themes)
            val currentThemeId = settingsService.themeId
            for (index in themes.indices) {
                if (currentThemeId == themes[index].id) {
                    command.setThemeIndex(index.toString())
                    break
                }
            }

            val currentLocale = settingsService.locale
            val locales = settingsService.availableLocales
            val localeStrings = Array(locales.size) { index ->
                if (currentLocale == locales[index]) command.setLocaleIndex(index.toString())
                locales[index].getDisplayName(locales[index])
            }
            command.setLocales(localeStrings)
            return command
    }

    fun save(command: GeneralSettingsCommand) {
        val theme = settingsService.availableThemes[command.getThemeIndex().toInt()]
        val locale = settingsService.availableLocales[command.getLocaleIndex().toInt()]

        // A changed cover source requires at least one full scan.
        if (settingsService.coverArtSource != command.getCoverArtSource() && !settingsService.fullScan) {
            settingsService.setFullScan(true)
            settingsService.setClearFullScanSettingAfterScan(true)
        }

        settingsService.setIndexString(command.getIndex())
        settingsService.setIgnoredArticles(command.getIgnoredArticles())
        settingsService.setGenreSeparators(command.getGenreSeparators())
        settingsService.setShortcuts(command.getShortcuts())
        settingsService.setPlaylistFolder(command.getPlaylistFolder())
        playlistService.addPlaylistFolderWatcher()
        playlistService.importPlaylists()
        settingsService.setMusicFileTypes(command.getMusicFileTypes())
        settingsService.setVideoFileTypes(command.getVideoFileTypes())
        settingsService.setCoverArtFileTypes(command.getCoverArtFileTypes())
        settingsService.setCoverArtSource(command.getCoverArtSource())
        settingsService.setCoverArtConcurrency(command.getCoverArtConcurrency())
        settingsService.setCoverArtQuality(command.getCoverArtQuality())
        settingsService.setSortAlbumsByYear(command.isSortAlbumsByYear())
        settingsService.setGettingStartedEnabled(command.isGettingStartedEnabled())
        settingsService.setWelcomeTitle(command.getWelcomeTitle())
        settingsService.setWelcomeSubtitle(command.getWelcomeSubtitle())
        settingsService.setWelcomeMessage(command.getWelcomeMessage())
        settingsService.setLoginMessage(command.getLoginMessage())
        settingsService.setSessionDuration(command.getSessionDuration())
        settingsService.setThemeId(theme.id)
        settingsService.setLocale(locale)
        settingsService.save()
    }
}
