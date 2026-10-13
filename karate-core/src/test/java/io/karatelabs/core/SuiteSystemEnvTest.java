package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code Runner.Builder.systemEnv}: an environment overlay snapshotted into one Suite, read by {@code karate.sysenv}
 * (karate-config and steps) and {@code boot.sysenv} before the process environment.
 */
class SuiteSystemEnvTest {

    static final String NAME = "__KARATE_SUITE_ENV_TEST__";

    @TempDir
    Path dir;

    @Test
    void karateConfigReadsTheOverlay() throws Exception {
        Files.writeString(dir.resolve("karate-config.js"),
                "function fn() { return { v: karate.sysenv('" + NAME + "'), path: karate.sysenv('PATH') }; }");
        Map<String, Object> overlaid = ConfigLoader.configOnly(dir, null, null, Map.of(NAME, "overlay"));
        assertEquals("overlay", overlaid.get("v"));
        assertEquals(System.getenv("PATH"), overlaid.get("path"), "an unlisted name reads the process");
        assertNull(ConfigLoader.configOnly(dir, null).get("v"), "no overlay: the process environment alone");
    }

    @Test
    void bootReadsTheOverlay() {
        Suite suite = Runner.builder().workingDir(dir).systemEnv(Map.of(NAME, "boot")).buildSuite();
        assertEquals("boot", new BootBinding(suite, dir, null, null).sysenv(NAME));
        assertNull(new BootBinding(null, dir, null, null).sysenv(NAME));
    }

    @Test
    void suitesSideBySideSeeOnlyTheirOwnOverlay() throws Exception {
        Files.writeString(dir.resolve("env.feature"), """
                Feature: overlay

                Scenario: own value
                  * def t = java.lang.System.currentTimeMillis()
                  * eval while (java.lang.System.currentTimeMillis() - t < 300) {}
                  * match karate.sysenv('%s') == karate.sysprop('expected')
                """.formatted(NAME));
        CompletableFuture<SuiteResult> a = CompletableFuture.supplyAsync(() -> run("a"));
        CompletableFuture<SuiteResult> b = CompletableFuture.supplyAsync(() -> run("b"));
        for (SuiteResult r : new SuiteResult[]{a.get(), b.get()}) {
            assertEquals(1, r.getScenarioPassedCount(), String.valueOf(r.getErrors()));
        }
    }

    private SuiteResult run(String value) {
        return Runner.path(dir.resolve("env.feature").toString())
                .workingDir(dir)
                .systemEnv(Map.of(NAME, value))
                .systemProperty("expected", value)
                .outputDir(dir.resolve("target-" + value).toString())
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);
    }
}
