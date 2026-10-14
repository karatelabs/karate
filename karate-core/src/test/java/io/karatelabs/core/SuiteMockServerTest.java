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
package io.karatelabs.core;

import io.karatelabs.output.ResultListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mock server {@code karate.start()} opens belongs to the Suite that was running (DESIGN.md "Suite isolation"):
 * stopped when the Suite's run ends, each Suite its own, unless a host took them with {@link Suite#detachServers()}
 * or the callSingle cache that started it outlives the Suite.
 */
public class SuiteMockServerTest {

    @TempDir
    Path root;

    static volatile CountDownLatch gate;

    /** The held scenario's step: blocks until the test lets its Suite finish. */
    public static void hold() throws InterruptedException {
        CountDownLatch g = gate;
        if (g != null) {
            g.await();
        }
    }

    static volatile Path nestedRoot;
    static volatile boolean nestedPassed;
    static volatile boolean nestedMockOutlivedItsSuite;
    static final List<MockServer> nestedTaken = new ArrayList<>();

    /** The nesting scenario's step: runs a second Suite to completion on this thread, then once more detaching. */
    public static void runNested() {
        AtomicReference<Integer> port = new AtomicReference<>();
        nestedPassed = suite(nestedRoot, "checks/ping.feature", port).parallel(1).isPassed();
        nestedMockOutlivedItsSuite = listening(port.get());
        suite(nestedRoot, "checks/ping.feature", port)
                .listener(event -> {
                    if (event instanceof SuiteRunEvent se && se.type() == RunEventType.SUITE_EXIT) {
                        nestedTaken.addAll(se.source().detachServers());
                    }
                    return true;
                })
                .parallel(1);
    }

    void project() throws IOException {
        Files.writeString(root.resolve("karate-config.js"), """
                function fn() {
                  return { baseUrl: karate.callSingle('/mock/start.js').url };
                }
                """);
        Path mock = Files.createDirectories(root.resolve("mock"));
        Files.writeString(mock.resolve("start.js"), """
                var server = karate.start({ mock: '/mock/api.feature', port: 0 });
                ({ url: server.url, server: server })
                """);
        Files.writeString(mock.resolve("api.feature"), """
                Feature: mock

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """);
        Path checks = Files.createDirectories(root.resolve("checks"));
        Files.writeString(checks.resolve("ping.feature"), """
                Feature: ping

                Scenario: ping
                  Given url baseUrl
                  And path 'ping'
                  When method get
                  Then status 200
                """);
        Files.writeString(checks.resolve("hold.feature"), """
                Feature: hold

                Scenario: ping then wait
                  Given url baseUrl
                  And path 'ping'
                  When method get
                  Then status 200
                  * Java.type('io.karatelabs.core.SuiteMockServerTest').hold()
                """);
        Files.writeString(checks.resolve("nested.feature"), """
                Feature: nested

                Scenario: run another Suite from a step
                  * Java.type('io.karatelabs.core.SuiteMockServerTest').runNested()
                """);
        Files.writeString(mock.resolve("outer.feature"), """
                Feature: outer mock

                Background:
                  * def inner = karate.start('/mock/api.feature')

                Scenario: pathMatches('/ping')
                  * def response = { inner: '#(inner.port)' }
                """);
        Files.writeString(checks.resolve("outer.feature"), """
                Feature: outer

                Scenario: ping the mock whose Background started another
                  * def outer = karate.start('/mock/outer.feature')
                  Given url outer.url
                  And path 'ping'
                  When method get
                  Then status 200
                """);
    }

    Runner.Builder suite(String feature, AtomicReference<Integer> port) {
        return suite(root, feature, port);
    }

