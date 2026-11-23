package org.airsonic.player.dao

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.apache.commons.lang3.EnumUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.ResultSet
import java.sql.SQLException

/** Provides database services for music folders. */
open class MusicFolderDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    @Throws(Exception::class)
    open fun register() {
        registerInserts("music_folder", "id", INSERT_COLUMNS.split(", "), MusicFolder::class.java)
    }

    /** Returns all non-deleted music folders. */
    open fun getAllMusicFolders(): List<MusicFolder> = query(
        "select $QUERY_COLUMNS from music_folder where id >= 0",
        MUSICFOLDER_ROW_MAPPER,
    )

    open fun getDeletedMusicFolders(): List<MusicFolder> = query(
        "select $QUERY_COLUMNS from music_folder where id < 0",
        MUSICFOLDER_ROW_MAPPER,
    )

    /** Returns the folder for [path], or null when no folder has that path. */
    open fun getMusicFolderForPath(path: String): MusicFolder? = queryOne(
        "select $QUERY_COLUMNS from music_folder where path = ?",
        MUSICFOLDER_ROW_MAPPER,
        path,
    )

    open fun createMusicFolder(musicFolder: MusicFolder) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            if (getMusicFolderForPath(musicFolder.path.toString()) == null) {
                val id = insert("music_folder", musicFolder)
                update("insert into music_folder_user (music_folder_id, username) select ?, username from users", id)
                musicFolder.id = id
                LOG.info("Created music folder {} with id {}", musicFolder.path, musicFolder.id)
            } else {
                LOG.info("Did NOT create music folder {}", musicFolder.path)
            }
            null
        }
    }

    open fun deleteMusicFolder(id: Int?) {
        update("delete from music_folder where id=?", id)
        LOG.info("Deleted music folder with ID {}", id)
    }

    open fun expungeMusicFolders() {
        update("delete from music_folder where id < 0")
    }

    open fun updateMusicFolderId(oldId: Int?, newId: Int?) {
        update("update music_folder set id=? where id=?", newId, oldId)
    }

    open fun reassignChildren(from: MusicFolder, to: MusicFolder) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            if (to.path.nameCount > from.path.nameCount) {
                reassignAncestorToDescendant(from, to)
            } else {
                reassignDescendantToAncestor(from, to)
            }
            null
        }
    }

    open fun updateMusicFolder(musicFolder: MusicFolder) {
        update(
            "update music_folder set path=?, name=?, type=?, enabled=?, changed=? where id=?",
            musicFolder.path.toString(),
            musicFolder.name,
            musicFolder.type.name,
            musicFolder.isEnabled(),
            musicFolder.changed,
            musicFolder.id,
        )
    }

    open fun getMusicFoldersForUser(username: String): List<MusicFolder> = query(
        "select ${prefix(QUERY_COLUMNS, "music_folder")} from music_folder, music_folder_user " +
            "where music_folder.id = music_folder_user.music_folder_id and music_folder.id >= 0 and music_folder_user.username = ?",
        MUSICFOLDER_ROW_MAPPER,
        username,
    )

    open fun setMusicFoldersForUser(username: String, musicFolderIds: Collection<Int>) {
        inTransaction(TransactionManager.ISOLATION_READ_COMMITTED) {
            update("delete from music_folder_user where username = ?", username)
            for (musicFolderId in musicFolderIds) {
                update("insert into music_folder_user(music_folder_id, username) values (?, ?)", musicFolderId, username)
            }
            null
        }
    }

    private fun reassignAncestorToDescendant(ancestor: MusicFolder, descendant: MusicFolder) {
        val relativePath = ancestor.path.relativize(descendant.path).toString()
        val length = relativePath.length
        var sql = "update media_file set " +
            "folder_id=?, " +
            "path=SUBSTR(path, ${length + 2}), " +
            "parent_path=(case " +
            "  when (length(parent_path) > $length) then SUBSTR(parent_path, ${length + 2}) " +
            "  else SUBSTR(parent_path, ${length + 1}) end) " +
            "where folder_id=? and path like ?"
        update(sql, descendant.id, ancestor.id, "$relativePath${File.separator}%")

        sql = "update cover_art set folder_id=?, path=SUBSTR(path, ${length + 2}) where folder_id=? and path like ?"
        update(sql, descendant.id, ancestor.id, "$relativePath${File.separator}%")

        sql = "update media_file set folder_id=?, path='', parent_path=null, title=?, type=? where folder_id=? and path=?"
        update(sql, descendant.id, descendant.name, MediaFile.MediaType.DIRECTORY, ancestor.id, relativePath)
    }

    private fun reassignDescendantToAncestor(descendant: MusicFolder, ancestor: MusicFolder) {
        val relativePath = ancestor.path.relativize(descendant.path)
        var sql = "update media_file set folder_id=?, title=null, path=?, parent_path=? where folder_id=? and path=''"
        update(
            sql,
            ancestor.id,
            relativePath,
            relativePath.parent?.toString() ?: "",
            descendant.id,
        )

        sql = "update media_file set folder_id=?, path=concat(?, path), " +
            "parent_path=(case when (parent_path = '') then ? else concat(?, parent_path) end) where folder_id=?"
        update(
            sql,
            ancestor.id,
            "$relativePath${File.separator}",
            relativePath,
            "$relativePath${File.separator}",
            descendant.id,
        )

        sql = "update cover_art set folder_id=?, path=concat(?, path) where folder_id=?"
        update(sql, ancestor.id, "$relativePath${File.separator}", descendant.id)
    }

    open class MusicFolderRowMapper : RowMapper<MusicFolder> {
        @Throws(SQLException::class)
        open override fun mapRow(rs: ResultSet, rowNum: Int): MusicFolder = MusicFolder(
            rs.getInt("id"),
            Paths.get(rs.getString("path")),
            rs.getString("name"),
            EnumUtils.getEnum(MusicFolder.Type::class.java, rs.getString("type"), MusicFolder.Type.MEDIA),
            rs.getBoolean("enabled"),
            rs.getTimestamp("changed")?.toInstant(),
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(MusicFolderDao::class.java)
        private const val INSERT_COLUMNS = "path, name, type, enabled, changed"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"

        @JvmField
        val MUSICFOLDER_ROW_MAPPER = MusicFolderRowMapper()
    }
}
