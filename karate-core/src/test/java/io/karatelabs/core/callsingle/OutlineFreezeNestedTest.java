package io.karatelabs.core.callsingle;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repro for #3080: freeze returns when the callSingle target does a nested call/callonce read. */
class OutlineFreezeNestedTest {

    @TempDir
    Path tempDir;

    private SuiteResult run(String sharedBody) throws Exception {
        Files.writeString(tempDir.resolve("inner.feature"), """
                @ignore
                Feature: inner
                Scenario:
                * def innerValue = 'inner'
                """);
        Files.writeString(tempDir.resolve("shared.feature"), sharedBody);
        Path main = tempDir.resolve("outline.feature");
        Files.writeString(main, """
                Feature: outline row values must not freeze

                Background:
                  * karate.set( karate.callSingle('shared.feature') )

                Scenario Outline: Row <rowNum> keeps its own colValue
                  * def expectedList = ['row1', 'row2', 'row3', 'row4']
                  * match colValue == expectedList[__num]

                  Examples:
                  | colValue! | rowNum! |
                  | 'row1'    | 1       |
                  | 'row2'    | 2       |
                  | 'row3'    | 3       |
                  | 'row4'    | 4       |
                """);
        return Runner.builder()
                .path(main.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);
    }

    @Test
    void nestedCallOnceInTarget() throws Exception {
        SuiteResult r = run("""
                @ignore
                Feature: shared with nested callonce
                Scenario:
                * callonce read('inner.feature')
                * def serviceName = 'TestService'
                """);
        assertTrue(r.isPassed(), "row freeze with nested callonce target");
    }

    @Test
    void nestedCallInTarget() throws Exception {
        SuiteResult r = run("""
                @ignore
                Feature: shared with nested call
                Scenario:
                * call read('inner.feature')
                * def serviceName = 'TestService'
                """);
        assertTrue(r.isPassed(), "row freeze with nested call target");
    }
}
