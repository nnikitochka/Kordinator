package su.nnedition.kord.dao

import su.nnedition.kord.domain.PlayQueue
import su.nnedition.kord.domain.Player
import su.nnedition.kord.domain.PlayerTechnology
import su.nnedition.kord.domain.TranscodeScheme
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections

/** Provides player persistence and attaches each player to its in-memory play queue. */
class PlayerDao(
    private val playerDaoPlayQueueFactory: PlayerDaoPlayQueueFactory,
    jdbcHelper: JdbcHelper
) : AbstractDao(jdbcHelper) {
    private val rowMapper = PlayerRowMapper()
    private val playlists: MutableMap<Int?, PlayQueue> = Collections.synchronizedMap(HashMap())

    @Throws(Exception::class)
    fun register() {
        registerInserts("player", "id", INSERT_COLUMNS.split(", "), Player::class.java)
    }

    fun getAllPlayers(): List<Player> = query("select $QUERY_COLUMNS from player", rowMapper)

    fun getPlayersForUserAndClientId(username: String?, clientId: String?): List<Player> =
        if (clientId != null) {
            query("select $QUERY_COLUMNS from player where username=? and client_id=?", rowMapper, username, clientId)
        } else {
            query("select $QUERY_COLUMNS from player where username=? and client_id is null", rowMapper, username)
        }

    fun getPlayerById(id: Int): Player? = queryOne("select $QUERY_COLUMNS from player where id=?", rowMapper, id)

    fun createPlayer(player: Player?) {
        val player = player!!
        var id = insert("player", player)
        // Keep the historical guard against Player 0 (see Kordinator issue 646).
        if (id!!.equals(0)) {
            deletePlayer(0)
            id = insert("player", player)
        }
        player.id = id
        addPlaylist(player)
        LOG.info("Created player {}", id)
    }

    fun deletePlayer(id: Int?) {
        update("delete from player where id=?", id)
        playlists.remove(id)
    }

    fun deleteOldPlayers(days: Int) {
        val lastSeen = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val deleted = update(
            "delete from player where name is null and client_id is null and (last_seen is null or last_seen < ?)",
            lastSeen
        )
        if (deleted > 0) LOG.info("Deleted {} player(s) that haven't been used after {}", deleted, lastSeen)
    }

    fun updatePlayer(player: Player) {
        update(
            "update player set name=?, type=?, username=?, ip_address=?, auto_control_enabled=?, " +
                "m3u_bom_enabled=?, last_seen=?, transcode_scheme=?, dynamic_ip=?, technology=?, client_id=? " +
                "where id=?",
            player.name, player.type, player.username, player.ipAddress, player.autoControlEnabled,
            player.m3uBomEnabled, player.lastSeen, player.transcodeScheme.name, player.dynamicIp,
            player.technology.name, player.clientId, player.id
        )
    }

    private fun addPlaylist(player: Player) {
        var playQueue = playlists[player.id]
        if (playQueue == null) {
            playQueue = playerDaoPlayQueueFactory.createPlayQueue()
            playlists[player.id] = playQueue
        }
        player.playQueue = playQueue
    }

    private inner class PlayerRowMapper : RowMapper<Player> {
        override fun mapRow(rs: ResultSet, rowNum: Int): Player = Player().apply {
            id = rs.getInt("id")
            name = rs.getString("name")
            type = rs.getString("type")
            username = rs.getString("username")
            ipAddress = rs.getString("ip_address")
            autoControlEnabled = rs.getBoolean("auto_control_enabled")
            m3uBomEnabled = rs.getBoolean("m3u_bom_enabled")
            lastSeen = rs.getTimestamp("last_seen").toInstantOrNull()
            transcodeScheme = TranscodeScheme.valueOf(rs.getString("transcode_scheme"))
            dynamicIp = rs.getBoolean("dynamic_ip")
            technology = PlayerTechnology.valueOf(rs.getString("technology"))
            clientId = rs.getString("client_id")
            addPlaylist(this)
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(PlayerDao::class.java)
        private const val INSERT_COLUMNS = "name, type, username, ip_address, auto_control_enabled, m3u_bom_enabled, " +
            "last_seen, transcode_scheme, dynamic_ip, technology, client_id"
        private const val QUERY_COLUMNS = "id, $INSERT_COLUMNS"
    }
}

private fun Timestamp?.toInstantOrNull(): Instant? = this?.toInstant()