    static Runner.Builder suite(Path root, String feature, AtomicReference<Integer> port) {
        return Runner.path(root.resolve(feature).toString())
                .workingDir(root).configDir(root.toString())
                .outputDir(root.resolve("target").resolve(feature.replace('/', '-')))
                .outputHtmlReport(false).outputConsoleSummary(false)
                .listener(event -> {
                    if (event instanceof HttpRunEvent h && h.type() == RunEventType.HTTP_EXIT) {
                        port.set(URI.create(h.request().getUrlBase()).getPort());
                    }
                    return true;
                });
    }

    static boolean listening(int port) {
        try {
            for (InetAddress address : InetAddress.getAllByName("localhost")) {
                try (Socket s = new Socket(address, port)) {
                    return true;
                } catch (IOException ignored) {
                    // this address is not bound; try the next
                }
            }
        } catch (IOException ignored) {
            // no address resolves: nothing listens
        }
        return false;
    }

    @Test
    void theMockTheConfigStartedStopsWithItsSuite() throws Exception {
        project();
        AtomicReference<Integer> port = new AtomicReference<>();
        SuiteResult result = suite("checks/ping.feature", port).parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        assertNotNull(port.get(), "the scenario called the mock");
        assertFalse(listening(port.get()), "the mock on " + port.get() + " outlived its Suite");
    }

    @Test
    void twoSuitesSideBySideEachStopOnlyTheirOwn() throws Exception {
        project();
        gate = new CountDownLatch(1);
        AtomicReference<Integer> held = new AtomicReference<>();
        AtomicReference<SuiteResult> heldResult = new AtomicReference<>();
        Thread t = new Thread(() -> heldResult.set(suite("checks/hold.feature", held).parallel(1)));
        t.start();
        try {
            for (int i = 0; i < 200 && held.get() == null; i++) {
                Thread.sleep(50);
            }
            assertNotNull(held.get(), "the held Suite reached its mock");
            AtomicReference<Integer> other = new AtomicReference<>();
            assertTrue(suite("checks/ping.feature", other).parallel(1).isPassed());
            assertNotEquals(held.get(), other.get(), "each Suite started its own mock");
            assertFalse(listening(other.get()), "the finished Suite's mock stopped");
            assertTrue(listening(held.get()), "the Suite still running keeps its mock");
        } finally {
            gate.countDown();
            t.join(30_000);
        }
        assertTrue(heldResult.get().isPassed(), () -> heldResult.get().getErrors().toString());
        assertFalse(listening(held.get()), "the held Suite's mock stopped once it ended");
    }

    @Test
    void aHostThatDetachesTheServersOwnsThemPastTheSuite() throws Exception {
        project();
        AtomicReference<Integer> port = new AtomicReference<>();
        List<MockServer> taken = new ArrayList<>();
        SuiteResult result = suite("checks/ping.feature", port)
                .listener(event -> {
                    if (event instanceof SuiteRunEvent se && se.type() == RunEventType.SUITE_EXIT) {
                        taken.addAll(se.source().detachServers());
                    }
                    return true;
                })
                .parallel(1);
        assertTrue(result.isPassed());
        assertEquals(1, taken.size());
        assertEquals(port.get(), taken.get(0).getPort());
        assertTrue(listening(port.get()), "the detached mock survives the Suite");
        taken.get(0).stopAndWait();
        assertFalse(listening(port.get()));
    }

    @Test
    void aResultListenerThatThrowsAtSuiteEndStillLetsTheMockStop() throws Exception {
        project();
        AtomicReference<Integer> port = new AtomicReference<>();
        Runner.Builder builder = suite("checks/ping.feature", port).resultListener(new ResultListener() {
            @Override
            public void onSuiteEnd(SuiteResult result) {
                throw new IllegalStateException("report failed");
            }
        });
        assertThrows(IllegalStateException.class, () -> builder.parallel(1));
        assertFalse(listening(port.get()), "the mock outlived a Suite whose listener threw");
    }

