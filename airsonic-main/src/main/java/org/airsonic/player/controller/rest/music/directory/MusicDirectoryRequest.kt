package org.airsonic.player.controller.rest.music.directory

import io.ktor.http.Parameters
import org.airsonic.player.domain.Player

class MusicDirectoryRequest(val username: String, val player: Player, val parameters: Parameters)
