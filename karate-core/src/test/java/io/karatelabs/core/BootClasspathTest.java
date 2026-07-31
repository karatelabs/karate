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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code boot.classpath(dir)} — the one user-facing verb that lets a project whose files live under
 * a build layout ({@code src/test/resources}) resolve every reference the same way in a JVM run and
 * in a bare-folder run. It does double duty: it declares the dir a {@code classpath:} MISS falls
 * back to, and (when no working dir was explicitly chosen) it re-anchors the working dir on THE
 * root. These pin both halves, plus the two ways it must stay inert.
 */
class BootClasspathTest {

    @TempDir
    Path project;

    private void write(String relative, String content) throws Exception {
        Path target = project.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private Suite build() {
        return Runner.builder().configDir(project.toString()).buildSuite();
    }

    @Test
    void declaredDirBecomesTheClasspathFallbackWithoutMovingTheRoot() throws Exception {
        write("karate-boot.js", "boot.classpath('src/test/resources');\n");
        Suite suite = build();
        assertEquals(project, suite.getRoot(), "the root stays the project dir");
        assertEquals(project.resolve("src/test/resources"), suite.getClasspathRoot());
    }

    @Test
    void undeclaredProjectHasClasspathRootEqualToRoot() throws Exception {
        write("karate-boot.js", "boot.log('nothing declared');\n");
        Suite suite = build();
        assertEquals(suite.getRoot(), suite.getClasspathRoot(),
                "a bare-folder project needs no declaration");
    }

    @Test
    void emptyStringIsValidAndMeansTheRoot() throws Exception {
        write("karate-boot.js", "boot.classpath('');\n");
        Suite suite = build();
        assertEquals(project, suite.getClasspathRoot());
        assertEquals(project, suite.getWorkingDir(), "'' still re-anchors the working dir");
    }

    @Test
    void declaringAClasspathDirReAnchorsTheWorkingDirOnTheRoot() throws Exception {
        write("karate-boot.js", "boot.classpath('src/test/resources');\n");
        Suite suite = build();
        assertEquals(project, suite.getWorkingDir(),
                "one committed line makes a Java project's working dir the project root");
    }

    @Test
    void anExplicitWorkingDirSuppressesTheReAnchor() throws Exception {
        // configDir points one level in, so THE root and the working dir genuinely differ: without an
        // explicit working dir the declaration would re-anchor it onto the root
        write("karate-boot.js", "boot.classpath('src/test/resources');\n");
        Suite suite = Runner.builder()
                .configDir(project.resolve("cfg").toString())
                .workingDir(project)
                .buildSuite();
        assertEquals(project.resolve("cfg"), suite.getRoot());
        assertEquals(project, suite.getWorkingDir(), "an explicit choice always wins");
        assertEquals(project.resolve("cfg/src/test/resources"), suite.getClasspathRoot(),
                "…but the classpath mapping still applies, against THE root");
    }

    @Test
    void noBootFileLeavesEveryAnchorAtItsDefault() {
        Suite suite = build();
        assertNull(suite.getBootBinding());
        assertEquals(project, suite.getRoot());
        assertEquals(project, suite.getClasspathRoot());
        assertEquals(io.karatelabs.common.FileUtils.WORKING_DIR.toPath(), suite.getWorkingDir(),
                "with nothing declared the working dir is the process CWD, as before");
    }

    @Test
    void aClasspathMissFallsBackToTheDeclaredDir() throws Exception {
        write("karate-boot.js", "boot.classpath('src/test/resources');\n");
        write("src/test/resources/data/x.json", "{ \"id\": 7 }\n");
        write("features/read.feature", """
            Feature: mapped classpath ref

            Scenario: a classpath: miss retries under the declared dir
            * def data = read('classpath:data/x.json')
            * match data == { id: 7 }
            """);
        SuiteResult result = Runner.path(project.resolve("features/read.feature").toString())
                .configDir(project.toString())
                .outputDir(project.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed(), "classpath: should fall back under the boot-declared dir");
    }

    @Test
    void theSameFileIsReachableByBothSpellingsFromTheMappedDir() throws Exception {
        write("karate-boot.js", "boot.classpath('src/test/resources');\n");
        write("src/test/resources/data/x.json", "{ \"id\": 7 }\n");
        write("features/both.feature", """
            Feature: one file, two spellings

            Scenario: classpath: and the root-anchored form agree
            * def viaClasspath = read('classpath:data/x.json')
            * def viaRoot = read('/src/test/resources/data/x.json')
            * match viaClasspath == viaRoot
            """);
        SuiteResult result = Runner.path(project.resolve("features/both.feature").toString())
                .configDir(project.toString())
                .outputDir(project.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());
    }

    @Test
    void aPrefixedOrAbsoluteArgumentIsRejectedWithGuidance() throws Exception {
        write("karate-boot.js", "boot.classpath('classpath:src/test/resources');\n");
        RuntimeException e = assertThrows(RuntimeException.class, this::build);
        assertTrue(e.getMessage().contains("RELATIVE to the project root"),
                "expected pointed guidance, got: " + e.getMessage());
    }

    @Test
    void bootReadResolvesOnTheUnifiedRule() throws Exception {
        write("data/greeting.txt", "hello");
        write("karate-boot.js", """
            var bare = boot.read('data/greeting.txt');
            var rooted = boot.read('/data/greeting.txt');
            if (bare !== 'hello' || rooted !== 'hello') {
                throw new Error('boot.read did not resolve against the root: ' + bare + '/' + rooted);
            }
            """);
        assertNotNull(build().getBootBinding());
    }
}
