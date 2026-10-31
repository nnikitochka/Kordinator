package org.airsonic.player.controller.rest.music.song

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class SongRequest(val username: String, val player: Player, val parameters: Parameters)
