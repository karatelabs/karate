package io.karatelabs.http;

import java.util.concurrent.CompletableFuture;

/**
 * Callback interface for OAuth 2.0 browser-based authorization.
 * Implementations can use JavaFX WebView, system browser, CDP, etc.
 */
public interface OAuth2BrowserCallback {

    /**
     * Open browser for user authorization.
     * The implementation should:
     * 1. Open the authorization URL in a browser (WebView, system browser, etc.)
     * 2. Wait for the OAuth provider to redirect to redirectUri
     * 3. Extract the authorization code from the redirect
     * 4. Complete the future with the code
     *
     * @param authorizationUrl Full authorization URL with all query parameters
     * @param redirectUri The redirect URI that will receive the authorization code
     * @return CompletableFuture that completes with authorization code or error
     */
    CompletableFuture<String> openBrowserForAuthorization(
        String authorizationUrl,
        String redirectUri
    );

    /**
     * Optional: Cancel ongoing authorization
     */
    default void cancel() {
        // Optional implementation
    }
}
