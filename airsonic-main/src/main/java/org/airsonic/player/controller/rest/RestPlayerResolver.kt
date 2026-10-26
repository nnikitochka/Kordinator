package org.airsonic.player.controller.rest

import org.airsonic.player.domain.Player
import org.airsonic.player.domain.PlayerTechnology
import org.airsonic.player.service.PlayerService

/** Resolves the Subsonic player associated with a REST client without depending on Servlet APIs. */
class RestPlayerResolver(
    private val playerService: PlayerService
) {
    fun getOrCreatePlayerId(username: String?, clientId: String?, ipAddress: String, jukebox: Boolean): Int? {
        val effectiveClientId = if (jukebox) "$clientId-jukebox" else clientId
        var players = playerService.getPlayersForUserAndClientId(username, effectiveClientId)

        if (players.isEmpty()) {
            val player = Player().apply {
                this.ipAddress = ipAddress
                this.username = username
                this.clientId = effectiveClientId
                this.name = effectiveClientId
                this.technology = if (jukebox) {
                    PlayerTechnology.JUKEBOX
                } else {
                    PlayerTechnology.EXTERNAL_WITH_PLAYLIST
                }
            }
            playerService.createPlayer(player)
            players = playerService.getPlayersForUserAndClientId(username, effectiveClientId)
        }

        return players.firstOrNull()?.id
    }
}
