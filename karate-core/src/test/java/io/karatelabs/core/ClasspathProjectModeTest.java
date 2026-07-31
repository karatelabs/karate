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
import io.karatelabs.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Project mode" (a bare folder run — serve / MCP / packaged runtime) has no Java
 * classpath carrying the project's files, yet {@code classpath:} is the reflex most
 * karate docs and Java-team habits reach for. These tests pin the fallback in
 * {@link Resource#classpathWithRootFallback}: a classloader hit always wins
 * (Java-project behavior unchanged), a miss retries against the resource root —
 * the same anchor a leading-{@code /} ref resolves against — so a
 * {@code classpath:} ref just works in both modes.
 */
class ClasspathProjectModeTest {

    @TempDir
    Path tempDir;

    private SuiteResult run(Path feature) {
        return Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
    }

    @Test
    void readClasspathRefResolvesAgainstProjectRoot() throws Exception {
        Files.writeString(tempDir.resolve("data.json"), """
            { "id": 42 }
            """);
        Files.createDirectories(tempDir.resolve("payloads"));
        Files.writeString(tempDir.resolve("payloads/nested.json"), """
            { "nested": true }
            """);
        Path feature = tempDir.resolve("read.feature");
        Files.writeString(feature, """
            Feature: classpath read in project mode

            Scenario: root-level and nested refs
            * def data = read('classpath:data.json')
            * match data == { id: 42 }
            * def nested = read('classpath:payloads/nested.json')
            * match nested == { nested: true }
            """);

        SuiteResult result = run(feature);
        assertTrue(result.isPassed(), "classpath: read should fall back to the project root");
    }

    @Test
    void callFeatureByClasspathRefResolvesAgainstProjectRoot() throws Exception {
        Files.writeString(tempDir.resolve("called.feature"), """
            Feature: called

            Scenario:
            * def fromCalled = 'hello'
            """);
        Path feature = tempDir.resolve("caller.feature");
        Files.writeString(feature, """
            Feature: classpath call in project mode

            Scenario: call by classpath ref
            * def result = call read('classpath:called.feature')
            * match result.fromCalled == 'hello'
            """);

        SuiteResult result = run(feature);
        assertTrue(result.isPassed(), "classpath: feature call should fall back to the project root");
    }

    @Test
    void callSingleClasspathRefFromConfigResolvesAgainstProjectRoot() throws Exception {
        // karate-config.js evaluates against a MemoryResource — the config-eval lane
        // where a project's karate.callSingle('classpath:…') previously hard-failed.
        Files.writeString(tempDir.resolve("once.js"), """
            (function(){ return { seeded: 'once' }; })()
            """);
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() { return { shared: karate.callSingle('classpath:once.js') }; }
            """);
        Path feature = tempDir.resolve("config.feature");
        Files.writeString(feature, """
            Feature: classpath callSingle in project mode

            Scenario: config seeded via classpath ref
            * match shared.seeded == 'once'
            """);

        SuiteResult result = run(feature);
        assertTrue(result.isPassed(), "classpath: callSingle from karate-config.js should fall back to the project root");
    }

    @Test
    void classloaderHitStillWinsOverRootFallback() throws Exception {
        // json/cat.json exists on the test classpath; a same-named file in the project
        // root must NOT shadow it — the classloader stays the first authority.
        Files.createDirectories(tempDir.resolve("json"));
        Files.writeString(tempDir.resolve("json/cat.json"), """
            { "name": "Imposter", "age": 99 }
            """);
        Path feature = tempDir.resolve("cp.feature");
        Files.writeString(feature, """
            Feature: classloader wins

            Scenario: real classpath resource
            * def cat = read('classpath:json/cat.json')
            * match cat == { name: 'Billie', age: 5 }
            """);

        SuiteResult result = run(feature);
        assertTrue(result.isPassed(), "a genuine classpath resource must resolve via the classloader, not the root fallback");
    }

    @Test
    void missOnBothStillFailsWithResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> Resource.classpathWithRootFallback("classpath:no/such/thing.json", tempDir));
    }

    @Test
    void theStaticDoorReachesTheFallbackToo() throws Exception {
        // Resource.path(String) is classloader-only — every serve-lane namespace used to come in
        // through it, so the "one mental model" stopped exactly where karate-max's own namespaces
        // started. The root-carrying overload is that same door with the fallback wired in.
        Files.writeString(tempDir.resolve("data.json"), "{ \"id\": 42 }");
        assertThrows(ResourceNotFoundException.class, () -> Resource.path("classpath:data.json"));
        assertEquals(tempDir.resolve("data.json"),
                Resource.path("classpath:data.json", tempDir).getPath());
    }

    @Test
    void theStaticDoorResolvesEverySpellingToTheSameFile() throws Exception {
        Files.createDirectories(tempDir.resolve("api"));
        Path spec = Files.writeString(tempDir.resolve("api/openapi.yaml"), "openapi: 3.0.0");
        assertEquals(spec, Resource.path("/api/openapi.yaml", tempDir).getPath());
        assertEquals(spec, Resource.path("api/openapi.yaml", tempDir).getPath());
        assertEquals(spec, Resource.path("classpath:api/openapi.yaml", tempDir).getPath());
        assertEquals(spec, Resource.path("file:" + spec, tempDir).getPath());
    }

    @Test
    void aClassloaderHitIsReAnchoredOnTheCallersRoot() {
        // the hit still wins (see above) — but the Resource it yields carries the caller's root,
        // so a leading-"/" reference made from INSIDE it stays in the project instead of leaking
        // to the process CWD
        Resource hit = Resource.path("classpath:json/cat.json", tempDir);
        assertTrue(hit.isClassPath());
        assertEquals(tempDir, hit.getRoot());
        assertEquals(tempDir.resolve("other.json"), hit.resolve("/other.json").getPath());
    }
}
