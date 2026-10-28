package io.karatelabs.cli;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The CLI in a Gradle and a Maven layout: a {@code karate-pom.json} holding only the reserved
 * {@code xplorer} key resolves the same report dir as no file, and a bare {@code karate} prints help.
 */
class PomLayoutTest {

    @ParameterizedTest
    @ValueSource(strings = {"build.gradle", "pom.xml"})
    void aPomHoldingOnlyXplorerBehavesAsNoFile(String buildFile, @TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(buildFile), "");
        String buildDir = buildFile.equals("pom.xml") ? "target" : "build";
        Path reports = dir.resolve(buildDir).resolve("karate-reports");
        Files.writeString(dir.resolve("t.feature"), """
                Feature: t
                Scenario: one
                * def x = 1
                """);
        Path pom = dir.resolve("karate-pom.json");
        Files.writeString(pom, """
                { "xplorer": { "collections": ["api.postman_collection.json"] } }
                """);

        String ran = karate(dir, "run", "t.feature");
        assertTrue(Files.exists(reports.resolve("karate-summary.html")), buildDir + " with the pom: " + ran);
        String cleaned = karate(dir, "clean");
        assertFalse(Files.exists(reports), "cleaned " + buildDir + ": " + cleaned);
        assertTrue(cleaned.contains("Cleaned: karate-reports"), cleaned);
        String bare = karate(dir);
        assertTrue(bare.contains("Usage:"), "a bare karate prints help: " + bare);
        assertFalse(bare.contains("No test paths specified"), bare);

        Files.delete(pom);
        String ranWithout = karate(dir, "run", "t.feature");
        assertTrue(Files.exists(reports.resolve("karate-summary.html")), buildDir + " without the pom: " + ranWithout);
        String bareWithout = karate(dir);
        assertTrue(bareWithout.contains("Usage:"), bareWithout);
    }

    /** Runs the CLI in its own JVM with {@code dir} as the working dir and returns its combined output. */
    private static String karate(Path dir, String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(javaBin, "-cp", classPath, "io.karatelabs.Main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(new File(dir.toFile(), "out.txt"))
                .start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the CLI exited");
        return Files.readString(dir.resolve("out.txt"));
    }

}
