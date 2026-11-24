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
            command.coverArtFileTypes = settingsService.coverArtFileTypes
            command.coverArtSource = settingsService.coverArtSource
            command.coverArtConcurrency = settingsService.coverArtConcurrency
            command.coverArtQuality = settingsService.coverArtQuality
            command.ignoredArticles = settingsService.ignoredArticles
            command.genreSeparators = settingsService.genreSeparators
            command.shortcuts = settingsService.shortcuts
            command.index = settingsService.indexString
            command.playlistFolder = settingsService.playlistFolder
            command.musicFileTypes = settingsService.musicFileTypes
            command.videoFileTypes = settingsService.videoFileTypes
            command.sortAlbumsByYear = settingsService.isSortAlbumsByYear()
            command.gettingStartedEnabled = settingsService.isGettingStartedEnabled()
            command.welcomeTitle = settingsService.welcomeTitle
            command.welcomeSubtitle = settingsService.welcomeSubtitle
            command.welcomeMessage = settingsService.welcomeMessage
            command.loginMessage = settingsService.loginMessage
            command.sessionDuration = settingsService.sessionDuration

            val themes = settingsService.availableThemes
            command.themes = themes
            val currentThemeId = settingsService.themeId
            for (index in themes.indices) {
                if (currentThemeId == themes[index].id) {
                    command.themeIndex = index.toString()
                    break
                }
            }

            val currentLocale = settingsService.locale
            val locales = settingsService.availableLocales
            val localeStrings = Array(locales.size) { index ->
                if (currentLocale == locales[index]) command.localeIndex = index.toString()
                locales[index].getDisplayName(locales[index])
            }
            command.locales = localeStrings
            return command
    }

    fun save(command: GeneralSettingsCommand) {
        val theme = settingsService.availableThemes[command.themeIndex!!.toInt()]
        val locale = settingsService.availableLocales[command.localeIndex!!.toInt()]

        // A changed cover source requires at least one full scan.
        if (settingsService.coverArtSource != command.coverArtSource && !settingsService.fullScan) {
            settingsService.setFullScan(true)
            settingsService.setClearFullScanSettingAfterScan(true)
        }

        settingsService.setIndexString(command.index)
        settingsService.setIgnoredArticles(command.ignoredArticles)
        settingsService.setGenreSeparators(command.genreSeparators)
        settingsService.setShortcuts(command.shortcuts)
        settingsService.setPlaylistFolder(command.playlistFolder)
        playlistService.addPlaylistFolderWatcher()
        playlistService.importPlaylists()
        settingsService.setMusicFileTypes(command.musicFileTypes)
        settingsService.setVideoFileTypes(command.videoFileTypes)
        settingsService.setCoverArtFileTypes(command.coverArtFileTypes)
        settingsService.setCoverArtSource(command.coverArtSource)
        settingsService.setCoverArtConcurrency(command.coverArtConcurrency)
        settingsService.setCoverArtQuality(command.coverArtQuality)
        settingsService.setSortAlbumsByYear(command.sortAlbumsByYear)
        settingsService.setGettingStartedEnabled(command.gettingStartedEnabled)
        settingsService.setWelcomeTitle(command.welcomeTitle)
        settingsService.setWelcomeSubtitle(command.welcomeSubtitle)
        settingsService.setWelcomeMessage(command.welcomeMessage)
        settingsService.setLoginMessage(command.loginMessage)
        settingsService.setSessionDuration(command.sessionDuration)
        settingsService.setThemeId(theme.id)
        settingsService.setLocale(locale)
        settingsService.save()
    }
}
