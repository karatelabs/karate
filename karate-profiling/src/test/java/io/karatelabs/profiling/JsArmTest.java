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
package io.karatelabs.profiling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two properties a wrong number would ride in on: the classpath swap must be
 * exactly-one-jar or refuse, and a jar whose bytes disagree with its manifest must fail the
 * run rather than relabel it.
 */
class JsArmTest {

    @TempDir
    Path dir;

    private static final String M2_JS = "/home/x/.m2/repository/io/karatelabs/karate-js/2.1.2.RC2/karate-js-2.1.2.RC2.jar";
    private static final String M2_CORE = "/home/x/.m2/repository/io/karatelabs/karate-core/2.1.2.RC2/karate-core-2.1.2.RC2.jar";

    private Path jar(String name, String content) throws IOException {
        Path jar = dir.resolve(name);
        Files.writeString(jar, content);
        return jar;
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(Files.readAllBytes(file))) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    @Test
    void swapsExactlyTheKarateJsEntry() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "arm bytes");
        String classpath = String.join(File.pathSeparator, "/x/target/classes", M2_JS, M2_CORE);
        String rewritten = JsArm.resolve(arm.toString()).rewriteClasspath(classpath);
        assertEquals(String.join(File.pathSeparator, "/x/target/classes", arm.toString(), M2_CORE),
                rewritten);
    }

    @Test
    void refusesAClasspathWithoutKarateJs() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "arm bytes");
        JsArm resolved = JsArm.resolve(arm.toString());
        String classpath = String.join(File.pathSeparator, "/x/target/classes", M2_CORE);
        assertThrows(IllegalStateException.class, () -> resolved.rewriteClasspath(classpath));
    }

    @Test
    void refusesAClasspathWithTwoKarateJsEntries() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "arm bytes");
        JsArm resolved = JsArm.resolve(arm.toString());
        String twice = String.join(File.pathSeparator, M2_JS,
                "/elsewhere/js-arms/karate-js-def5678.jar");
        assertThrows(IllegalStateException.class, () -> resolved.rewriteClasspath(twice));
    }

    @Test
    void recognisesRepositoryAndArmLayoutsOnly() {
        assertTrue(JsArm.isKarateJsJar(M2_JS));
        assertTrue(JsArm.isKarateJsJar("/x/target/js-arms/karate-js-abc1234.jar"));
        // Same basename shape, wrong provenance: not swapped.
        assertEquals(false, JsArm.isKarateJsJar("/opt/vendor/karate-js-tools-1.0.jar"));
        assertEquals(false, JsArm.isKarateJsJar(M2_CORE));
    }

    @Test
    void manifestMismatchIsFatal() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "actual bytes");
        Files.writeString(dir.resolve("karate-js-abc1234.jar.manifest"),
                "commit: 1111111111111111111111111111111111111111\nsha256: " + "0".repeat(64) + "\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> JsArm.resolve(arm.toString()));
        assertTrue(e.getMessage().contains("REFUSING"), e.getMessage());
    }

    @Test
    void matchingManifestCarriesTheCommitIntoProvenance() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "actual bytes");
        Files.writeString(dir.resolve("karate-js-abc1234.jar.manifest"),
                "commit: 1111111111111111111111111111111111111111\nsha256: " + sha256(arm) + "\n");
        String description = JsArm.resolve(arm.toString()).describe();
        assertTrue(description.contains("commit 1111111111111111111111111111111111111111"),
                description);
        assertTrue(description.contains("sha256 " + sha256(arm).substring(0, 16)), description);
    }

    @Test
    void missingManifestIsAllowedButSaysSo() throws Exception {
        Path arm = jar("karate-js-abc1234.jar", "hand-built bytes");
        String description = JsArm.resolve(arm.toString()).describe();
        assertTrue(description.contains("unknown"), description);
    }

}
