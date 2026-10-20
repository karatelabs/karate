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
package io.karatelabs.driver;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.core.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code driver.quit()} on a pooled driver frees its slot, whichever scenario in a call chain
 * acquired it and whichever one quit it.
 */
class PooledDriverQuitTest {

    @TempDir
    Path tempDir;

    private static Driver fakeDriver() {
        boolean[] terminated = {false};
        String[] url = {null};
        return (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[]{Driver.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "toString": return "fake-driver";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "quit": terminated[0] = true; return null;
                        case "isTerminated": return terminated[0];
                        case "isResponsive": return !terminated[0];
                        case "setUrl": url[0] = (String) args[0]; return null;
                        case "getUrl": return url[0];
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    Class<?> rt = method.getReturnType();
                    return rt.isPrimitive() && rt != void.class ? Array.get(Array.newInstance(rt, 1), 0) : null;
                });
    }

    static class FakePool extends PooledDriverProvider {

        final AtomicInteger created = new AtomicInteger();

        FakePool(int size) {
            super(size);
        }

        @Override
        protected Driver createDriver(Map<String, Object> config) {
            created.incrementAndGet();
            return fakeDriver();
        }

    }

    @Test
    void testQuitInCallChainFreesPoolSlot() throws Exception {
        Files.writeString(tempDir.resolve("open-browser.feature"), """
            @ignore
            Feature: open a browser
            Scenario:
            * configure driver = { type: 'chrome' }
            * driver 'about:blank'
            """);
        Files.writeString(tempDir.resolve("open-and-quit.feature"), """
            @ignore
            Feature: open a browser, then quit it
            Scenario:
            * configure driver = { type: 'chrome' }
            * driver 'about:blank'
            * driver.quit()
            """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
            Feature: start a new browser after driver.quit()

            Scenario: quit, then start a new browser
            * configure driver = { type: 'chrome' }
            * driver 'about:blank'
            * driver.quit()
            * driver 'about:blank'
            * match driver.url == 'about:blank'

            Scenario: a called feature quits its browser, then the caller starts a new one
            * call read('open-and-quit.feature')
            * call read('open-browser.feature')
            * match driver.url == 'about:blank'

            Scenario: the caller quits a browser a called feature opened, then starts a new one
            * call read('open-browser.feature')
            * driver.quit()
            * call read('open-browser.feature')
            * match driver.url == 'about:blank'

            Scenario: a later scenario starts a browser
            * configure driver = { type: 'chrome' }
            * driver 'about:blank'
            * match driver.url == 'about:blank'
            """);
        FakePool pool = new FakePool(1);
        SuiteResult result = TestUtils.testBuilder()
                .path(main.toString())
                .workingDir(tempDir)
                .driverProvider(pool)
                .parallel(1);
        List<String> failures = new ArrayList<>();
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    failures.add(sr.getScenario().getName() + ": " + sr.getFailureMessage());
                }
            }
        }
        assertEquals(List.of(), failures);
        assertEquals(4, pool.created.get());
        assertEquals("PooledDriverProvider[size=1, created=1, available=0, assigned=0]", pool.getStats());
    }

    @Test
    void testParallelScenariosShareOneSlot() throws Exception {
        Path main = tempDir.resolve("parallel.feature");
        Files.writeString(main, """
            Feature: more parallel scenarios than pool slots

            Scenario Outline: scenario <n>
            * configure driver = { type: 'chrome' }
            * driver 'about:blank'
            * java.lang.Thread.sleep(20)
            * if (<n> % 3 == 0) driver.quit()

            Examples:
            | n |
            | 1 |
            | 2 |
            | 3 |
            | 4 |
            | 5 |
            | 6 |
            | 7 |
            | 8 |
            | 9 |
            | 10 |
            | 11 |
            | 12 |
            """);
        FakePool pool = new FakePool(1);
        SuiteResult result = TestUtils.testBuilder()
                .path(main.toString())
                .workingDir(tempDir)
                .driverProvider(pool)
                .parallel(8);
        assertEquals(12, result.getScenarioPassedCount());
        // one per quit, except a quit by the last scenario to finish, which leaves no driver open
        int created = pool.created.get();
        assertTrue(created == 4 || created == 5, "created " + created);
        assertEquals("PooledDriverProvider[size=1, created=" + (created - 4) + ", available=0, assigned=0]",
                pool.getStats());
    }

    @Test
    void testReleaseRacingShutdownClosesDriver() {
        ScenarioRuntime runtime = TestUtils.run("* def a = 1");
        PooledDriverProvider pool = new FakePool(1) {
            @Override
            protected void cleanScenarioState(Driver driver) {
                shutdown(); // lands after release's shutdown check, before its offer
            }
        };
        Driver driver = pool.acquire(runtime, Map.of());
        pool.release(runtime, driver);
        assertTrue(driver.isTerminated());
        assertTrue(pool.getStats().contains("available=0"), pool.getStats());
    }

}
