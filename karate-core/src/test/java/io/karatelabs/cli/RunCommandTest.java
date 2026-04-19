/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 */
package io.karatelabs.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
}
