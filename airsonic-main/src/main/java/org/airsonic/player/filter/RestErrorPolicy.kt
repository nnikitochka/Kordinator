package org.airsonic.player.filter

import org.airsonic.player.controller.rest.error.ErrorCode

/** Framework-neutral classification of failures raised while serving REST requests. */
class RestErrorPolicy {
    fun classify(error: Throwable): Decision {
        val className = error.javaClass.name
        val missingParameter = className.endsWith("ServletRequestBindingException") ||
            className.endsWith("MissingServletRequestParameterException")
        val code = if (missingParameter) ErrorCode.MISSING_PARAMETER else ErrorCode.GENERIC
        val message = error.message ?: error.javaClass.simpleName
        return Decision(code, message)
    }

    @JvmRecord
    data class Decision(val errorCode: ErrorCode, val message: String)
}
