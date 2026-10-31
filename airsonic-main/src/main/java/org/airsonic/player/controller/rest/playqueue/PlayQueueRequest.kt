package org.airsonic.player.controller.rest.playqueue

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class PlayQueueRequest(val username: String, val parameters: Parameters, val player: Player?)
