/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every HTTP client a run creates must be released when the scenario that owns it ends.
 *
 * <p>None of them ever were. {@code ApacheHttpClient.close()} existed and nothing in the
 * repository called it, so every execution abandoned its client, its connection manager and its
 * pooled sockets to the collector — released at a time no test controlled. A file descriptor is
 * not heap, so the heap-after-GC floor that the profiling harness uses as its leak detector could
 * not see this at all, and it is the reason docs/PROFILING.md calls the lifecycle a prerequisite
 * for a Gatling soak rather than an optimisation.
 *
 * <p>The count is the interesting part. A {@code ScenarioRuntime} builds its own client
 * unconditionally, and {@code karate.call()} creates another runtime — so a scenario with N calls
 * creates N+1 clients, not one. Asserting create-count == release-count rather than "at least one
 * release" is what makes that visible.
 */
class HttpClientLifecycleTest {

    /** Counts what it hands out and what comes back, and answers requests without a network. */
    private static final class CountingFactory implements HttpClientFactory {

        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();
        final List<HttpClient> handedOut = new ArrayList<>();

        @Override
        public synchronized HttpClient create() {
            created.incrementAndGet();
            HttpClient client = new NoNetworkClient();
            handedOut.add(client);
            return client;
        }

        @Override
        public void release(HttpClient client) {
            released.incrementAndGet();
            try {
                client.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class NoNetworkClient implements HttpClient {

        boolean closed;

        @Override
        public HttpResponse invoke(HttpRequest request) {
            HttpResponse response = new HttpResponse();
            response.setStatus(200);
            return response;
        }

        @Override
        public void apply(KarateConfig config) {
        }

        @Override
        public void abort() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static SuiteResult run(Path dir, CountingFactory factory) {
        return Runner.builder()
                .path(dir.toAbsolutePath().toString())
                .httpClientFactory(factory)
                .backupOutputDir(false)
                .outputHtmlReport(false)
                .parallel(1);
    }

    @Test
    void testEveryClientIsReleasedWhenItsScenarioEnds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("simple.feature"), """
                Feature: one scenario, one client

                  Scenario: plain
                    * def x = 1
                    * match x == 1
                """);
        CountingFactory factory = new CountingFactory();
        SuiteResult result = run(dir, factory);

        assertEquals(0, result.getScenarioFailedCount(), "the feature itself should pass");
        assertTrue(factory.created.get() > 0, "no client was created — the test proves nothing");
        assertEquals(factory.created.get(), factory.released.get(),
                "every created client must be released at the end of the scenario that owns it");
        for (HttpClient client : factory.handedOut) {
            assertTrue(((NoNetworkClient) client).closed, "a released client should have been closed");
        }
    }

    /**
     * The case that makes the count worth asserting: {@code karate.call()} builds a whole new
     * runtime, and therefore a whole new client, per call. Before the fix this feature created
     * four clients and released none.
     */
    @Test
    void testCalledFeaturesReleaseTheirOwnClients(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("callee.feature"), """
                Feature: callee

                  Scenario: called
                    * def answer = 42
                """);
        Files.writeString(dir.resolve("caller.feature"), """
                Feature: caller

                  Scenario: calls three times
                    * def a = karate.call('callee.feature')
                    * def b = karate.call('callee.feature')
                    * def c = karate.call('callee.feature')
                    * match a.answer == 42
                """);
        CountingFactory factory = new CountingFactory();
        SuiteResult result = run(dir, factory);

        assertEquals(0, result.getScenarioFailedCount(), "the features themselves should pass");
        // One per scenario, and a called feature is its own scenario — so this is well above the
        // two features involved. The exact number is not the assertion; create == release is.
        assertTrue(factory.created.get() >= 4,
                "expected a client per scenario including called ones, got " + factory.created.get());
        assertEquals(factory.created.get(), factory.released.get(),
                "a called feature's client must be released too — it owns its own");
    }

