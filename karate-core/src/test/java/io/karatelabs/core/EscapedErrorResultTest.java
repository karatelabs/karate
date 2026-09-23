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

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@link Throwable} that is not an {@code Exception} (a {@code StackOverflowError}, a custom
 * {@code Error}) escapes the step executor's catch; the scenario must still read failed, carry the
 * error, and fire SCENARIO_EXIT with its stable identity.
 */
class EscapedErrorResultTest {

    static class BoomError extends Error {
        BoomError(String message) {
            super(message);
        }
    }

    @Test
    void escapedErrorFailsScenarioAndStillFiresExit() {
        Feature feature = Feature.read(Resource.text("""
                Feature: escaped error
                Scenario: boom
                * def __id = 'escaped-stable-id'
                * print 'boom'
                * def after = 1
                """));
        List<Map<String, Object>> exits = new ArrayList<>();
        RunListener listener = event -> {
            if (event instanceof StepRunEvent sre && event.getType() == RunEventType.STEP_ENTER
                    && sre.step() != null && "'boom'".equals(sre.step().getText())) {
                throw new BoomError("custom error escaped the step");
            }
            if (event instanceof ScenarioRunEvent sre && event.getType() == RunEventType.SCENARIO_EXIT) {
                exits.add(sre.toJson());
            }
            return true;
        };
        SuiteResult suiteResult = Runner.builder()
                .features(feature)
                .skipTagFiltering(true)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .listener(listener)
                .parallel(1);

        ScenarioResult result = suiteResult.getFeatureResults().getFirst().getScenarioResults().getFirst();
        assertTrue(result.isFailed());
        assertInstanceOf(BoomError.class, result.getError());
        assertTrue(result.getFailureMessage().contains("custom error escaped the step"));
        assertEquals("escaped-stable-id", result.getStableId());

        assertEquals(1, exits.size());
        Map<String, Object> exit = exits.getFirst();
        assertEquals("escaped-stable-id", exit.get("slug"));
        assertEquals(false, exit.get("passed"));
    }

}
