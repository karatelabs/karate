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
package io.karatelabs.driver.temp;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual verification of {@code configure driver = { ..., stop: false }}.
 * Disabled by default — requires a local chromedriver on PATH and a visible Chrome.
 *
 * To run:
 *   mvn -pl karate-core test -Dtest=TempStopFalseTest
 *
 * What to watch for in the console:
 *   - WARN at init  : "configure driver = { stop: false } — bypassing driver pool ..."
 *   - WARN at exit  : "driver.stop=false — leaving browser running ..."
 *   - Chrome window opens and stays visible during karate.stop(9000) pause
 *   - After you `curl http://localhost:9000`, the scenario ends but Chrome
 *     does NOT close (until the JVM itself exits).
 */
@Disabled("Manual test — requires local chromedriver + visible Chrome")
public class TempStopFalseTest {

    @Test
    void chromedriverStopFalse() {
        SuiteResult result = Runner.path("classpath:io/karatelabs/driver/temp/stop-false.feature")
                .outputDir(Path.of("target", "temp-stop-false-reports"))
                .outputHtmlReport(true)
                .outputConsoleSummary(true)
                .parallel(1);
        assertTrue(result.isPassed(), "stop-false feature should pass");
    }

}
