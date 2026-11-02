package io.karatelabs.http;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.UnaryOperator;

/**
 * The token the threads sharing a handler reuse: the first to find it missing or expired fetches the next one and the
 * others await that fetch, so one answer (or one failure) serves them all. No lock is held across the fetch: scenarios
 * run on virtual threads, and a monitor held across blocking I/O pins its carrier (JDK 21), which starves the token
 * endpoint's own virtual threads when there are as few carriers as scenarios waiting.
 */
final class OAuth2TokenCache {

    private OAuth2Token token;
    private CompletableFuture<OAuth2Token> fetching;

    /** The cached token, else the one {@code fetch} returns given the expired one (null when there is none). */
    OAuth2Token get(UnaryOperator<OAuth2Token> fetch) {
        CompletableFuture<OAuth2Token> future;
        OAuth2Token expired;
        boolean fetcher;
        synchronized (this) {
            if (token != null && !token.isExpired()) {
                return token;
            }
            fetcher = fetching == null;
            if (fetcher) {
                fetching = new CompletableFuture<>();
            }
            future = fetching;
            expired = token;
        }
        if (!fetcher) {
            try {
                return future.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException cause ? cause : e;
            }
        }
        OAuth2Token next;
        try {
            next = fetch.apply(expired);
        } catch (Throwable t) {
            synchronized (this) {
                fetching = null;
            }
            future.completeExceptionally(t);
            throw t;
        }
        synchronized (this) {
            // an answer without an access token is not kept, so the next request asks again
            if (next.getAccessToken() != null) {
                token = next;
            }
            fetching = null;
        }
        future.complete(next);
        return next;
    }

    /** {@code rejected} is expired if it is still the cached token (by identity), so the next request fetches again. */
    synchronized void rejected(OAuth2Token rejected) {
        if (token != null && token == rejected) {
            // keeps the refresh token, for a grant that renews with it
            token = new OAuth2Token(token.getAccessToken(), token.getTokenType(), token.getRefreshToken(), 0, token.getScope());
        }
    }

}
