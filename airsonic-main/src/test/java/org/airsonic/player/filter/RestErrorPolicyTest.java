package org.airsonic.player.filter;

import org.airsonic.player.controller.rest.error.ErrorCode;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void suppressesClientAbortErrorResponses() {
        assertTrue(policy.classify(new ClientAbortException("client disconnected")).clientAbort());
        assertFalse(policy.classify(new IllegalStateException("server error")).clientAbort());
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
