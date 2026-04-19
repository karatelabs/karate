/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.core;

import io.karatelabs.output.Console;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class KarateOptionsHandlerTest {

    @TempDir
    Path tempDir;

    private final Map<String, String> fakeSysprops = new HashMap<>();
    private final Map<String, String> fakeEnv = new HashMap<>();
    private final Function<String, String> savedSysprop = KarateOptionsHandler.sysPropReader;
    private final Function<String, String> savedEnv = KarateOptionsHandler.envVarReader;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
        KarateOptionsHandler.sysPropReader = fakeSysprops::get;
        KarateOptionsHandler.envVarReader = fakeEnv::get;
    }

    @AfterEach
    void tearDown() {
        KarateOptionsHandler.sysPropReader = savedSysprop;
        KarateOptionsHandler.envVarReader = savedEnv;
    }

    // ========== Direct parseAndApplyOptions tests ==========

    @Test
    void testOverrideTags() {
        Runner.Builder builder = Runner.builder().tags("@a");
        KarateOptionsHandler.parseAndApplyOptions(builder, "--tags @b", 1);
        assertEquals(java.util.List.of("@b"), builder.getTags());
    }

    @Test
    void testOverridePathsReplaces() {
        Runner.Builder builder = Runner.builder().path("orig.feature");
        KarateOptionsHandler.parseAndApplyOptions(builder, "new.feature", 1);
        assertEquals(java.util.List.of("new.feature"), builder.getPaths());
    }

    @Test
    void testOverridePathsViaDashP() {
        Runner.Builder builder = Runner.builder().path("orig.feature");
        KarateOptionsHandler.parseAndApplyOptions(builder, "-P a.feature -P b.feature", 1);
        assertEquals(java.util.List.of("a.feature", "b.feature"), builder.getPaths());
    }

    @Test
    void testOverrideEnv() {
        Runner.Builder builder = Runner.builder().karateEnv("dev");
        KarateOptionsHandler.parseAndApplyOptions(builder, "--env qa", 1);
        assertEquals("qa", builder.getEnv());
    }

    @Test
    void testOverrideThreads() {
        Runner.Builder builder = Runner.builder();
        int effective = KarateOptionsHandler.parseAndApplyOptions(builder, "--threads 5", 1);
        assertEquals(5, effective);
    }

    @Test
    void testThreadsParamPreservedWhenNotInOptions() {
        Runner.Builder builder = Runner.builder();
        int effective = KarateOptionsHandler.parseAndApplyOptions(builder, "--tags @smoke", 3);
        assertEquals(3, effective);
    }

    @Test
    void testPartialOverridePreservesOtherBuilderValues() {
        Runner.Builder builder = Runner.builder()
                .path("preserve.feature")
                .karateEnv("dev");
        KarateOptionsHandler.parseAndApplyOptions(builder, "--tags @smoke", 1);
        assertEquals(java.util.List.of("preserve.feature"), builder.getPaths());
        assertEquals("dev", builder.getEnv());
        assertEquals(java.util.List.of("@smoke"), builder.getTags());
    }

    @Test
    void testDryRunFromOptions() {
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.parseAndApplyOptions(builder, "-D", 1);
        assertTrue(builder.isDryRun());
    }

    @Test
    void testInvalidOptionsDoesNotCrash() {
        Runner.Builder builder = Runner.builder().tags("@preserve");
        int effective = KarateOptionsHandler.parseAndApplyOptions(builder, "--bogus-flag-nonexistent", 2);
        assertEquals(2, effective);
        // Builder values preserved — invalid string ignored
        assertEquals(java.util.List.of("@preserve"), builder.getTags());
    }

    @Test
    void testFormatsApplied() {
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.parseAndApplyOptions(builder, "-f ~html,cucumber:json", 1);
        assertFalse(builder.isOutputHtmlReport());
        assertTrue(builder.isOutputCucumberJson());
        assertFalse(builder.isOutputJunitXml());
    }

    @Test
    void testOutputDirOverride() {
        Runner.Builder builder = Runner.builder().outputDir("original");
        KarateOptionsHandler.parseAndApplyOptions(builder, "-o overridden", 1);
        assertEquals(Path.of("overridden"), builder.getOutputDir());
    }

    // ========== apply() precedence tests (uses fake sysprop/env readers) ==========

    @Test
    void testNoSysprop_BuilderPreserved() {
        Runner.Builder builder = Runner.builder().karateEnv("dev").tags("@a");
        int effective = KarateOptionsHandler.apply(builder, 2);
        assertEquals(2, effective);
        assertEquals("dev", builder.getEnv());
        assertEquals(java.util.List.of("@a"), builder.getTags());
    }

    @Test
    void testIndividualKarateEnvSysprop() {
        fakeSysprops.put("karate.env", "qa");
        Runner.Builder builder = Runner.builder().karateEnv("dev");
        KarateOptionsHandler.apply(builder, 1);
        assertEquals("qa", builder.getEnv());
    }

    @Test
    void testKarateOptionsEnvWinsOverIndividual() {
        fakeSysprops.put("karate.env", "from-individual");
        fakeSysprops.put("karate.options", "--env from-options");
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.apply(builder, 1);
        assertEquals("from-options", builder.getEnv());
    }

    @Test
    void testEnvVarFallbackWhenSyspropAbsent() {
        fakeEnv.put("KARATE_OPTIONS", "--tags @smoke");
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.apply(builder, 1);
        assertEquals(java.util.List.of("@smoke"), builder.getTags());
    }

    @Test
    void testSyspropWinsOverEnvVar() {
        fakeSysprops.put("karate.options", "--tags @from-sysprop");
        fakeEnv.put("KARATE_OPTIONS", "--tags @from-env");
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.apply(builder, 1);
        assertEquals(java.util.List.of("@from-sysprop"), builder.getTags());
    }

    @Test
    void testKarateEnvEnvVarFallback() {
        fakeEnv.put("KARATE_ENV", "qa-from-env");
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.apply(builder, 1);
        assertEquals("qa-from-env", builder.getEnv());
    }

    @Test
    void testKarateConfigDirSysprop() {
        fakeSysprops.put("karate.config.dir", "custom/config/dir");
        Runner.Builder builder = Runner.builder();
        KarateOptionsHandler.apply(builder, 1);
        assertEquals("custom/config/dir", builder.getConfigDir());
    }

    @Test
    void testBlankSyspropIgnored() {
        fakeSysprops.put("karate.options", "   ");
        Runner.Builder builder = Runner.builder().tags("@a");
        KarateOptionsHandler.apply(builder, 1);
        assertEquals(java.util.List.of("@a"), builder.getTags());
    }

    // ========== End-to-end via parallel() — confirms hook fires inside parallel() ==========

    @Test
    void testParallelHonorsKarateOptionsFromSysprop() throws Exception {
        Path feature = tempDir.resolve("simple.feature");
        Files.writeString(feature, """
                Feature: simple

                @smoke
                Scenario: runs under smoke tag
                * def a = 1
                * match a == 1

                @skip
                Scenario: does not run under skip tag
                * def b = 2
                * match b == 99
                """);
        fakeSysprops.put("karate.options", "--tags @smoke");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        // Only @smoke scenario ran; @skip scenario filtered out
        assertEquals(1, result.getScenarioPassedCount());
        assertEquals(0, result.getScenarioFailedCount());
    }

    @Test
    void testParallelHonorsIndividualKarateEnvSysprop() throws Exception {
        Path feature = tempDir.resolve("env.feature");
        Files.writeString(feature, """
                Feature: env check

                Scenario: reads karate.env
                * match karate.env == 'qa'
                """);
        fakeSysprops.put("karate.env", "qa");
        SuiteResult result = Runner.path(feature.toString())
                .karateEnv("dev") // Builder says 'dev', sysprop overrides to 'qa'
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed(), "scenario should see karate.env == 'qa'");
    }
}
