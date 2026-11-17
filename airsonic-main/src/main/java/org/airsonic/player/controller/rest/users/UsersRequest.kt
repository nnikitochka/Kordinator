package org.airsonic.player.controller.rest.users

import io.ktor.http.Parameters
import org.airsonic.player.domain.User
import org.airsonic.player.security.CallerContext

class UsersRequest(val user: User, val callerContext: CallerContext, val parameters: Parameters) {
    var transferPlayer: org.airsonic.player.domain.Player? = null
}
