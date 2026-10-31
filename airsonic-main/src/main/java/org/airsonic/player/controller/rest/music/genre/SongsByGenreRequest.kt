package org.airsonic.player.controller.rest.music.genre

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class SongsByGenreRequest(val username: String, val player: Player, val parameters: Parameters)
