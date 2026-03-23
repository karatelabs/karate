package io.karatelabs.http;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2ExceptionTest {

    static final Logger logger = LoggerFactory.getLogger(OAuth2ExceptionTest.class);

    @Test
    void testExceptionWithMessage() {
        OAuth2Exception exception = new OAuth2Exception("Test error message");
        assertEquals("Test error message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        OAuth2Exception exception = new OAuth2Exception("Test error", cause);
        assertEquals("Test error", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals(cause, exception.getCause());
        assertEquals("Root cause", exception.getCause().getMessage());
    }

    @Test
    void testExceptionIsRuntimeException() {
        OAuth2Exception exception = new OAuth2Exception("Test");
        assertTrue(exception instanceof RuntimeException);
    }
}
