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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * A karate-js build under test: the jar {@code --js-jar} swaps onto the child classpath, plus
 * the provenance that makes a run attributable to a <em>source commit</em> rather than a
 * filename.
 *
 * <p>Why the ceremony: both arms of an A/B typically carry the same Maven version, so the
 * version string identifies nothing, and a filename is just a claim. {@code etc/js-arm.sh}
 * writes a sidecar manifest ({@code <jar>.manifest}) at build time carrying the resolved
 * commit and the jar's SHA-256; this class recomputes the hash and <b>refuses a jar whose
 * bytes do not match its manifest</b> — a stale cache entry or a half-copied file must fail
 * the run, not relabel it. A jar without a manifest is allowed (a hand-built jar is a
 * legitimate experiment) but is recorded as {@code commit (unknown)}, which is the honest
 * label for it.
 */
final class JsArm {

    private final Path jar;
    private final String commit;
    private final String sha256;

    private JsArm(Path jar, String commit, String sha256) {
        this.jar = jar;
        this.commit = commit;
        this.sha256 = sha256;
    }

    static JsArm resolve(String jarPath) {
        Path jar = Path.of(jarPath).toAbsolutePath();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("--js-jar " + jarPath + " does not exist or is "
                    + "not a file. Build one with etc/js-arm.sh <git-ref>.");
        }
        String actual = sha256Of(jar);
        String commit = "(unknown — no manifest; identity is the sha256 only)";
        Path manifest = jar.resolveSibling(jar.getFileName() + ".manifest");
        if (Files.isReadable(manifest)) {
            String declaredSha = null;
            String declaredCommit = null;
            try {
                for (String line : Files.readAllLines(manifest)) {
                    if (line.startsWith("commit:")) {
                        declaredCommit = line.substring("commit:".length()).trim();
                    } else if (line.startsWith("sha256:")) {
                        declaredSha = line.substring("sha256:".length()).trim();
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("could not read " + manifest, e);
            }
            if (declaredSha == null || declaredCommit == null) {
                throw new IllegalArgumentException(manifest + " is malformed: it must carry "
                        + "'commit:' and 'sha256:' lines. Rebuild the arm with etc/js-arm.sh.");
            }
            if (!declaredSha.equalsIgnoreCase(actual)) {
                throw new IllegalArgumentException("REFUSING " + jar.getFileName() + ": its "
                        + "bytes (sha256 " + shortHash(actual) + ") do not match its manifest ("
                        + shortHash(declaredSha) + "). The jar is stale, truncated or replaced —"
                        + " a run against it would be attributed to commit " + declaredCommit
                        + ", which is not what would execute. Rebuild with etc/js-arm.sh.");
            }
            commit = declaredCommit;
        }
        return new JsArm(jar, commit, actual);
    }

    /**
     * Replace the karate-js entry of the child classpath with this arm's jar.
     *
     * <p>Exactly one entry must match. Zero means the classpath was built without karate-js
     * and the swap would silently change nothing; more than one means the child would load a
     * blend of two builds, with whichever wins the classloader race being the one measured.
     * Both are refused — a wrong number attributed to a named commit is the worst output this
     * harness can produce.
     */
    String rewriteClasspath(String classpath) {
        String[] entries = classpath.split(java.io.File.pathSeparator);
        List<String> rewritten = new ArrayList<>(entries.length);
        int matches = 0;
        for (String entry : entries) {
            if (isKarateJsJar(entry)) {
                matches++;
                rewritten.add(jar.toString());
            } else {
                rewritten.add(entry);
            }
        }
        if (matches != 1) {
            throw new IllegalStateException("expected exactly one karate-js jar on the child "
                    + "classpath, found " + matches + " — cannot swap in " + jar.getFileName()
                    + ". Rebuild target/cp.txt via etc/run.sh.");
        }
        return String.join(java.io.File.pathSeparator, rewritten);
    }

    static boolean isKarateJsJar(String classpathEntry) {
        String normalised = classpathEntry.replace('\\', '/');
        String fileName = normalised.substring(normalised.lastIndexOf('/') + 1);
        return fileName.startsWith("karate-js-") && fileName.endsWith(".jar")
                // The repository layout, so a jar named karate-js-tools-1.0.jar from some
                // other group cannot match by filename alone…
                && (normalised.contains("/io/karatelabs/karate-js/")
                // …while a jar outside ~/.m2 (a previously swapped arm, in a recorded command
                // being re-parsed) still matches by its js-arms location.
                || normalised.contains("/js-arms/"));
    }

    /** What run-meta.txt and the digest record: basename, commit, content hash. */
    String describe() {
        return jar.getFileName() + " commit " + commit + " sha256 " + shortHash(sha256);
    }

    private static String shortHash(String hex) {
        return hex.length() <= 16 ? hex : hex.substring(0, 16);
    }

    private static String sha256Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                        .append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException("could not hash " + file, e);
        }
    }

}
