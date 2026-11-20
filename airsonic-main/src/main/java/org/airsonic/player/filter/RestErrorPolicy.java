package org.airsonic.player.filter;

import org.airsonic.player.controller.rest.error.ErrorCode;

/** Framework-neutral classification of failures raised while serving REST requests. */
public final class RestErrorPolicy {
    public Decision classify(Throwable error) {
        String className = error.getClass().getName();
        boolean missingParameter = className.endsWith("ServletRequestBindingException")
                || className.endsWith("MissingServletRequestParameterException");
        ErrorCode code = missingParameter ? ErrorCode.MISSING_PARAMETER : ErrorCode.GENERIC;
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new Decision(code, message);
    }

    public record Decision(ErrorCode errorCode, String message) {
    }
}
