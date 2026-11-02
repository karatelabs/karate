package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuth2TokenCacheTest {

    final OAuth2TokenCache cache = new OAuth2TokenCache();
    final AtomicInteger fetches = new AtomicInteger();

    OAuth2Token token(String accessToken, String refreshToken) {
        return new OAuth2Token(accessToken, "Bearer", refreshToken, System.currentTimeMillis() + 3600_000, null);
    }

    @Test
    void threadsFindingNoTokenShareOneFetch() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<OAuth2Token>> got = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                got.add(executor.submit(() -> cache.get(expired -> {
                    fetches.incrementAndGet();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return token("t1", null);
                })));
            }
            Thread.sleep(100);
            release.countDown();
            OAuth2Token first = got.get(0).get(10, TimeUnit.SECONDS);
            for (Future<OAuth2Token> future : got) {
                assertSame(first, future.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, fetches.get());
        assertSame(cache.get(expired -> token("t2", null)), cache.get(expired -> token("t3", null)));
    }

    @Test
    void aFailedFetchFailsItsWaitersAndTheNextCallFetchesAgain() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<OAuth2Token> leader = executor.submit(() -> cache.get(expired -> {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new OAuth2Exception("refused");
            }));
            entered.await(10, TimeUnit.SECONDS);
            Future<OAuth2Token> waiter = executor.submit(() -> cache.get(expired -> {
                fetches.incrementAndGet();
                return token("unexpected", null);
            }));
            Thread.sleep(100);
            release.countDown();
            for (Future<OAuth2Token> future : List.of(leader, waiter)) {
                Exception e = assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
                assertEquals("refused", e.getCause().getMessage());
            }
        }
        assertEquals(0, fetches.get(), "the waiter shared the leader's failure");
        assertEquals("t1", cache.get(expired -> token("t1", null)).getAccessToken());
    }

    @Test
    void aRejectedTokenIsFetchedAgainWithItsRefreshToken() {
        OAuth2Token first = cache.get(expired -> token("t1", "r1"));
        cache.rejected(token("other", null));
        assertSame(first, cache.get(expired -> token("t2", null)));
        cache.rejected(first);
        OAuth2Token second = cache.get(expired -> {
            assertEquals("r1", expired.getRefreshToken());
            return token("t2", null);
        });
        assertEquals("t2", second.getAccessToken());
        cache.rejected(second);
        assertEquals("t3", cache.get(expired -> {
            assertNull(expired.getRefreshToken());
            return token("t3", null);
        }).getAccessToken());
    }

    @Test
    void anAnswerWithoutAnAccessTokenIsReturnedButNotKept() {
        assertNull(cache.get(expired -> token(null, null)).getAccessToken());
        assertEquals("t1", cache.get(expired -> token("t1", null)).getAccessToken());
    }

}
