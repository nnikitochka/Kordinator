package org.airsonic.player.filter;

import org.airsonic.player.controller.rest.error.ErrorCode;

/** Framework-neutral classification of failures raised while serving REST requests. */
public final class RestErrorPolicy {
    private static final String CLIENT_ABORT_EXCEPTION = "org.apache.catalina.connector.ClientAbortException";

    public Decision classify(Throwable error) {
        String className = error.getClass().getName();
        boolean missingParameter = className.endsWith("ServletRequestBindingException")
                || className.endsWith("MissingServletRequestParameterException");
        ErrorCode code = missingParameter ? ErrorCode.MISSING_PARAMETER : ErrorCode.GENERIC;
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return new Decision(code, message, isClientAbort(error));
    }

    private static boolean isClientAbort(Throwable error) {
        try {
            return Class.forName(CLIENT_ABORT_EXCEPTION).isInstance(error);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public record Decision(ErrorCode errorCode, String message, boolean clientAbort) {
    }
}
