/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Suite#getRoot()} — THE root, and the constructor ordering that keeps it honest.
 * Every drift in path resolution here has historically been silent, so each branch of the root
 * computation and each thing that must NOT move it gets a pin.
 */
class SuiteRootTest {

    @TempDir
    Path project;

    private void write(String relative, String content) throws Exception {
        Path target = project.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    @Test
    void anExplicitConfigDirIsTheRoot() {
        Suite suite = Runner.builder().configDir(project.toString()).buildSuite();
        assertEquals(project, suite.getRoot());
    }

    @Test
    void aConfigDirNamingAJsFileContributesItsParent() throws Exception {
        write("cfg/karate-config.js", "function fn() { return {}; }\n");
        Suite suite = Runner.builder()
                .configDir(project.resolve("cfg/karate-config.js").toString())
                .buildSuite();
        assertEquals(project.resolve("cfg"), suite.getRoot());
    }

    @Test
    void anExplicitWorkingDirIsTheRootWhenNoConfigDirWasSet(@TempDir Path other) {
        // no configDir → the classloader probe runs; karate-core's own test classpath has no
        // karate-config.js at its root, so the explicit working dir is the answer
        Suite suite = Runner.builder().workingDir(other).buildSuite();
        assertEquals(other, suite.getRoot());
        assertEquals(other, suite.getWorkingDir());
    }

    @Test
    void theRootDoesNotDriftToWhereTheConfigFileWasFound() throws Exception {
        // the config lives ONLY under the mapped dir — a served Maven project's shape. The root
        // must stay the project dir, or every reference would silently re-anchor one level in.
        write("karate-boot.js", "boot.classpath('res');\n");
        write("res/karate-config.js", "function fn() { return { seeded: 'yes' }; }\n");
        Suite suite = Runner.builder().configDir(project.toString()).buildSuite();
        assertNotNull(suite.configResource, "config under the mapped dir must still be discovered");
        assertEquals(project, suite.getRoot(), "the root must NOT follow the config file");
        assertEquals(project.resolve("res"), suite.getConfigDir());
    }

    @Test
    void bootEvaluatesBeforeConfigAndFeaturesAreResolved() throws Exception {
        // the boot file WRITES the config + feature the run then needs: it can only work if boot
        // runs first. This is the ordering, asserted by construction rather than by inspection.
        write("karate-boot.js", """
            boot.classpath('res');
            boot.log('booted');
            """);
        write("res/karate-config.js", "function fn() { return { fromConfig: 'ok' }; }\n");
        write("a.feature", """
            Feature: ordering

            Scenario: config from the mapped dir is visible
            * match fromConfig == 'ok'
            """);
        SuiteResult result = Runner.path(project.resolve("a.feature").toString())
                .configDir(project.toString())
                .outputDir(project.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());
    }

    @Test
    void anExtSeesTheFinalRootInOnBoot() throws Exception {
        io.karatelabs.ext.rootcapture.RootcaptureExt.SEEN.remove();
        write("karate-boot.js", "boot.ext('rootcapture');\n");
        Suite suite = Runner.builder().configDir(project.toString()).buildSuite();
        assertEquals(project, io.karatelabs.ext.rootcapture.RootcaptureExt.SEEN.get(),
                "onBoot must see THE root, already final");
        assertEquals(project, suite.getRoot());
    }

    @Test
    void configOnlyAnchorsOnItsWorkingDirArg() throws Exception {
        write("karate-config.js", "function fn() { return { a: 1 }; }\n");
        Map<String, Object> config = ConfigLoader.configOnly(project, null);
        assertEquals(1, ((Number) config.get("a")).intValue());
    }

    @Test
    void configOnlyThrowsWhenAPresentConfigFails() throws Exception {
        // documented fail-loud contract: configOnly never goes through ScenarioRuntime.call(),
        // so a config failure captured during init must still be surfaced to this caller
        write("karate-config.js", "function fn() { throw 'config is broken'; }\n");
        RuntimeException e = assertThrows(RuntimeException.class, () -> ConfigLoader.configOnly(project, null));
        assertTrue(e.getMessage().contains("Config evaluation failed"), e.getMessage());
    }

    @Test
    void configOnlyContributesNothingForAHelpersOnlyConfig() throws Exception {
        // no fn() to invoke: the file only declares helpers, so it returns nothing. The eval result is
        // a function object — which IS a Map — and must not be mistaken for a config variable map.
        write("karate-config.js", "function helperOne() { return 1; }\nfunction helperTwo() { return 2; }\n");
        assertTrue(ConfigLoader.configOnly(project, null).isEmpty());
    }

    @Test
    void aMissingConfigWarnsAndNamesTheDirsItProbed() {
        // A standalone (CLI) run has no JVM classpath to fall back on. When the config is not under
        // the working dir the whole config phase contributes nothing, and the run only fails later
        // as a bare ReferenceError in the first step that reads a config variable. The miss has to
        // be loud, and it has to name where we looked — that is what points at --workingdir.
        ch.qos.logback.classic.Logger runtime =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("karate.runtime");
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> capture =
                new ch.qos.logback.core.AppenderBase<>() {
                    @Override
                    protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                        if (event.getLevel() == ch.qos.logback.classic.Level.WARN) {
                            warnings.add(event.getFormattedMessage());
                        }
                    }
                };
        capture.setContext((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory());
        capture.start();
        runtime.addAppender(capture);
        try {
            // the temp dir has no karate-config.js
            Runner.builder().configDir(project.toString()).buildSuite();
        } finally {
            runtime.detachAppender(capture);
            capture.stop();
        }
        String warning = warnings.stream()
                .filter(w -> w.contains("karate-config.js"))
                .findFirst()
                .orElse(null);
        assertNotNull(warning, "a missing karate-config.js must warn, not whisper at trace: " + warnings);
        assertTrue(warning.contains("not found"), warning);
        assertTrue(warning.contains(project.toString()), "the warning must name the dir probed: " + warning);
    }

    @Test
    void bootOnlyReturnsTheBindingTheSuiteAlreadyEvaluated() throws Exception {
        write("karate-boot.js", "boot.classpath('res');\n");
        BootBinding boot = BootLoader.bootOnly(project, null);
        assertNotNull(boot);
        assertEquals("res", boot.getClasspathDir());
    }

    @Test
    void aSingleClasspathFeatureIsRootedOnTheProjectNotTheCwd() {
        // the asymmetry that used to exist against the directory-scan branch: one classpath:
        // feature ran rooted at the process CWD, so a leading-"/" ref inside it left the project
        Suite suite = Runner.builder()
                .path("classpath:feature/http-simple.feature")
                .workingDir(project)
                .configDir(project.toString())
                .buildSuite();
        assertEquals(1, suite.features.size());
        assertEquals(project, suite.features.get(0).getResource().getRoot());
    }

    @Test
    void embeddedCodeCarriesItsHostResourceAnchors() {
        Resource host = Resource.from(project.resolve("karate-config.js"), project, project.resolve("res"));
        Resource embedded = Resource.embedded("(function(){})", host, 0);
        assertEquals(project, embedded.getRoot(),
                "an embedded (config-eval) resource must not fall back to the system temp dir");
        assertEquals(project.resolve("res"), embedded.getClasspathRoot());
        // …and so a reference made from inside it lands in the project
        assertEquals(project.resolve("x.js"), embedded.resolve("/x.js").getPath());
    }

    /** Records the root {@code onBoot} was handed. Resolved by the {@code boot.ext} name convention. */
    public static class RootCapturingExt implements Ext {

        static final ThreadLocal<Path> SEEN = new ThreadLocal<>();

        @Override
        public void onBoot(Suite suite) {
            SEEN.set(suite.getRoot());
        }
    }
}
