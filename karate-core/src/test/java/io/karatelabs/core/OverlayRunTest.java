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

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.output.Console;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A feature run from in-memory bytes under its real path ({@link Runner.Builder#overlay}), and the
 * SHA-256 of the executed source carried by every feature result.
 */
class OverlayRunTest {

    static final String DISK = """
            Feature: checks

            Scenario: from disk
            * def who = 'disk'
            """;

    // passes only when it runs: the sibling call, the config value and the overlay's own text
    static final String OVERLAY = """
            Feature: checks

            Scenario: from overlay
            * def helper = call read('helper.feature')
            * match helper.greeting == 'hello'
            * match configValue == 'from-config'
            * def who = 'overlay'
            """;

    @TempDir
    Path tempDir;

    Path featureFile;

    @BeforeEach
    void setup() throws Exception {
        Console.setColorsEnabled(false);
        Path dir = Files.createDirectories(tempDir.resolve("checks"));
        Files.writeString(tempDir.resolve("karate-config.js"), "function fn() { return { configValue: 'from-config' } }");
        Files.writeString(dir.resolve("helper.feature"), """
                Feature: helper

                Scenario: greet
                * def greeting = 'hello'
                """);
        featureFile = dir.resolve("orders.feature");
        Files.writeString(featureFile, DISK);
        Files.setLastModifiedTime(featureFile, FileTime.fromMillis(1_000_000_000_000L));
    }

    Runner.Builder builder(String reportDir) {
        return Runner.path(featureFile.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve(reportDir))
                .outputHtmlReport(false)
                .outputConsoleSummary(false);
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static FeatureResult top(SuiteResult result) {
        assertEquals(1, result.getFeatureResults().size());
        return result.getFeatureResults().get(0);
    }

    static String scenarioName(FeatureResult fr) {
        return fr.getScenarioResults().get(0).getScenario().getName();
    }

    @Test
    void overlayRunsTheInMemoryTextUnderTheRealPathAndLeavesTheDiskFileAlone() throws Exception {
        byte[] overlay = OVERLAY.getBytes(StandardCharsets.UTF_8);
        byte[] diskBefore = Files.readAllBytes(featureFile);
        FileTime mtimeBefore = Files.getLastModifiedTime(featureFile);

        SuiteResult result = builder("reports").overlay(featureFile, overlay).outputJsonLines(true).parallel(1);

        FeatureResult fr = top(result);
        assertTrue(result.isPassed(), "the sibling call resolves on disk and karate-config applies");
        assertEquals("from overlay", scenarioName(fr));
        Map<String, Object> json = fr.toJson();
        assertEquals("checks/orders.feature", json.get("relativePath"));
        assertEquals("checks_orders_feature", json.get("id"));
        assertEquals(featureFile.toUri(), fr.getFeature().getResource().getUri());
        assertEquals(sha256(overlay), json.get("sourceHash"));

        // the JSONL stream carries the same hash on enter and exit
        List<String> lines = Files.readAllLines(tempDir.resolve("reports/karate-json/karate-events.jsonl"));
        List<String> hashes = new ArrayList<>();
        for (String line : lines) {
            Map<String, Object> event = Json.of(line).asMap();
            if (String.valueOf(event.get("type")).startsWith("FEATURE_")) {
                Map<?, ?> data = (Map<?, ?>) event.get("data");
                if ("checks/orders.feature".equals(data.get("path")) || "checks/orders.feature".equals(data.get("relativePath"))) {
                    hashes.add((String) data.get("sourceHash"));
                }
            }
        }
        assertEquals(List.of(sha256(overlay), sha256(overlay)), hashes);

        assertArrayEquals(diskBefore, Files.readAllBytes(featureFile));
        assertEquals(mtimeBefore, Files.getLastModifiedTime(featureFile));
    }

    @Test
    void normalRunHashesTheDiskBytes() throws Exception {
        SuiteResult result = builder("reports").parallel(1);
        FeatureResult fr = top(result);
        assertEquals("from disk", scenarioName(fr));
        assertEquals(sha256(Files.readAllBytes(featureFile)), fr.toJson().get("sourceHash"));
    }

    // Windows refuses a "classpath:..." string as a filesystem path (the ':' is illegal there), so
    // the overlay lookup must not turn every path into a Path. A NUL is illegal on every OS, which
    // lets this run the same check here.
    @Test
    void aPathTheFileSystemCannotParseIsNotAnOverlayLookup() throws Exception {
        String unparseable = "classpath:checks\u0000missing";
        SuiteResult plain = Runner.path(featureFile.toString(), unparseable)
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);
        assertEquals("from disk", scenarioName(top(plain)));

        Path created = featureFile.resolveSibling("created.feature");
        SuiteResult withOverlay = Runner.path(created.toString(), unparseable)
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports2"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .overlay(created, OVERLAY.getBytes(StandardCharsets.UTF_8))
                .parallel(1);
        assertEquals("from overlay", scenarioName(top(withOverlay)));
    }

    @Test
    void overlayOfAFileNotYetOnDisk() throws Exception {
        Path created = featureFile.resolveSibling("created.feature");
        SuiteResult result = Runner.path(created.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .overlay(created, OVERLAY.getBytes(StandardCharsets.UTF_8))
                .parallel(1);
        assertTrue(result.isPassed());
        assertEquals("checks/created.feature", top(result).toJson().get("relativePath"));
        assertFalse(Files.exists(created));
    }

    @Test
    void prebuiltOverlayFeatureIsReAnchoredWithoutReparsingTheDisk() throws Exception {
        // built CWD-rooted, so the Suite re-anchors it at the working dir: it must keep its bytes
        Feature feature = Feature.read(Resource.overlay(featureFile, OVERLAY.getBytes(StandardCharsets.UTF_8), null, null));
        SuiteResult result = Runner.features(feature)
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());
        assertEquals("from overlay", scenarioName(top(result)));
        assertEquals(tempDir.toAbsolutePath().normalize(), top(result).getFeature().getResource().getRoot());
    }

    @Test
    void concurrentNormalRunOfTheSamePathSeesTheDisk() throws Exception {
        CountDownLatch overlayRunning = new CountDownLatch(1);
        CountDownLatch normalDone = new CountDownLatch(1);
        // hold the overlay run inside its scenario until the normal run has finished
        RunListener hold = event -> {
            if (event.getType() == RunEventType.SCENARIO_ENTER) {
                overlayRunning.countDown();
                try {
                    normalDone.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        };
        CompletableFuture<SuiteResult> overlayRun = CompletableFuture.supplyAsync(() ->
                builder("reports-overlay").overlay(featureFile, OVERLAY.getBytes(StandardCharsets.UTF_8))
                        .listener(hold).parallel(1));
        assertTrue(overlayRunning.await(30, TimeUnit.SECONDS));
        SuiteResult normal;
        try {
            normal = builder("reports-normal").parallel(1);
        } finally {
            normalDone.countDown();
        }
        assertEquals("from disk", scenarioName(top(normal)));
        assertEquals(sha256(DISK.getBytes(StandardCharsets.UTF_8)), top(normal).toJson().get("sourceHash"));
        SuiteResult overlaid = overlayRun.get(30, TimeUnit.SECONDS);
        assertTrue(overlaid.isPassed());
        assertEquals("from overlay", scenarioName(top(overlaid)));
    }

    @Test
    void anOverlaidKarateConfigIsTheConfigTheRunReadsAndTheDiskKeepsItsOwn() throws Exception {
        Path config = tempDir.resolve("karate-config.js");
        byte[] before = Files.readAllBytes(config);
        byte[] held = "function fn() { return { configValue: 'from-config', heldValue: 'held' } }".getBytes(StandardCharsets.UTF_8);
        byte[] reads = OVERLAY.replace("* def who = 'overlay'", "* match heldValue == 'held'").getBytes(StandardCharsets.UTF_8);
        SuiteResult result = builder("reports").overlay(featureFile, reads).overlay(config, held).parallel(1);
        assertTrue(result.isPassed(), "the run reads the overlaid config");
        assertArrayEquals(before, Files.readAllBytes(config));
        assertFalse(builder("reports-disk").overlay(featureFile, reads).parallel(1).isPassed(), "a run without it reads the file");
    }

    @Test
    void anOverlaidKarateConfigNotYetOnDiskIsRead() throws Exception {
        Path config = tempDir.resolve("karate-config.js");
        Files.delete(config);
        byte[] held = "function fn() { return { configValue: 'from-config' } }".getBytes(StandardCharsets.UTF_8);
        SuiteResult result = builder("reports").overlay(featureFile, OVERLAY.getBytes(StandardCharsets.UTF_8)).overlay(config, held).parallel(1);
        assertTrue(result.isPassed(), "the run reads a config that exists only as an overlay");
        assertFalse(Files.exists(config));
    }

}
