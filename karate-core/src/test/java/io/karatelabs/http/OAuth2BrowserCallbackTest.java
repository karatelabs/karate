package io.karatelabs.http;

import io.karatelabs.http.OAuth2Exception;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2BrowserCallbackTest {

    static final Logger logger = LoggerFactory.getLogger(OAuth2BrowserCallbackTest.class);

    @Test
    void testSuccessfulCallback() throws Exception {
        OAuth2BrowserCallback callback = new MockBrowserCallback("test-auth-code");

        CompletableFuture<String> future = callback.openBrowserForAuthorization(
            "http://example.com/authorize?client_id=test",
            "http://localhost:8080/callback"
        );

        String code = future.get(1, TimeUnit.SECONDS);
        assertEquals("test-auth-code", code);
    }

    @Test
    void testFailedCallback() {
        OAuth2BrowserCallback callback = new MockBrowserCallback(
            new OAuth2Exception("User denied authorization")
        );

        CompletableFuture<String> future = callback.openBrowserForAuthorization(
            "http://example.com/authorize?client_id=test",
            "http://localhost:8080/callback"
        );

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            future.get(1, TimeUnit.SECONDS);
        });

        assertTrue(exception.getCause() instanceof OAuth2Exception);
        assertTrue(exception.getCause().getMessage().contains("User denied authorization"));
    }

    @Test
    void testCancelCallback() {
        MockBrowserCallback callback = new MockBrowserCallback("test-code");

        CompletableFuture<String> future = callback.openBrowserForAuthorization(
            "http://example.com/authorize?client_id=test",
            "http://localhost:8080/callback"
        );

        // Test cancel method
        callback.cancel();
        assertTrue(callback.wasCancelled());
    }

    @Test
    void testAsyncBehavior() throws Exception {
        // Simulate delayed authorization
        OAuth2BrowserCallback callback = new OAuth2BrowserCallback() {
            @Override
            public CompletableFuture<String> openBrowserForAuthorization(
                String authorizationUrl,
                String redirectUri
            ) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(100); // Simulate user interaction delay
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return "delayed-code";
                });
            }
        };

        long startTime = System.currentTimeMillis();
        CompletableFuture<String> future = callback.openBrowserForAuthorization(
            "http://example.com/authorize",
            "http://localhost:8080/callback"
        );

        // Should return immediately, not block
        long callTime = System.currentTimeMillis() - startTime;
        assertTrue(callTime < 50, "Call should return immediately");

        // But future should eventually complete
        String code = future.get(1, TimeUnit.SECONDS);
        assertEquals("delayed-code", code);
    }

    @Test
    void testDefaultCancelMethod() {
        OAuth2BrowserCallback callback = new OAuth2BrowserCallback() {
            @Override
            public CompletableFuture<String> openBrowserForAuthorization(
                String authorizationUrl,
                String redirectUri
            ) {
                return CompletableFuture.completedFuture("test");
            }
        };

        // Default cancel should not throw exception
        assertDoesNotThrow(() -> callback.cancel());
    }

    // Mock implementation for testing
    static class MockBrowserCallback implements OAuth2BrowserCallback {
        private final String code;
        private final Exception exception;
        private boolean cancelled = false;

        MockBrowserCallback(String code) {
            this.code = code;
            this.exception = null;
        }

        MockBrowserCallback(Exception exception) {
            this.code = null;
            this.exception = exception;
        }

        @Override
        public CompletableFuture<String> openBrowserForAuthorization(
            String authorizationUrl,
            String redirectUri
        ) {
            logger.info("Mock browser opening: {}", authorizationUrl);
            logger.info("Redirect URI: {}", redirectUri);

            if (exception != null) {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(exception);
                return future;
            }
            return CompletableFuture.completedFuture(code);
        }

        @Override
        public void cancel() {
            cancelled = true;
            logger.info("Mock browser cancelled");
        }

        public boolean wasCancelled() {
            return cancelled;
        }
    }
}
