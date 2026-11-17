package org.airsonic.player.controller.rest.music.top

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class TopSongsRequest(val username: String, val parameters: Parameters, val player: Player?)
