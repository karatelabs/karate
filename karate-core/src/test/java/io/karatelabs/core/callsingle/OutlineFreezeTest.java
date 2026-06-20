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
package io.karatelabs.core.callsingle;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduction for issue #2934: in a Scenario Outline whose Background combines
 * {@code karate.set(karate.callSingle(...))} with a {@code call read(...)}, every outline row
 * received row 1's Examples values (the per-row scope froze after the first row).
 */
class OutlineFreezeTest {

    @TempDir
    Path tempDir;

    @Test
    void outlineRowsKeepOwnValuesWithCallSingleAndCallReadInBackground() throws Exception {
        Files.writeString(tempDir.resolve("constants.feature"), """
                @ignore
                Feature: constants (callSingle target)
                Scenario:
                * def serviceName = 'TestService'
                * def baseUrl = 'https://example.com'
                """);
        Files.writeString(tempDir.resolve("bgNoHttpCall.feature"), """
                @ignore
                Feature: background call without http
                Scenario:
                * def items = [{ id: 'item1' }, { id: 'item2' }]
                """);
        Path main = tempDir.resolve("outline.feature");
        Files.writeString(main, """
                Feature: outline row values must not freeze

                  Background:
                    * karate.set( karate.callSingle('constants.feature') )
                    * def bgResult = call read('bgNoHttpCall.feature')

                  Scenario Outline: Row <rowNum> keeps its own phone
                    * def expectedPhones = ['1111', '2222', '3333', '4444']
                    * def phoneStr = phone + ''
                    * match phoneStr == expectedPhones[__num]

                    Examples:
                      | rowNum | phone |
                      | 1      | 1111  |
                      | 2      | 2222  |
                      | 3      | 3333  |
                      | 4      | 4444  |
                """);

        SuiteResult result = Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(
                result.isPassed(),
                "each outline row must keep its own Examples values, but a row saw row 1's values");
    }
}
