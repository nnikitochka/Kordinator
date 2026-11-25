package su.nnedition.kord.dao

import su.nnedition.kord.domain.Genre
import su.nnedition.kord.domain.MediaFile
import su.nnedition.kord.domain.MusicFolder
import org.apache.commons.lang.StringUtils

/** Genre, album-list and artist-track queries. */
class MediaFileDaoAlbumQueries(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = MediaFileRowMapper()
    private val genreMapper = GenreRowMapper()

    fun getGenres(sortByAlbum: Boolean): List<Genre> {
        val orderBy = if (sortByAlbum) "album_count" else "song_count"
        return query("select ${MediaFileDaoSql.GENRE_COLUMNS} from genre order by $orderBy, name desc", genreMapper)
    }

    fun updateGenres(genres: List<Genre>): Boolean {
        update("delete from genre")
        if (genres.isEmpty()) return true
        val batch = genres.map { arrayOf<Any?>(it.name, it.getSongCount(), it.getAlbumCount()) }
        return batchedUpdate("insert into genre(${MediaFileDaoSql.GENRE_COLUMNS}) values(?, ?, ?)", batch) == genres.size
    }

    fun getMostFrequentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders)
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and play_count > 0 and present " +
                "and folder_id in (:folders) order by play_count desc, id limit :count offset :offset", rowMapper, args
        )
    }

    fun getMostRecentlyPlayedAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders)
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and last_played is not null and present " +
                "and folder_id in (:folders) order by last_played desc, id limit :count offset :offset", rowMapper, args
        )
    }

    fun getNewestAlbums(offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders)
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and folder_id in (:folders) and present " +
                "order by created desc, id limit :count offset :offset", rowMapper, args
        )
    }

    fun getAlphabeticalAlbums(offset: Int, count: Int, byArtist: Boolean, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders)
        val orderBy = if (byArtist) "artist, album" else "album"
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and folder_id in (:folders) and present " +
                "order by $orderBy, id limit :count offset :offset", rowMapper, args
        )
    }

    fun getAlbumsByYear(offset: Int, count: Int, fromYear: Int, toYear: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders) + mapOf("fromYear" to fromYear, "toYear" to toYear)
        val sql = if (fromYear <= toYear) {
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and folder_id in (:folders) and present " +
                "and year between :fromYear and :toYear order by year, id limit :count offset :offset"
        } else {
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and folder_id in (:folders) and present " +
                "and year between :toYear and :fromYear order by year desc, id limit :count offset :offset"
        }
        return namedQuery(sql, rowMapper, args)
    }

    fun getAlbumsByGenre(offset: Int, count: Int, genre: String, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = albumPagingArgs(offset, count, musicFolders) + mapOf("genre" to genre)
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type = :type and folder_id in (:folders) " +
                "and present and genre = :genre order by id limit :count offset :offset", rowMapper, args
        )
    }

    fun getSongsByGenre(genre: String, offset: Int, count: Int, musicFolders: List<MusicFolder>): List<MediaFile> {
        if (musicFolders.isEmpty()) return emptyList()
        val args = mapOf(
            "types" to listOf(MediaFile.MediaType.MUSIC.name, MediaFile.MediaType.AUDIOBOOK.name),
            "genre" to genre,
            "count" to count,
            "offset" to offset,
            "folders" to MusicFolder.toIdList(musicFolders)
        )
        return namedQuery(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type in (:types) and genre = :genre " +
                "and present and folder_id in (:folders) order by id limit :count offset :offset", rowMapper, args
        )
    }

    fun getSongsByArtist(artist: String, offset: Int, count: Int): List<MediaFile> = query(
        "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where type in (?,?) and artist=? and present order by id limit ? offset ?",
        rowMapper, MediaFile.MediaType.MUSIC.name, MediaFile.MediaType.AUDIOBOOK.name, artist, count, offset
    )

    fun getSongByArtistAndTitle(artist: String?, title: String?, musicFolders: List<MusicFolder>): MediaFile? {
        if (musicFolders.isEmpty() || StringUtils.isBlank(title) || StringUtils.isBlank(artist)) return null
        val args = mapOf(
            "artist" to artist,
            "title" to title,
            "type" to MediaFile.MediaType.MUSIC.name,
            "folders" to MusicFolder.toIdList(musicFolders)
        )
        return namedQueryOne(
            "select ${MediaFileDaoSql.QUERY_COLUMNS} from media_file where artist = :artist and title = :title " +
                "and type = :type and present and folder_id in (:folders)", rowMapper, args
        )
    }

    private fun albumPagingArgs(offset: Int, count: Int, musicFolders: List<MusicFolder>): Map<String, Any?> = mapOf(
        "type" to MediaFile.MediaType.ALBUM.name,
        "folders" to MusicFolder.toIdList(musicFolders),
        "count" to count,
        "offset" to offset
    )
}
