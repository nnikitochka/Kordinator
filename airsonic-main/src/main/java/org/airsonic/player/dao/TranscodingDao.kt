package org.airsonic.player.dao

import org.airsonic.player.domain.Transcoding
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/** Provides database services for transcoding configurations. */
class TranscodingDao(jdbcHelper: JdbcHelper) : AbstractDao(jdbcHelper) {
    private val rowMapper = TranscodingRowMapper()

    @Throws(Exception::class)
    fun register() {
        registerInserts("transcoding", "id", INSERT_COLUMNS.split(", "), Transcoding::class.java)
    }

    val allTranscodings: List<Transcoding>
        get() = query("select $QUERY_COLUMNS from transcoding", rowMapper)

    fun getTranscodingsForPlayer(playerId: Int?): List<Transcoding> = query(
        "select $QUERY_COLUMNS from transcoding, player_transcoding " +
            "where player_transcoding.player_id = ? " +
            "and   player_transcoding.transcoding_id = transcoding.id",
        rowMapper,
        playerId
    )

    fun setTranscodingsForPlayer(playerId: Int?, transcodingIds: IntArray) {
        inTransaction {
            update("delete from player_transcoding where player_id = ?", playerId)
            val sql = "insert into player_transcoding(player_id, transcoding_id) values (?, ?)"
            transcodingIds.forEach { transcodingId -> update(sql, playerId, transcodingId) }
        }
    }

    fun createTranscoding(transcoding: Transcoding) {
        transcoding.id = insert("transcoding", transcoding)
        LOG.info("Created transcoding {}", transcoding.name)
    }

    fun deleteTranscoding(id: Int?) {
        update("delete from transcoding where id=?", id)
        LOG.info("Deleted transcoding with ID {}", id)
    }

    fun updateTranscoding(transcoding: Transcoding) {
        update(
            "update transcoding set name=?, source_formats=?, target_format=?, " +
                "step1=?, step2=?, step3=?, default_active=? where id=?",
            transcoding.name, transcoding.sourceFormats, transcoding.targetFormat, transcoding.step1,
            transcoding.step2, transcoding.step3, transcoding.defaultActive, transcoding.id
        )
    }

    private class TranscodingRowMapper : RowMapper<Transcoding> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Transcoding = Transcoding(
            rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8)
        )
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(TranscodingDao::class.java)
        private const val INSERT_COLUMNS = "name, source_formats, target_format, step1, step2, step3, default_active"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    }
}
