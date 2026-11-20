package org.airsonic.player.filter;

import org.airsonic.player.controller.rest.error.ErrorCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RestErrorPolicyTest {
    private final RestErrorPolicy policy = new RestErrorPolicy();

    @Test
    public void mapsBindingExceptionClassNamesToMissingParameter() {
        assertEquals(ErrorCode.MISSING_PARAMETER,
                policy.classify(new ServletRequestBindingException("binding failed")).errorCode());
        assertEquals(ErrorCode.MISSING_PARAMETER,
                policy.classify(new MissingServletRequestParameterException("missing")).errorCode());
    }

    @Test
    public void mapsOtherExceptionsToGenericAndFallsBackToSimpleClassName() {
        RestErrorPolicy.Decision withMessage = policy.classify(new IllegalArgumentException("invalid input"));
        assertEquals(ErrorCode.GENERIC, withMessage.errorCode());
        assertEquals("invalid input", withMessage.message());

        RestErrorPolicy.Decision withoutMessage = policy.classify(new IllegalStateException());
        assertEquals("IllegalStateException", withoutMessage.message());
    }

    private static final class ServletRequestBindingException extends RuntimeException {
        private ServletRequestBindingException(String message) {
            super(message);
        }
    }

    private static final class MissingServletRequestParameterException extends RuntimeException {
        private MissingServletRequestParameterException(String message) {
            super(message);
        }
    }
}
