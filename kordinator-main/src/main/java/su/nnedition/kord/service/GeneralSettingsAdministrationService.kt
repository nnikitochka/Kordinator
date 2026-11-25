package su.nnedition.kord.service

import su.nnedition.kord.command.GeneralSettingsCommand
import su.nnedition.kord.domain.Theme
import java.util.Locale

/** Reads and applies general server settings. */
class GeneralSettingsAdministrationService(
    private val settingsService: SettingsService,
    private val playlistService: PlaylistService,
) {
    val availableLocales: Array<Locale>
        get() = settingsService.getAvailableLocales()

    val settings: GeneralSettingsCommand
        get() {
            val command = GeneralSettingsCommand()
            command.coverArtFileTypes = settingsService.getCoverArtFileTypes()
            command.coverArtSource = settingsService.getCoverArtSource()
            command.coverArtConcurrency = settingsService.getCoverArtConcurrency()
            command.coverArtQuality = settingsService.getCoverArtQuality()
            command.ignoredArticles = settingsService.getIgnoredArticles()
            command.genreSeparators = settingsService.getGenreSeparators()
            command.shortcuts = settingsService.getShortcuts()
            command.index = settingsService.getIndexString()
            command.playlistFolder = settingsService.getPlaylistFolder()
            command.musicFileTypes = settingsService.getMusicFileTypes()
            command.videoFileTypes = settingsService.getVideoFileTypes()
            command.sortAlbumsByYear = settingsService.isSortAlbumsByYear()
            command.gettingStartedEnabled = settingsService.isGettingStartedEnabled()
            command.welcomeTitle = settingsService.getWelcomeTitle()
            command.welcomeSubtitle = settingsService.getWelcomeSubtitle()
            command.welcomeMessage = settingsService.getWelcomeMessage()
            command.loginMessage = settingsService.getLoginMessage()
            command.sessionDuration = settingsService.getSessionDuration()

            val themes = settingsService.getAvailableThemes()
            command.themes = themes
            val currentThemeId = settingsService.getThemeId()
            for (index in themes.indices) {
                if (currentThemeId == themes[index].id) {
                    command.themeIndex = index.toString()
                    break
                }
            }

            val currentLocale = settingsService.getLocale()
            val locales = settingsService.getAvailableLocales()
            val localeStrings = Array(locales.size) { index ->
                if (currentLocale == locales[index]) command.localeIndex = index.toString()
                locales[index].getDisplayName(locales[index])
            }
            command.locales = localeStrings
            return command
    }

    fun save(command: GeneralSettingsCommand) {
        val theme = settingsService.getAvailableThemes()[command.themeIndex!!.toInt()]
        val locale = settingsService.getAvailableLocales()[command.localeIndex!!.toInt()]

        // A changed cover source requires at least one full scan.
        if (settingsService.getCoverArtSource() != command.coverArtSource && !settingsService.getFullScan()) {
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
        settingsService.setCoverArtSource(requireNotNull(command.coverArtSource))
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