    /**
     * A dynamic Scenario Outline builds a throwaway runtime to evaluate its expression, and that
     * runtime never goes through the scenario call path — so its client was minted and never
     * handed back, once per such feature per run. Found by review, and it failed this assertion
     * (created 4, released 3) before the template runtime released explicitly.
     */
    @Test
    void testADynamicOutlineTemplateRuntimeReleasesItsClient(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dynamic.feature"), """
                Feature: dynamic outline

                  Scenario Outline: row <name>
                    * match name == '#string'

                    Examples:
                      | karate.setup().rows |

                  @setup
                  Scenario: setup
                    * def rows = [{ name: 'a' }, { name: 'b' }]
                """);
        CountingFactory factory = new CountingFactory();
        SuiteResult result = run(dir, factory);

        assertEquals(0, result.getScenarioFailedCount(), "the feature itself should pass");
        assertEquals(factory.created.get(), factory.released.get(),
                "the throwaway runtime that evaluates the dynamic expression owns a client too");
    }

    /**
     * A failing {@code karate-config.js} makes every scenario return early, before the block that
     * releases — so a whole suite's worth of clients was abandoned in exactly the situation a user
     * re-runs repeatedly. Found by review; released 0 of 1 before the early return released too.
     */
    @Test
    void testAConfigFailureStillReleasesTheClient(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("karate-config.js"), "function() { throw 'boom'; }");
        Files.writeString(dir.resolve("simple.feature"), """
                Feature: config is broken

                  Scenario: never really starts
                    * def x = 1
                """);
        CountingFactory factory = new CountingFactory();
        Runner.builder()
                .path(dir.toAbsolutePath().toString())
                .configDir(dir.toAbsolutePath().toString())
                .httpClientFactory(factory)
                .backupOutputDir(false)
                .outputHtmlReport(false)
                .parallel(1);

        assertTrue(factory.created.get() > 0, "no client was created — the test proves nothing");
        assertEquals(factory.created.get(), factory.released.get(),
                "a scenario that dies in config still owns a client it must hand back");
    }

    /**
     * {@code ApacheHttpClient.apply()} closes the outgoing client before rebuilding, and a review
     * asked whether that can terminate a still-open exchange. It cannot, and this pins the two
     * sequences that would show it if it could: a {@code configure} step between two requests, and
     * a shared-scope {@code call} between two requests — {@code apply()} fires on both.
     *
     * <p>The reason it is safe is structural. {@code invoke()} uses the response-handler form of
     * {@code execute}, and the handler reads the entity to a {@code byte[]} before returning, so
     * the exchange is complete and the connection released before {@code invoke()} returns.
     * {@code HttpResponse} carries bytes, never a stream — there is no user-visible response that
     * can outlive the call.
     *
     * <p>Concurrent sharing of a single {@code ApacheHttpClient} is a different matter, and one
     * that predates this change: {@code request}, {@code startTime} and {@code currentRequest} are
     * per-instance fields overwritten by every call, so two scenarios sharing one instance already
     * corrupt each other's request state. Closing does not add a new class of failure there.
     */
    @Test
    void testAClientSurvivesConfigureAndSharedScopeCallsBetweenRequests(@TempDir Path dir)
            throws IOException {
        MockServer server = MockServer.featureString("""
                Feature: echo

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """).port(0).start();
        try {
            String url = "http://localhost:" + server.getPort();
            Files.writeString(dir.resolve("shared.feature"), """
                    Feature: shared scope callee

                      Scenario: called
                        * def fromCallee = 1
                    """);
            Files.writeString(dir.resolve("reconfigure.feature"), """
                    Feature: apply() fires between these requests

                      Scenario: request, configure, request, shared call, request
                        * url '%s'
                        * path 'ping'
                        * method get
                        * status 200
                        * match response.ok == true

                        # configure calls apply(), which closes the built client
                        * configure readTimeout = 20000
                        * path 'ping'
                        * method get
                        * status 200

                        # a shared-scope call calls apply() unconditionally on the way out
                        * call read('shared.feature')
                        * match fromCallee == 1
                        * path 'ping'
                        * method get
                        * status 200
                        * match response.ok == true
                    """.formatted(url));
            SuiteResult result = Runner.builder()
                    .path(dir.resolve("reconfigure.feature").toAbsolutePath().toString())
                    .backupOutputDir(false)
                    .outputHtmlReport(false)
                    .parallel(1);
            assertEquals(0, result.getScenarioFailedCount(),
                    "closing the client on configure/shared-call must not break the next request");
        } finally {
            server.stopAndWait();
        }
    }

    /**
     * A custom factory that shares one client must not have it closed underneath it. This is why
     * {@link HttpClientFactory#release} defaults to doing nothing rather than closing: the
     * {@code () -> client} lambda form is common — the project's own test utilities use it — and
     * a closing default would tear down a shared client after the first scenario.
     */
    @Test
    void testASharedClientFromALambdaFactoryIsNotClosed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("two.feature"), """
                Feature: two scenarios sharing one client

                  Scenario: first
                    * def x = 1

                  Scenario: second
                    * def y = 2
                """);
        NoNetworkClient shared = new NoNetworkClient();
        SuiteResult result = Runner.builder()
                .path(dir.toAbsolutePath().toString())
                .httpClientFactory(() -> shared)
                .backupOutputDir(false)
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(0, result.getScenarioFailedCount());
        assertTrue(!shared.closed,
                "a shared client from a custom factory must survive the scenarios that used it");
    }

}
