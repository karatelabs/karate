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
package io.karatelabs.gatling;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.PerfEvent;
import io.karatelabs.core.PerfHook;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.StepResult;
import io.karatelabs.http.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Under Gatling there is no HTML report and nothing else reads Karate's per-step log, so building
 * it — which re-parses and pretty-prints every response body — would be pure waste. These tests pin
 * down that it is off by default in this lane, that opting back in restores it, and that turning it
 * off does not take {@code print} off the console with it.
 */
class StepLogCaptureTest {

    private static final String FEATURE = "classpath:features/cats-create-print.feature";

    /** Naming an event null means "do not report it" — no stats engine is involved here. */
    private static final PerfHook PERF_HOOK = new PerfHook() {
        @Override
        public String getPerfEventName(HttpRequest request, ScenarioRuntime runtime) {
            return null;
        }

        @Override
        public void reportPerfEvent(PerfEvent event) {
        }
    };

    private Logger scenarioLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeAll
    static void beforeAll() {
        CatsMockServer.start();
    }

    @BeforeEach
    void beforeEach() {
        // the test logback config pins karate.* well above INFO, which is where print lands —
        // so listen on the category directly rather than depending on the console setup
        scenarioLogger = (Logger) LoggerFactory.getLogger("karate.scenario");
        originalLevel = scenarioLogger.getLevel();
        scenarioLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        scenarioLogger.addAppender(appender);
    }

    @AfterEach
    void afterEach() {
        scenarioLogger.detachAppender(appender);
        appender.stop();
        scenarioLogger.setLevel(originalLevel);
    }

    private FeatureResult run(Runner.Builder template) {
        Map<String, Object> arg = new HashMap<>();
        arg.put(KarateProtocol.GATLING_KEY, new HashMap<String, Object>());
        arg.put(KarateProtocol.KARATE_KEY, new HashMap<String, Object>());
        return Runner.runFeature(FEATURE, arg, PERF_HOOK, null, template);
    }

    private static String stepLogs(FeatureResult result) {
        StringBuilder sb = new StringBuilder();
        for (ScenarioResult sr : result.getScenarioResults()) {
            for (StepResult step : sr.getStepResults()) {
                if (step.getLog() != null) {
                    sb.append(step.getLog());
                }
            }
        }
        return sb.toString();
    }

    private List<String> printed() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void perfModeCapturesNothingByDefault() {
        FeatureResult result = run(null);

        assertFalse(result.isFailed(), "the feature itself should pass");
        assertEquals("", stepLogs(result),
                "nothing reads the per-step log under Gatling, so none of it should be built");
    }

    @Test
    void printStillReachesTheConsoleWithCaptureOff() {
        // assert the run itself was healthy, so a feature that broke before the print step
        // reads as a broken feature rather than as a missing log line
        assertFalse(run(null).isFailed());

        // this is what makes "no logging unless you ask for it" a default rather than a gag:
        // the user's own output still goes wherever the Logback config sends it
        assertTrue(printed().contains("printed by the feature"),
                "print must survive capture being off: " + printed());
    }

    @Test
    void captureStepLogsOptsBackIn() {
        FeatureResult result = run(Runner.builder().captureStepLogs(true));

        String log = stepLogs(result);
        assertTrue(log.contains("POST http://localhost"), "the request block should be captured: " + log);
        // the pretty-printed response body — the expensive part, and the point of the flag
        assertTrue(log.contains("CaptureKitty"), "the response body should be captured: " + log);
        assertTrue(log.contains("printed by the feature"), "print should be captured too: " + log);
    }

    /**
     * The one reader of the per-step log in this lane. Asking for replay has to switch capture back
     * on, or replay is a feature that silently does nothing — see {@link KarateProtocolBuilder}.
     */
    @Test
    void logReplaySwitchesCaptureBackOn() {
        KarateProtocol protocol = KarateDsl.karateProtocol().logReplay("all").build();

        FeatureResult result = run(protocol.getRunner());

        assertTrue(stepLogs(result).contains("CaptureKitty"),
                "log replay reads exactly this buffer, so it must be filled");
    }
}
