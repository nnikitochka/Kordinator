package org.airsonic.player.dao

import org.airsonic.player.domain.MediaFile
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.RandomSearchCriteria
import java.time.Instant

/** Per-user favorites and random-song queries. */
class MediaFileDaoUserQueries(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = MediaFileRowMapper()

    fun getStarredAlbums(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): List<MediaFile> =
        starredFiles(offset, count, username, musicFolders, MediaFile.MediaType.ALBUM.name)

    fun getStarredDirectories(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): List<MediaFile> =
        starredFiles(offset, count, username, musicFolders, MediaFile.MediaType.DIRECTORY.name)

    fun getStarredFiles(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = starredArgs(offset, count, username, musicFolders).toMutableMap()
        args["types"] = listOf(MediaFile.MediaType.MUSIC.name, MediaFile.MediaType.AUDIOBOOK.name, MediaFile.MediaType.VIDEO.name)
        return namedQuery(
            "select ${prefix(MediaFileDaoSql.QUERY_COLUMNS, "media_file")} from starred_media_file, media_file " +
                "where media_file.id = starred_media_file.media_file_id and media_file.present " +
                "and media_file.type in (:types) and starred_media_file.username = :username " +
                "and media_file.folder_id in (:folders) " +
                "order by starred_media_file.created desc, starred_media_file.id limit :count offset :offset",
            rowMapper, args
        )
    }

    private fun starredFiles(
        offset: Int,
        count: Int,
        username: String,
        musicFolders: List<MusicFolder>,
        type: String
    ): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = starredArgs(offset, count, username, musicFolders) + ("type" to type)
        return namedQuery(
            "select ${prefix(MediaFileDaoSql.QUERY_COLUMNS, "media_file")} from starred_media_file, media_file " +
                "where media_file.id = starred_media_file.media_file_id and media_file.present " +
                "and media_file.type = :type and media_file.folder_id in (:folders) " +
                "and starred_media_file.username = :username " +
                "order by starred_media_file.created desc, starred_media_file.id limit :count offset :offset",
            rowMapper, args
        )
    }

    private fun starredArgs(offset: Int, count: Int, username: String, musicFolders: List<MusicFolder>): Map<String, Any?> = mapOf(
        "folders" to MusicFolder.toIdList(musicFolders),
        "username" to username,
        "count" to count,
        "offset" to offset
    )

    fun getRandomSongs(criteria: RandomSearchCriteria, username: String): List<MediaFile> {
        val musicFolders = criteria.musicFolders!!
        if (musicFolders.isEmpty()) return emptyList()
        val args = mapOf(
            "folders" to MusicFolder.toIdList(musicFolders),
            "username" to username,
            "fromYear" to criteria.fromYear,
            "toYear" to criteria.toYear,
            "genre" to criteria.genre,
            "minLastPlayed" to criteria.minLastPlayedDate,
            "maxLastPlayed" to criteria.maxLastPlayedDate,
            "minAlbumRating" to criteria.minAlbumRating,
            "maxAlbumRating" to criteria.maxAlbumRating,
            "minPlayCount" to criteria.minPlayCount,
            "maxPlayCount" to criteria.maxPlayCount,
            "starred" to criteria.isShowStarredSongs,
            "unstarred" to criteria.isShowUnstarredSongs,
            "format" to criteria.format
        )
        val joinAlbumRating = criteria.minAlbumRating != null || criteria.maxAlbumRating != null
        val joinStarred = criteria.isShowStarredSongs xor criteria.isShowUnstarredSongs
        val sql = buildString {
            append("select ${prefix(MediaFileDaoSql.QUERY_COLUMNS, "media_file")} from media_file ")
            if (joinStarred) append(
                "left outer join starred_media_file on media_file.id = starred_media_file.media_file_id " +
                    "and starred_media_file.username = :username "
            )
            if (joinAlbumRating) {
                append("left outer join media_file media_album on media_album.type = 'ALBUM' ")
                append("and media_album.album = media_file.album and media_album.artist = media_file.artist ")
                append("left outer join user_rating on user_rating.media_file_id = media_album.id and user_rating.username = :username ")
            }
            append(" where media_file.present and media_file.type = 'MUSIC'")
            if (musicFolders.isNotEmpty()) append(" and media_file.folder_id in (:folders)")
            if (criteria.genre != null) append(" and media_file.genre = :genre")
            if (criteria.format != null) append(" and media_file.format = :format")
            if (criteria.fromYear != null) append(" and media_file.year >= :fromYear")
            if (criteria.toYear != null) append(" and media_file.year <= :toYear")
            if (criteria.minLastPlayedDate != null) append(" and media_file.last_played >= :minLastPlayed")
            if (criteria.maxLastPlayedDate != null) {
                append(if (criteria.minLastPlayedDate == null) {
                    " and (media_file.last_played is null or media_file.last_played <= :maxLastPlayed)"
                } else " and media_file.last_played <= :maxLastPlayed")
            }
            if (criteria.minAlbumRating != null) append(" and user_rating.rating >= :minAlbumRating")
            if (criteria.maxAlbumRating != null) {
                append(if (criteria.minAlbumRating == null) {
                    " and (user_rating.rating is null or user_rating.rating <= :maxAlbumRating)"
                } else " and user_rating.rating <= :maxAlbumRating")
            }
            if (criteria.minPlayCount != null) append(" and media_file.play_count >= :minPlayCount")
            if (criteria.maxPlayCount != null) {
                append(if (criteria.minPlayCount == null) {
                    " and (media_file.play_count is null or media_file.play_count <= :maxPlayCount)"
                } else " and media_file.play_count <= :maxPlayCount")
            }
            if (criteria.isShowStarredSongs && !criteria.isShowUnstarredSongs) append(" and starred_media_file.id is not null")
            if (criteria.isShowUnstarredSongs && !criteria.isShowStarredSongs) append(" and starred_media_file.id is null")
            append(" order by rand() limit ${criteria.count}")
        }
        return namedQuery(sql, rowMapper, args)
    }

    fun getAlbumCount(musicFolders: List<MusicFolder>): Int = countAlbums(musicFolders, "")

    fun getPlayedAlbumCount(musicFolders: List<MusicFolder>): Int = countAlbums(musicFolders, "and play_count > 0 ")

    private fun countAlbums(musicFolders: List<MusicFolder>, extraCondition: String): Int {
        if (musicFolders.isEmpty()) return 0
        val args = mapOf(
            "type" to MediaFile.MediaType.ALBUM.name,
            "folders" to MusicFolder.toIdList(musicFolders)
        )
        return namedQueryForInt(
            "select count(*) from media_file where type = :type $extraCondition and present and folder_id in (:folders)", 0, args
        ) ?: 0
    }

    fun getStarredAlbumCount(username: String, musicFolders: List<MusicFolder>): Int {
        if (musicFolders.isEmpty()) return 0
        val args = mapOf(
            "type" to MediaFile.MediaType.ALBUM.name,
            "folders" to MusicFolder.toIdList(musicFolders),
            "username" to username
        )
        return namedQueryForInt(
            "select count(*) from starred_media_file, media_file " +
                "where media_file.id = starred_media_file.media_file_id and media_file.type = :type " +
                "and media_file.present and media_file.folder_id in (:folders) and starred_media_file.username = :username",
            0, args
        ) ?: 0
    }

    fun starMediaFiles(ids: List<Int>, username: String) {
        if (ids.isEmpty()) return
        unstarMediaFiles(ids, username)
        val now = Instant.now()
        val batch = ids.parallelStream().map { id -> arrayOf<Any?>(id, username, now) }.toList()
        batchedUpdate("insert into starred_media_file(media_file_id, username, created) values (?,?,?)", batch)
    }

    fun unstarMediaFiles(ids: List<Int>, username: String) {
        if (ids.isNotEmpty()) {
            namedUpdate("delete from starred_media_file where media_file_id in (:ids) and username=:user", mapOf("ids" to ids, "user" to username))
        }
    }

    fun getMediaFileStarredDate(id: Int, username: String): Instant? =
        queryForInstant("select created from starred_media_file where media_file_id=? and username=?", null, id, username)
}
