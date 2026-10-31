package org.airsonic.player.controller.rest.videos

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class VideosRequest(val username: String, val player: Player, val parameters: Parameters)
