package su.nnedition.kord.runtime.migrations

import java.sql.SQLException
import java.sql.Statement
import java.util.ArrayList
import java.util.Optional
import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.CustomChangeException
import liquibase.exception.DatabaseException
import liquibase.exception.SetupException
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.InsertStatement
import su.nnedition.kord.domain.AlbumListType
import su.nnedition.kord.domain.AvatarScheme
import su.nnedition.kord.domain.TranscodeScheme
import su.nnedition.kord.domain.UserSettings
import su.nnedition.kord.util.StringUtil
import su.nnedition.kord.util.Util

class UserSettingsJsonTablePopulation : CustomSqlChange {
    override fun getConfirmationMessage() = "Table user_settings2 (for json) populated from user_settings"

    @Throws(SetupException::class)
    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    @Throws(CustomChangeException::class)
    override fun generateStatements(database: Database): Array<SqlStatement> {
        val settingsRows = ArrayList<UserSettings>()
        val connection = database.connection as? JdbcConnection
        if (connection != null) {
            try {
                connection.createStatement().use { statement: Statement ->
                    statement.executeQuery("SELECT * FROM user_settings").use { rows ->
                        while (rows.next()) settingsRows += mapRow(rows)
                    }
                }
            } catch (exception: DatabaseException) {
                throw CustomChangeException(exception)
            } catch (exception: SQLException) {
                throw CustomChangeException(exception)
            }
        }

        return Array(settingsRows.size) { index ->
            val settings = settingsRows[index]
            InsertStatement(database.defaultCatalogName, database.defaultSchemaName, "user_settings2")
                .addColumnValue("username", settings.username)
                .addColumnValue("settings", Util.toJson(settings))
        }
    }

    private fun mapRow(result: java.sql.ResultSet): UserSettings = UserSettings(result.getString("username")).apply {
        locale = StringUtil.parseLocale(result.getString("locale"))
        themeId = result.getString("theme_id")
        finalVersionNotificationEnabled = result.getBoolean("final_version_notification")
        betaVersionNotificationEnabled = result.getBoolean("beta_version_notification")
        songNotificationEnabled = result.getBoolean("song_notification")

        mainVisibility.trackNumberVisible = result.getBoolean("main_track_number")
        mainVisibility.artistVisible = result.getBoolean("main_artist")
        mainVisibility.albumVisible = result.getBoolean("main_album")
        mainVisibility.genreVisible = result.getBoolean("main_genre")
        mainVisibility.yearVisible = result.getBoolean("main_year")
        mainVisibility.bitRateVisible = result.getBoolean("main_bit_rate")
        mainVisibility.durationVisible = result.getBoolean("main_duration")
        mainVisibility.formatVisible = result.getBoolean("main_format")
        mainVisibility.fileSizeVisible = result.getBoolean("main_file_size")

        playlistVisibility.trackNumberVisible = result.getBoolean("playlist_track_number")
        playlistVisibility.artistVisible = result.getBoolean("playlist_artist")
        playlistVisibility.albumVisible = result.getBoolean("playlist_album")
        playlistVisibility.genreVisible = result.getBoolean("playlist_genre")
        playlistVisibility.yearVisible = result.getBoolean("playlist_year")
        playlistVisibility.bitRateVisible = result.getBoolean("playlist_bit_rate")
        playlistVisibility.durationVisible = result.getBoolean("playlist_duration")
        playlistVisibility.formatVisible = result.getBoolean("playlist_format")
        playlistVisibility.fileSizeVisible = result.getBoolean("playlist_file_size")

        lastFmEnabled = result.getBoolean("last_fm_enabled")
        listenBrainzEnabled = result.getBoolean("listenbrainz_enabled")
        transcodeScheme = TranscodeScheme.valueOf(result.getString("transcode_scheme"))
        showNowPlayingEnabled = result.getBoolean("show_now_playing")
        selectedMusicFolderId = result.getInt("selected_music_folder_id")
        partyModeEnabled = result.getBoolean("party_mode_enabled")
        nowPlayingAllowed = result.getBoolean("now_playing_allowed")
        avatarScheme = AvatarScheme.valueOf(result.getString("avatar_scheme"))
        systemAvatarId = result.getObject("system_avatar_id") as? Int
        changed = Optional.ofNullable(result.getTimestamp("changed")).map { it.toInstant() }.orElse(null)
        showArtistInfoEnabled = result.getBoolean("show_artist_info")
        autoHidePlayQueue = result.getBoolean("auto_hide_play_queue")
        viewAsList = result.getBoolean("view_as_list")
        defaultAlbumList = AlbumListType.fromId(result.getString("default_album_list")) ?: AlbumListType.RANDOM
        queueFollowingSongs = result.getBoolean("queue_following_songs")
        showSideBar = result.getBoolean("show_side_bar")
        keyboardShortcutsEnabled = result.getBoolean("keyboard_shortcuts_enabled")
        paginationSizeFiles = result.getInt("pagination_size")
        paginationSizeFolders = result.getInt("pagination_size")
        paginationSizePlaylist = result.getInt("pagination_size")
        paginationSizePlayqueue = result.getInt("pagination_size")
    }
}