    /** A Suite run from a step of another (Java glue) evaluates its config before any of its scenarios is current
     *  on the thread: the mock that config starts is still the nested Suite's own. */
    @Test
    void aSuiteNestedInAScenarioOwnsTheMockItsConfigStarted() throws Exception {
        project();
        nestedRoot = root;
        nestedTaken.clear();
        AtomicReference<Integer> port = new AtomicReference<>();
        SuiteResult result = suite("checks/nested.feature", port).parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        assertTrue(nestedPassed);
        assertFalse(nestedMockOutlivedItsSuite, "the nested Suite stopped the mock its config started");
        assertEquals(1, nestedTaken.size(), "the nested Suite's detach hands over its config's mock");
        nestedTaken.get(0).stopAndWait();
    }

    /** A mock's Background runs while karate.start() opens it, so what it starts belongs to the same Suite. */
    @Test
    void aMockStartedFromAMockBackgroundStopsWithTheSuite() throws Exception {
        project();
        AtomicReference<Integer> outer = new AtomicReference<>();
        AtomicReference<Integer> inner = new AtomicReference<>();
        SuiteResult result = suite("checks/outer.feature", outer)
                .listener(event -> {
                    if (event instanceof HttpRunEvent h && h.type() == RunEventType.HTTP_EXIT) {
                        inner.set(Integer.parseInt(h.response().getBodyString().replaceAll("\\D", "")));
                    }
                    return true;
                })
                .parallel(1);
        assertTrue(result.isPassed(), () -> result.getErrors().toString());
        assertFalse(listening(outer.get()), "the outer mock stopped");
        assertFalse(listening(inner.get()), "the mock the outer's Background started stopped too");
    }

    /** A host detaching while the Suite's own shutdown detaches: each server goes to exactly one of them. */
    @Test
    void concurrentDetachesTakeEachServerOnce() throws Exception {
        project();
        Suite suite = suite("checks/ping.feature", new AtomicReference<>()).buildSuite();
        List<MockServer> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            pool.add(MockServer.feature(root.resolve("mock/api.feature").toString()).start());
        }
        try {
            for (int round = 0; round < 200; round++) {
                pool.forEach(suite::ownServer);
                CyclicBarrier go = new CyclicBarrier(2);
                List<List<MockServer>> taken = new CopyOnWriteArrayList<>();
                Thread a = new Thread(() -> taken.add(detachAfter(go, suite)));
                Thread b = new Thread(() -> taken.add(detachAfter(go, suite)));
                a.start();
                b.start();
                a.join();
                b.join();
                int r = round;
                assertEquals(40, taken.get(0).size() + taken.get(1).size(), () -> "round " + r + ": a server taken twice");
            }
        } finally {
            pool.forEach(MockServer::stopAndWait);
        }
    }

    static List<MockServer> detachAfter(CyclicBarrier go, Suite suite) {
        try {
            go.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return suite.detachServers();
    }

    /** A host that injects the callSingle cache (karate-gatling) runs callSingle once for every Suite that shares it,
     *  so what callSingle started serves them all and is the host's to stop. */
    @Test
    void aMockStartedUnderASharedCallSingleCacheServesTheNextSuite() throws Exception {
        project();
        Map<String, Object> cache = new ConcurrentHashMap<>();
        ReentrantLock lock = new ReentrantLock();
        AtomicReference<Integer> first = new AtomicReference<>();
        AtomicReference<Integer> second = new AtomicReference<>();
        assertTrue(suite("checks/ping.feature", first).callSingleCache(cache, lock).parallel(1).isPassed());
        assertTrue(listening(first.get()), "the shared cache's mock outlives the Suite");
        SuiteResult again = suite("checks/ping.feature", second).callSingleCache(cache, lock).parallel(1);
        assertTrue(again.isPassed(), () -> again.getErrors().toString());
        assertEquals(first.get(), second.get(), "the second Suite reused the cached mock");
        MockServer server = (MockServer) ((Map<?, ?>) cache.get("/mock/start.js")).get("server");
        server.stopAndWait();
        assertFalse(listening(first.get()));
    }
}
