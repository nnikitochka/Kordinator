package org.airsonic.player.controller.rest.users

import io.ktor.http.Parameters
import org.airsonic.player.domain.User

class UsersRequest(val user: User, val parameters: Parameters) {
    var transferPlayer: org.airsonic.player.domain.Player? = null
}
