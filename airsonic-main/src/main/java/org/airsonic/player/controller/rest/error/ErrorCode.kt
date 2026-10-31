package org.airsonic.player.controller.rest.error

enum class ErrorCode(
    val code: Int,
    val message: String
) {
    GENERIC(0, "A generic error."),
    MISSING_PARAMETER(10, "Required parameter is missing."),
    PROTOCOL_MISMATCH_CLIENT_TOO_OLD(20, "Incompatible Airsonic REST protocol version. Client must upgrade."),
    PROTOCOL_MISMATCH_SERVER_TOO_OLD(30, "Incompatible Airsonic REST protocol version. Server must upgrade."),
    NOT_AUTHENTICATED(40, "Wrong username or password."),
    NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED(41, "Wrong username or password, but try authenticating via non-hashed password."),
    NOT_AUTHORIZED(50, "User is not authorized for the given operation."),
    NOT_FOUND(70, "Requested data was not found.");
}
