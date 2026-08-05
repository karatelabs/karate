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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.karatelabs.gatling.KarateDsl.karateProtocol;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A load run is normally pinned to a high Logback level, so the per-step Karate output — the HTTP
 * request / response blocks and {@code print} statements — never reaches the log, and a feature that
 * fails mid-scenario arrives with no context. Karate captures that output regardless of the Logback
 * level, so these tests pin down that it is held per virtual user and released only when something
 * actually fails.
 */
class LogReplayerTest {

    private static final String PASS_FEATURE = "classpath:features/cats-create.feature";
    private static final String FAIL_FEATURE = "classpath:features/cats-create-fail.feature";

    @SuppressWarnings("unchecked")
    private static final scala.collection.immutable.List<String> NO_GROUPS =
            (scala.collection.immutable.List<String>) (Object) scala.collection.immutable.Nil$.MODULE$;

    private Logger replayLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeAll
    static void beforeAll() {
        CatsMockServer.start();
    }

    @BeforeEach
    void beforeEach() {
        replayLogger = (Logger) LoggerFactory.getLogger(LogReplayer.class);
        appender = new ListAppender<>();
        appender.start();
        replayLogger.addAppender(appender);
    }

    @AfterEach
    void afterEach() {
        replayLogger.detachAppender(appender);
        appender.stop();
    }

    private List<String> replayed() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Run one feature the way the Gatling action does, threading the buffer through by hand. */
    private KarateExecutor.ExecutionResult run(String featurePath, KarateProtocol protocol, LogReplayer.Buffer buffer) {
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "ReplayKitty");
        // silent=true: perf events are not under test here, so no stats reporter is needed
        KarateExecutor executor = new KarateExecutor(featurePath, null, protocol, true);
        return executor.execute(gatlingVars, new HashMap<>(), buffer, null, "test", NO_GROUPS);
    }

    @Test
    void offByDefaultSoNothingIsHeldOrLogged() {
        KarateProtocol protocol = karateProtocol().build();

        KarateExecutor.ExecutionResult passed = run(PASS_FEATURE, protocol, null);
        assertNull(passed.logBuffer, "nothing should be retained when replay is off");

        KarateExecutor.ExecutionResult failed = run(FAIL_FEATURE, protocol, passed.logBuffer);
        assertFalse(failed.success);
        assertTrue(replayed().isEmpty(), "nothing should be replayed when replay is off: " + replayed());
    }

    @Test
    void failedModeReplaysTheFailingFeatureOnly() {
        KarateProtocol protocol = karateProtocol().logReplay(KarateLogReplay.FAILED).build();

        KarateExecutor.ExecutionResult passed = run(PASS_FEATURE, protocol, null);
        assertTrue(replayed().isEmpty(), "a passing feature must not log anything by itself");

        run(FAIL_FEATURE, protocol, passed.logBuffer);

        List<String> events = replayed();
        assertEquals(1, events.size(), "only the failing feature should be replayed: " + events);
        String replay = events.get(0);
        assertTrue(replay.contains(">>> karate: " + FAIL_FEATURE + " [failed]"), replay);
        // the HTTP output the quiet Logback config swallowed
        assertTrue(replay.contains("POST http://localhost"), replay);
        assertTrue(replay.contains("ReplayKitty"), replay);
        assertFalse(replay.contains(PASS_FEATURE), "FAILED mode must not carry earlier features: " + replay);
    }

    @Test
    void allModeReplaysTheFeaturesThatAlreadyPassed() {
        KarateProtocol protocol = karateProtocol().logReplay("all").build();

        KarateExecutor.ExecutionResult passed = run(PASS_FEATURE, protocol, null);
        assertNotNull(passed.logBuffer);
        assertEquals(1, passed.logBuffer.getEntries().size(), "the passed feature's output should be held");
        assertTrue(replayed().isEmpty(), "holding is not logging");

        KarateExecutor.ExecutionResult failed = run(FAIL_FEATURE, protocol, passed.logBuffer);

        List<String> events = replayed();
        assertEquals(2, events.size(), "the passed feature and the failed one: " + events);
        assertTrue(events.get(0).contains(">>> karate: " + PASS_FEATURE + " [passed]"), events.get(0));
        assertTrue(events.get(1).contains(">>> karate: " + FAIL_FEATURE + " [failed]"), events.get(1));
        // the buffer is cleared once its contents have explained a failure, so the next iteration
        // does not re-log output that has already been seen
        assertNotNull(failed.logBuffer);
        assertTrue(failed.logBuffer.getEntries().isEmpty(), "buffer should be cleared after a replay");
    }

    @Test
    void replayedOutputIsPlainTextForALogFile() {
        KarateProtocol protocol = karateProtocol().logReplay("failed").build();
        run(FAIL_FEATURE, protocol, null);

        String replay = replayed().get(0);
        assertFalse(replay.indexOf((char) 27) >= 0, "ANSI colour must be stripped: " + replay);
        // the invisible sentinels that mark a body for the HTML report have no business in a log
        assertFalse(replay.indexOf((char) 0) >= 0, "report sentinels must be stripped: " + replay);
    }

    @Test
    void retentionIsBoundedAndSaysWhatItDropped() {
        KarateProtocol protocol = karateProtocol().logReplay("all").logReplayLimit(1).build();

        LogReplayer.Buffer buffer = run(PASS_FEATURE, protocol, null).logBuffer;
        buffer = run(PASS_FEATURE, protocol, buffer).logBuffer;
        assertEquals(1, buffer.getEntries().size(), "the limit caps what is held per virtual user");
        assertEquals(1, buffer.getDropped());

        run(FAIL_FEATURE, protocol, buffer);

        List<String> events = replayed();
        assertEquals(3, events.size(), "the drop notice, the retained feature, the failed one: " + events);
        // a cap that silently swallows context reads as "this is everything" when it is not
        assertTrue(events.get(0).contains("1 earlier karate feature log(s) dropped"), events.get(0));
        assertTrue(events.get(0).contains("logReplayLimit is 1"), events.get(0));
    }
}
