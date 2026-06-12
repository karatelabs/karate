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
package io.karatelabs.core;

import io.karatelabs.driver.Driver;
import io.karatelabs.driver.DriverProvider;
import io.karatelabs.test.LogSilencer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The afterScenario hook must run while the scenario still owns its driver.
 * <p>
 * The screenshot-on-failure teardown idiom — an afterScenario hook that calls
 * {@code switchFrame(null)} / {@code screenshot()} — only works if the hook
 * fires before the driver is released. When the driver went back to the pool
 * first, a parallel scenario could acquire (and reset) the same browser while
 * the hook was still using it, producing blank screenshots or cross-scenario
 * interference that only surfaced under concurrency.
 */
public class AfterScenarioDriverHookTest {

    @TempDir
    Path tempDir;

    private static final List<String> events = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void reset() {
        events.clear();
    }

    public static void record(String event) {
        events.add(event);
    }

    /**
     * A no-op {@link Driver} backed by a dynamic proxy: default methods (jsGet
     * and friends) execute for real so the driver behaves like a Driver from
     * the engine's point of view, while the backend primitives (navigate,
     * switchFrame, screenshot, ...) are stubbed out.
     */
    private static Driver fakeDriver() {
        return (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(),
                new Class<?>[]{Driver.class}, AfterScenarioDriverHookTest::stubInvoke);
    }

    private static Object stubInvoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "fake-driver";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        Class<?> rt = method.getReturnType();
        if (rt == byte[].class) {
            return new byte[0];
        }
        if (rt == boolean.class) {
            return false;
        }
        if (rt == int.class) {
            return 0;
        }
        if (rt == long.class) {
            return 0L;
        }
        if (rt == double.class) {
            return 0d;
        }
        return null;
    }

    static class RecordingDriverProvider implements DriverProvider {

        private final Driver driver = fakeDriver();

        @Override
        public Driver acquire(ScenarioRuntime runtime, Map<String, Object> config) {
            record("acquire");
            return driver;
        }

        @Override
        public void release(ScenarioRuntime runtime, Driver driver) {
            record("release");
        }

        @Override
        public void shutdown() {
        }

    }

    @Test
    void testAfterScenarioHookRunsBeforeDriverRelease() throws Exception {
        Path feature = tempDir.resolve("hook-driver-order.feature");
        Files.writeString(feature, """
            Feature: afterScenario hook can use the driver for an error screenshot

            Background:
              * configure driver = { type: 'chrome', screenshotOnFailure: false }
              * configure afterScenario =
              \"\"\"
              function(){
                switchFrame(null);
                var img = screenshot();
                Java.type('io.karatelabs.core.AfterScenarioDriverHookTest').record('hook');
              }
              \"\"\"

            Scenario: fails after the driver has started
              * driver 'about:blank'
              * match 1 == 2
            """);

        RecordingDriverProvider provider = new RecordingDriverProvider();
        SuiteResult result = LogSilencer.silenced("karate.runtime", () -> TestUtils.testBuilder()
                .path(feature.toString())
                .workingDir(tempDir)
                .driverProvider(provider)
                .parallel(1));

        assertFalse(result.isPassed(), "scenario should fail on the match step");
        assertEquals(List.of("acquire", "hook", "release"), List.copyOf(events),
                "afterScenario hook must run before the driver is released to the provider");

        String msg = getFailureMessage(result);
        assertFalse(msg.contains("switchFrame"),
                "hook should resolve driver bindings, not fail with a ReferenceError: " + msg);
    }

    private String getFailureMessage(SuiteResult result) {
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "none";
    }

}
