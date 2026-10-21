/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.core.mock;

import io.karatelabs.common.Resource;
import io.karatelabs.core.MockHandler;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.js.JavaInvokable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The test that started a mock reads the mock's variables with {@code mock.get('name')} — a
 * snapshot, so the mock's later requests do not change it and the test cannot change the mock.
 */
class MockServerVariableTest {

    @TempDir
    Path root;

    static final String CRUD_MOCK = """
            Feature: crud mock

            Background:
              * def items = {}
              * def counter = 0

            Scenario: pathMatches('/items') && methodIs('post')
              * def counter = counter + 1
              * def id = counter + ''
              * items[id] = request
              * def response = items[id]

            Scenario: pathMatches('/items') && methodIs('get')
              * def response = karate.valuesOf(items)
            """;

    @Test
    void theCallingFeatureReadsTheMocksVariables() throws Exception {
        Files.writeString(root.resolve("mock.feature"), CRUD_MOCK);
        Files.writeString(root.resolve("test.feature"), """
                Feature: reads mock state

                Scenario: mock.get
                  * def mock = karate.start({ mock: 'mock.feature', port: 0 })
                  * url mock.url
                  * path 'items'
                  * request { name: 'foo' }
                  * method post
                  * status 200
                  * match mock.get('items') == { '1': { name: 'foo' } }
                  * match mock.get('counter') == 1
                  * match mock.get('nope') == null
                  * match mock.get('nope', []) == []
                  * match mock.get('counter', 99) == 1

                  # a snapshot: changing it leaves the mock alone
                  * def snapshot = mock.get('items')
                  * snapshot['2'] = { name: 'injected' }
                  * url mock.url
                  * path 'items'
                  * method get
                  * match response == [{ name: 'foo' }]

                  # ... and a later request does not change it
                  * url mock.url
                  * path 'items'
                  * request { name: 'bar' }
                  * method post
                  * match snapshot == { '1': { name: 'foo' }, '2': { name: 'injected' } }
                  * match mock.get('items') == { '1': { name: 'foo' }, '2': { name: 'bar' } }
                """);
        SuiteResult result = Runner.path(root.resolve("test.feature").toString())
                .workingDir(root).configDir(root.toString())
                .outputDir(root.resolve("target"))
                .outputHtmlReport(false).outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSnapshotIsDetachedFromTheMocksState() {
        MockHandler handler = new MockHandler(Feature.read(Resource.text(CRUD_MOCK)));
        handler.apply(post("{\"name\":\"foo\"}"));
        Map<String, Object> snapshot = (Map<String, Object>) handler.getVariableSnapshot("items");
        ((Map<String, Object>) snapshot.get("1")).put("name", "changed");
        snapshot.put("2", Map.of("name", "injected"));
        handler.apply(post("{\"name\":\"bar\"}"));
        assertEquals(Map.of("1", Map.of("name", "changed"), "2", Map.of("name", "injected")), snapshot);
        assertEquals(Map.of("1", Map.of("name", "foo"), "2", Map.of("name", "bar")), handler.getVariableSnapshot("items"));
    }

    static HttpRequest post(String json) {
        HttpRequest request = new HttpRequest();
        request.setMethod("POST");
        request.setPath("/items");
        request.setBody(json.getBytes(StandardCharsets.UTF_8));
        request.putHeader("Content-Type", "application/json");
        return request;
    }

    @Test
    void aSnapshotNeverRacesARequestThatIsWritingTheVariable() throws Exception {
        MockHandler handler = new MockHandler(Feature.read(Resource.text(CRUD_MOCK)));
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = Thread.ofPlatform().start(() -> {
            try {
                for (int i = 0; i < 2000; i++) {
                    assertEquals(200, handler.apply(post("{\"n\":" + i + "}")).getStatus());
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.set(true);
            }
        });
        try {
            do {
                // a deep copy walks every entry — without the request lock it iterates a map the
                // writer is adding to, and fails or sees an entry whose value is not yet assigned
                Map<?, ?> items = (Map<?, ?>) handler.getVariableSnapshot("items");
                for (Object value : items.values()) {
                    assertNotNull(value);
                }
            } while (!done.get());
        } finally {
            writer.join();
        }
        assertNull(failure.get(), () -> "writer failed: " + failure.get());
        assertEquals(2000, ((Map<?, ?>) handler.getVariableSnapshot("items")).size());
    }

    @Test
    void aSnapshotWaitsForTheRequestInFlight() throws Exception {
        // the mock parks mid-request, between two writes to the same variable — a snapshot taken
        // then must wait and see both writes, never the half-way state
        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JavaInvokable park = args -> {
            parked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        };
        MockHandler handler = new MockHandler(Feature.read(Resource.text("""
                Feature: parks mid-request

                Background:
                  * def items = {}

                Scenario: pathMatches('/items')
                  * items.first = true
                  * eval park()
                  * items.second = true
                  * def response = items
                """)), Map.of("park", park));
        Thread writer = Thread.ofPlatform().start(() -> handler.apply(post("{}")));
        try {
            assertTrue(parked.await(10, TimeUnit.SECONDS));
            CompletableFuture<Object> snapshot = CompletableFuture.supplyAsync(() -> handler.getVariableSnapshot("items"));
            assertThrows(TimeoutException.class, () -> snapshot.get(200, TimeUnit.MILLISECONDS),
                    "the snapshot did not wait for the request in flight");
            release.countDown();
            assertEquals(Map.of("first", true, "second", true), snapshot.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            writer.join();
        }
    }

    @Test
    void aDateInARawJsArraySnapshotsAsADate() {
        MockHandler handler = new MockHandler(Feature.read(Resource.text("""
                Feature: raw js values

                Background:
                  * eval var dates = [new Date(0)]

                Scenario:
                  * def response = 'ok'
                """)));
        assertEquals(List.of(new Date(0)), handler.getVariableSnapshot("dates"));
    }

}
