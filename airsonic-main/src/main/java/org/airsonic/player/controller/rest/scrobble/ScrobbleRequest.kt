package org.airsonic.player.controller.rest.scrobble

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class ScrobbleRequest(val player: Player, val parameters: Parameters)
