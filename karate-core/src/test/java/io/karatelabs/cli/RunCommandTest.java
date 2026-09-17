/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.cli;

import io.karatelabs.core.RunEvent;
import io.karatelabs.core.RunListener;
import io.karatelabs.core.Suite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RunCommandTest {

    @Test
    void testRepeatablePathOption() {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs(
                "-P", "classpath:com/comp/commission",
                "-P", "classpath:com/comp/statement"
        );
        assertEquals(2, cmd.pathOptions.size());
        assertEquals("classpath:com/comp/commission", cmd.pathOptions.get(0));
        assertEquals("classpath:com/comp/statement", cmd.pathOptions.get(1));
        assertNull(cmd.paths);
    }

    @Test
    void testLongFormPathOption() {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs(
                "--path", "features/a",
                "--path", "features/b",
                "--path", "features/c"
        );
        assertEquals(3, cmd.pathOptions.size());
    }

    @Test
    void testPositionalAndPathOptionMerge() throws Exception {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs(
                "-P", "classpath:com/comp/commission",
                "features/positional"
        );
        assertEquals(1, cmd.pathOptions.size());
        assertEquals(1, cmd.paths.size());

        java.lang.reflect.Method m = RunCommand.class.getDeclaredMethod("resolvePaths");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> merged = (java.util.List<String>) m.invoke(cmd);
        assertEquals(2, merged.size());
        assertEquals("features/positional", merged.get(0));
        assertEquals("classpath:com/comp/commission", merged.get(1));
    }

    @Test
    void testPathOptionsNullWhenNotProvided() {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs("features/");
        assertNull(cmd.pathOptions);
        assertEquals(1, cmd.paths.size());
    }

    @Test
    void testCommaSeparatedPathOption() {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs(
                "--path", "classpath:com/comp/commission,classpath:com/comp/statement"
        );
        assertEquals(2, cmd.pathOptions.size());
        assertEquals("classpath:com/comp/commission", cmd.pathOptions.get(0));
        assertEquals("classpath:com/comp/statement", cmd.pathOptions.get(1));
    }

    @Test
    void testMixedRepeatedAndCommaSeparated() {
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs(
                "-P", "a,b",
                "-P", "c"
        );
        assertEquals(3, cmd.pathOptions.size());
        assertEquals("a", cmd.pathOptions.get(0));
        assertEquals("b", cmd.pathOptions.get(1));
        assertEquals("c", cmd.pathOptions.get(2));
    }

    public static class NoopListener implements RunListener {
        @Override
        public boolean onEvent(RunEvent event) {
            return true;
        }
    }

    private static Path writeFeature(Path dir, String name) throws Exception {
        return Files.writeString(dir.resolve(name), """
            Feature: minimal

            Scenario: one
              * def x = 1
              * match x == 1
            """);
    }

    private static String json(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    @Test
    void testPomPathsAndListenersRegisteredOnce(@TempDir Path dir) throws Exception {
        Path feature = writeFeature(dir, "minimal.feature");
        Files.writeString(dir.resolve(RunCommand.DEFAULT_POM_FILE), """
            {
              "paths": ["%s"],
              "listeners": ["%s"]
            }
            """.formatted(json(feature), NoopListener.class.getName()));
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs("-w", dir.toString());
        cmd.loadPom();
        Suite suite = cmd.toBuilder().buildSuite();
        assertEquals(1, suite.features.size());
        assertEquals(1, suite.listeners.size());
    }

    @Test
    void testCliPathsReplacePomPaths(@TempDir Path dir) throws Exception {
        Path fromPom = writeFeature(dir, "from-pom.feature");
        Path fromCli = writeFeature(dir, "from-cli.feature");
        Files.writeString(dir.resolve(RunCommand.DEFAULT_POM_FILE), """
            {
              "paths": ["%s"]
            }
            """.formatted(json(fromPom)));
        RunCommand cmd = new RunCommand();
        new CommandLine(cmd).parseArgs("-w", dir.toString(), fromCli.toString());
        cmd.loadPom();
        Suite suite = cmd.toBuilder().buildSuite();
        assertEquals(1, suite.features.size());
        assertEquals(fromCli, suite.features.get(0).getResource().getPath());
    }

}
