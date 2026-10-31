package org.airsonic.player.controller.rest.music.random

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class RandomSongsRequest(val username: String, val player: Player, val parameters: Parameters)
