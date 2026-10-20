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
import io.karatelabs.driver.PooledDriverProvider;
import io.karatelabs.test.LogSilencer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An unsupported {@code configure driver} type must fail the scenario before
 * any browser is acquired, instead of silently falling back to desktop Chrome
 * (which lets a mobile suite pass without touching a device). A custom
 * {@link DriverProvider} owns its own type vocabulary and is not checked.
 */
class DriverTypeTest {

    @TempDir
    Path tempDir;

    static class CountingDriverProvider implements DriverProvider {

        final AtomicInteger acquired = new AtomicInteger();

        @Override
        public Driver acquire(ScenarioRuntime runtime, Map<String, Object> config) {
            acquired.incrementAndGet();
            return AfterScenarioDriverHookTest.fakeDriver();
        }

        @Override
        public void release(ScenarioRuntime runtime, Driver driver) {
        }

        @Override
        public void shutdown() {
        }

    }

    private SuiteResult run(String type, DriverProvider provider) throws Exception {
        Path feature = tempDir.resolve("driver-type.feature");
        Files.writeString(feature, """
            Feature: driver type

            Scenario: start a driver
              * configure driver = { type: '%s', webDriverUrl: 'http://127.0.0.1:4799', start: false }
              * driver 'about:blank'
            """.formatted(type));
        return LogSilencer.silenced("karate.runtime", () -> TestUtils.testBuilder()
                .path(feature.toString())
                .workingDir(tempDir)
                .driverProvider(provider)
                .parallel(1));
    }

    private static String failureMessage(SuiteResult result) {
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return null;
    }

    // the built-in pool rejects the type before acquiring, so no browser is launched
    @ParameterizedTest
    @ValueSource(strings = {"android", "ios", "winappdriver", "nonsense"})
    void testUnsupportedTypeFailsWithBuiltInDispatch(String type) throws Exception {
        SuiteResult result = run(type, new PooledDriverProvider(1));
        assertFalse(result.isPassed(), "unsupported driver type must fail the scenario");
        String msg = failureMessage(result);
        assertNotNull(msg);
        assertTrue(msg.contains("unsupported driver type: '" + type + "'"), msg);
        assertTrue(msg.contains("chromedriver"), "message should list the supported types: " + msg);
    }

    @Test
    void testMobileTypeMentionsAppium() throws Exception {
        String msg = failureMessage(run("ios", new PooledDriverProvider(1)));
        assertNotNull(msg);
        assertTrue(msg.contains("Appium"), msg);
    }

    @Test
    void testCustomProviderOwnsItsTypes() throws Exception {
        CountingDriverProvider provider = new CountingDriverProvider();
        SuiteResult result = run("android", provider);
        assertTrue(result.isPassed(), String.valueOf(failureMessage(result)));
        assertEquals(1, provider.acquired.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "chromedriver", "geckodriver", "safaridriver", "msedgedriver"})
    void testSupportedTypeAcquiresDriver(String type) throws Exception {
        CountingDriverProvider provider = new CountingDriverProvider();
        SuiteResult result = run(type, provider);
        assertTrue(result.isPassed(), String.valueOf(failureMessage(result)));
        assertEquals(1, provider.acquired.get());
    }

}
