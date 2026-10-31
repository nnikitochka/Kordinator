package org.airsonic.player.controller.rest.error

class APIException @JvmOverloads constructor(
    val error: ErrorCode,
    override val message: String = error.message
) : Exception()
