package org.airsonic.player.controller.rest.error;

public class APIException extends Exception {
    private final String message;
    private final ErrorCode error;

    public APIException(ErrorCode error, String message) {
        this.message = message;
        this.error = error;
    }

    public APIException(ErrorCode error) {
        this(error, error.getMessage());
    }

    @Override
    public String getMessage() {
        return message;
    }

    public ErrorCode getError() {
        return error;
    }
}
