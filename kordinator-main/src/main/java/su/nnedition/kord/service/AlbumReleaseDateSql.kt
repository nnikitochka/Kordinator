package su.nnedition.kord.service

/** SQL counterpart of ReleaseDate.mostCommon, applied before filtering and pagination. */
internal object AlbumReleaseDateSql {
    val ctes: String = buildString {
        append("""
            WITH release_parts AS (
                SELECT folder_id, album, year AS date_year,
                    CASE WHEN CHAR_LENGTH(release_date) >= 7 THEN CAST(SUBSTRING(release_date, 6, 2) AS INTEGER) END AS date_month,
                    CASE WHEN CHAR_LENGTH(release_date) = 10 THEN CAST(SUBSTRING(release_date, 9, 2) AS INTEGER) END AS date_day
                FROM media_file WHERE present AND type IN ('MUSIC', 'AUDIOBOOK') AND year BETWEEN 1 AND 9999
            ),
        """.trimIndent())
        append(vote("year", ""))
        append(vote("month", "JOIN release_year_winners y ON p.folder_id = y.folder_id AND p.album = y.album AND p.date_year = y.release_year"))
        append(vote("day", "JOIN release_year_winners y ON p.folder_id = y.folder_id AND p.album = y.album AND p.date_year = y.release_year " +
            "JOIN release_month_winners m ON p.folder_id = m.folder_id AND p.album = m.album AND p.date_month = m.release_month"))
        append("""
            album_release_dates AS (
                SELECT y.folder_id, y.album, y.release_year, m.release_month, d.release_day
                FROM release_year_winners y
                LEFT JOIN release_month_winners m ON y.folder_id = m.folder_id AND y.album = m.album
                LEFT JOIN release_day_winners d ON y.folder_id = d.folder_id AND y.album = d.album
            )
        """.trimIndent())
    }

    private fun vote(part: String, parents: String): String = """
        release_${part}_votes AS (
            SELECT p.folder_id, p.album, p.date_$part AS component, COUNT(*) AS votes
            FROM release_parts p $parents WHERE p.date_$part IS NOT NULL
            GROUP BY p.folder_id, p.album, p.date_$part
        ),
        release_${part}_winners AS (
            SELECT v.folder_id, v.album, MAX(v.component) AS release_$part
            FROM release_${part}_votes v
            WHERE NOT EXISTS (
                SELECT 1 FROM release_${part}_votes other
                WHERE other.folder_id = v.folder_id AND other.album = v.album AND other.votes > v.votes
            )
            GROUP BY v.folder_id, v.album HAVING COUNT(*) = 1
        ),
    """.trimIndent()
}
