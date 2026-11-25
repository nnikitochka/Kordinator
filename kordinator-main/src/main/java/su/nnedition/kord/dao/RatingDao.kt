package su.nnedition.kord.dao

import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder

/** Reads and writes per-user media ratings. */
class RatingDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    fun getHighestRatedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<Int> {
        if (count < 1 || musicFolders.isEmpty()) return emptyList()
        val args = mapOf(
            "type" to MediaFile.MediaType.ALBUM.name,
            "folders" to MusicFolder.toIdList(musicFolders),
            "count" to count,
            "offset" to offset
        )
        val sql = "select user_rating.media_file_id from user_rating, media_file " +
            "where user_rating.media_file_id=media_file.id and media_file.present and " +
            "media_file.type = :type and media_file.folder_id in (:folders) " +
            "group by user_rating.media_file_id " +
            "order by avg(rating) desc, user_rating.media_file_id limit :count offset :offset"
        return namedQueryForTypes(sql, Int::class.javaObjectType, args)
    }

    fun setRatingForUser(username: String, mediaFile: MediaFile, rating: Int?) {
        if (rating != null && rating !in 1..5) return
        update("delete from user_rating where username=? and media_file_id=?", username, mediaFile.id)
        if (rating != null) {
            update("insert into user_rating(username, media_file_id, rating) values(?, ?, ?)", username, mediaFile.id, rating)
        }
    }

    fun getAverageRating(mediaFile: MediaFile): Double? = queryForDouble(
        "select avg(rating) from user_rating where media_file_id=?", null, mediaFile.id
    )

    fun getRatingForUser(username: String, mediaFile: MediaFile): Int? = queryForInt(
        "select rating from user_rating where username=? and media_file_id=?", null, username, mediaFile.id
    )

    fun getRatedAlbumCount(username: String, musicFolders: List<MusicFolder>): Int {
        if (musicFolders.isEmpty()) return 0
        val args = mapOf(
            "type" to MediaFile.MediaType.ALBUM.name,
            "folders" to MusicFolder.toIdList(musicFolders),
            "username" to username
        )
        val sql = "select count(*) from user_rating, media_file " +
            "where media_file.id = user_rating.media_file_id and media_file.type = :type " +
            "and media_file.present and media_file.folder_id in (:folders) " +
            "and user_rating.username = :username"
        return namedQueryForInt(sql, 0, args) ?: 0
    }
}
