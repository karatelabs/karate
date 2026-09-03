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
import io.karatelabs.output.LogContext;
import io.karatelabs.output.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.karatelabs.gatling.KarateDsl.karateProtocol;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What a replay actually carries. {@link LogReplayerTest} pins down when a replay happens; this pins
 * down that it is the whole of the user's output and not just the HTTP traffic — {@code print},
 * {@code karate.log()} and {@code karate.logger.info()} all enter the report buffer at INFO, so all
 * three ride the same event as the request/response blocks.
 */
class LogReplayerOutputTest {

    private static final String FEATURE = "classpath:features/replay-markers.feature";

    /** The documented "prints only when something fails" recipe, logger for logger. */
    private static final String[] RECIPE_LOGGERS = {
            "karate", "karate.scenario", "karate.http", "karate.runtime", "io.karatelabs"
    };

    /** Everything LogContext.setRuntimeLogLevel touches — restored after every test, see below. */
    private static final String[] RUNTIME_LOGGERS = {
            "karate", "karate.runtime", "karate.http", "karate.mock", "karate.server",
            "karate.scenario", "karate.console", "io.karatelabs"
    };

    @SuppressWarnings("unchecked")
    private static final scala.collection.immutable.List<String> NO_GROUPS =
            (scala.collection.immutable.List<String>) (Object) scala.collection.immutable.Nil$.MODULE$;

    private Logger replayLogger;
    private ListAppender<ILoggingEvent> appender;
    private LogLevel reportLevel;
    private Map<String, Level> runtimeLevels;

    @BeforeAll
    static void beforeAll() {
        CatsMockServer.start();
    }

    @BeforeEach
    void beforeEach() {
        // A sibling test in this module drives HttpClient.invoke() directly, off any scenario, and
        // leaves the request block on this thread's LogContext. ScenarioRuntime.call() adopts
        // whatever is already there as config-time output — verbatim, past the report threshold —
        // so it would ride into the next replay and make these assertions order-dependent.
        LogContext.clear();
        // karate-config.js runs in the ScenarioRuntime constructor, before call() takes the logging
        // snapshot, so a `console` or `report` it sets is process-global and outlives the scenario.
        reportLevel = LogContext.getLogLevel();
        runtimeLevels = new LinkedHashMap<>();
        for (String name : RUNTIME_LOGGERS) {
            runtimeLevels.put(name, ((Logger) LoggerFactory.getLogger(name)).getLevel());
        }
        replayLogger = (Logger) LoggerFactory.getLogger(LogReplayer.class);
        appender = new ListAppender<>();
        appender.start();
        replayLogger.addAppender(appender);
    }

    @AfterEach
    void afterEach() {
        replayLogger.detachAppender(appender);
        appender.stop();
        runtimeLevels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
        LogContext.setLogLevel(reportLevel);
    }

    private List<String> replayed() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Run one feature the way the Gatling action does. silent=true: no stats reporter needed. */
    private void run(KarateProtocol protocol) {
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "ReplayKitty");
        new KarateExecutor(FEATURE, null, protocol, true)
                .execute(gatlingVars, new HashMap<>(), null, null, "test", NO_GROUPS);
    }

    private static void assertCarriesEverything(String replay) {
        assertTrue(replay.contains(">>> karate: " + FEATURE + " [failed]"), replay);
        assertTrue(replay.contains("--- scenario [1:"), "each scenario is headed by its [section:line] ref: " + replay);
        assertTrue(replay.contains("] print, karate.log and karate.logger, then fail after a real request [failed]"),
                "the scenario header names the scenario and its outcome: " + replay);
        assertTrue(replay.indexOf("--- scenario") < replay.indexOf("PRINTED-MARKER"),
                "the scenario header precedes its output: " + replay);
        assertTrue(replay.contains("PRINTED-MARKER"), "print must be replayed: " + replay);
        assertTrue(replay.contains("KLOG-MARKER"), "karate.log() must be replayed: " + replay);
        assertTrue(replay.contains("LOGGER-MARKER"), "karate.logger.info() must be replayed: " + replay);
        assertTrue(replay.contains("POST http://localhost"), "the request block must be replayed: " + replay);
        assertTrue(replay.contains("ReplayKitty"), "the response body must be replayed: " + replay);
    }

    @Test
    void replayCarriesPrintAndKarateLogAlongsideTheHttpBlocks() {
        run(karateProtocol().logReplay(KarateLogReplay.FAILED).build());

        List<String> events = replayed();
        assertEquals(1, events.size(), "expected one replay event: " + events);
        assertCarriesEverything(events.get(0));
    }

    /**
     * The configuration the whole feature exists for: the documented recipe, the {@code karate.*}
     * categories pinned to ERROR so a load run stays quiet, and the user's output visible only in
     * the replay of whatever failed. The children are pinned individually, as the recipe says,
     * because a Logback logger with its own level ignores its parent's. The replay logger is a
     * child of {@code io.karatelabs} and emits at ERROR, exactly the level that pin lets through.
     */
    @Test
    void printIsSilencedLiveButStillReachesTheReplay() {
        Logger scenarioLogger = (Logger) LoggerFactory.getLogger("karate.scenario");
        ListAppender<ILoggingEvent> live = new ListAppender<>();
        live.start();
        scenarioLogger.addAppender(live);
        for (String name : RECIPE_LOGGERS) {
            ((Logger) LoggerFactory.getLogger(name)).setLevel(Level.ERROR);
        }
        try {
            run(karateProtocol().logReplay(KarateLogReplay.FAILED).build());

            List<String> livePrints = live.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(livePrints.stream().noneMatch(s -> s.contains("PRINTED-MARKER")),
                    "print must not reach the console at ERROR: " + livePrints);

            List<String> events = replayed();
            assertEquals(1, events.size(), "expected one replay event: " + events);
            assertCarriesEverything(events.get(0));
        } finally {
            scenarioLogger.detachAppender(live);
            live.stop();
        }
    }

    /**
     * {@code report} is not something a user has to set: it defaults to DEBUG, which is below the
     * INFO that print and the HTTP blocks enter the buffer at. A config that touches only
     * {@code console} must not change what the replay carries.
     */
    @Test
    void replayNeedsNoReportLevelInTheConfig() {
        KarateProtocolBuilder builder = karateProtocol().logReplay(KarateLogReplay.FAILED);
        builder.runner.karateEnv("replayconsole");

        run(builder.build());

        List<String> events = replayed();
        assertEquals(1, events.size(), "expected one replay event: " + events);
        assertCarriesEverything(events.get(0));
    }

    /** The one config that does empty the buffer — and the case the hint text is written for. */
    @Test
    void aReportLevelAboveInfoIsWhatEmptiesTheReplay() {
        KarateProtocolBuilder builder = karateProtocol().logReplay(KarateLogReplay.FAILED);
        builder.runner.karateEnv("replayreportwarn");

        run(builder.build());

        List<String> events = replayed();
        assertEquals(1, events.size(), "expected the explanation, got: " + events);
        assertTrue(events.get(0).contains("captured no output to replay"), events.get(0));
        assertTrue(events.get(0).contains("report: 'info'"), events.get(0));
    }
}
