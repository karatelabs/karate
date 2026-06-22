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
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a feature failing inside a Gatling run surfaces the actual failure
 * detail to the logs. The single-feature Gatling run disables the HTML report and the
 * console summary, so {@link KarateExecutor}'s error log is the only place the reason
 * appears — logging just the path (the old behaviour) made failures undiagnosable.
 */
class KarateExecutorTest {

    @SuppressWarnings("unchecked")
    private static final scala.collection.immutable.List<String> NO_GROUPS =
            (scala.collection.immutable.List<String>) (Object) scala.collection.immutable.Nil$.MODULE$;

    @Test
    void failedFeatureLogsTheFailureMessageNotJustThePath() {
        CatsMockServer.start();

        Logger execLogger = (Logger) LoggerFactory.getLogger(KarateExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        execLogger.addAppender(appender);
        try {
            // silent=true: no stats reporting, so the GatlingStatsReporter / groups are unused
            KarateExecutor executor = new KarateExecutor(
                    "classpath:features/cats-create-fail.feature", null, null, true);

            Map<String, Object> gatlingVars = new HashMap<>();
            gatlingVars.put("name", "TestKitty");

            KarateExecutor.ExecutionResult result =
                    executor.execute(gatlingVars, new HashMap<>(), null, "test", NO_GROUPS);

            assertFalse(result.success, "feature was expected to fail");

            List<String> errors = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertFalse(errors.isEmpty(), "expected an ERROR log for the failed feature");

            String logged = String.join("\n", errors);
            assertTrue(logged.contains("cats-create-fail.feature"),
                    "log should name the feature: " + logged);
            // The actual assertion-failure detail must be present, not just the path
            assertTrue(logged.contains("WRONG_NAME_THAT_WILL_NEVER_MATCH"),
                    "log should carry the match-failure detail: " + logged);
        } finally {
            execLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
