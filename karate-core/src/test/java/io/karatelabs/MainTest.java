package io.karatelabs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final Set<String> BUILTINS = Set.of("run", "mock", "clean");

    /** Test shim — these cases exercise the default-to-run logic against the built-in subcommands. */
    private static String[] defaultToRun(String[] args) {
        return Main.defaultToRun(args, BUILTINS);
    }

    @Test
    void testDefaultToRunWithNoArgs() {
        String[] result = defaultToRun(new String[]{});
        assertEquals(0, result.length);
    }

    @Test
    void testDefaultToRunPrependsRunForPaths() {
        String[] result = defaultToRun(new String[]{"karate-tests/"});
        assertArrayEquals(new String[]{"run", "karate-tests/"}, result);
    }

    @Test
    void testDefaultToRunPrependsRunForFlags() {
        // v1-style: karate -g karate-tests karate-tests/
        String[] result = defaultToRun(new String[]{"-g", "karate-tests", "karate-tests/"});
        assertArrayEquals(new String[]{"run", "-g", "karate-tests", "karate-tests/"}, result);
    }

    @Test
    void testDefaultToRunPrependsRunForMultipleFlags() {
        String[] result = defaultToRun(new String[]{"-t", "@smoke", "-e", "dev", "-T", "5", "src/test"});
        assertArrayEquals(new String[]{"run", "-t", "@smoke", "-e", "dev", "-T", "5", "src/test"}, result);
    }

    @Test
    void testDefaultToRunDoesNotPrependForRunSubcommand() {
        String[] result = defaultToRun(new String[]{"run", "karate-tests/"});
        assertArrayEquals(new String[]{"run", "karate-tests/"}, result);
    }

    @Test
    void testDefaultToRunDoesNotPrependForMockSubcommand() {
        String[] result = defaultToRun(new String[]{"mock", "-m", "mock.feature"});
        assertArrayEquals(new String[]{"mock", "-m", "mock.feature"}, result);
    }

    @Test
    void testDefaultToRunDoesNotPrependForCleanSubcommand() {
        String[] result = defaultToRun(new String[]{"clean"});
        assertArrayEquals(new String[]{"clean"}, result);
    }

    @Test
    void testDefaultToRunDoesNotPrependForHelp() {
        assertArrayEquals(new String[]{"--help"}, defaultToRun(new String[]{"--help"}));
        assertArrayEquals(new String[]{"-h"}, defaultToRun(new String[]{"-h"}));
    }

    @Test
    void testDefaultToRunDoesNotPrependForVersion() {
        assertArrayEquals(new String[]{"--version"}, defaultToRun(new String[]{"--version"}));
        assertArrayEquals(new String[]{"-V"}, defaultToRun(new String[]{"-V"}));
    }

    @Test
    void testDefaultToRunSkipsNoColorBeforeDecision() {
        // --no-color is a Main-level flag, should be skipped when finding the first real arg
        String[] result = defaultToRun(new String[]{"--no-color", "-g", "tests", "tests/"});
        assertArrayEquals(new String[]{"run", "--no-color", "-g", "tests", "tests/"}, result);
    }

    @Test
    void testDefaultToRunNoColorWithSubcommand() {
        String[] result = defaultToRun(new String[]{"--no-color", "run", "tests/"});
        assertArrayEquals(new String[]{"--no-color", "run", "tests/"}, result);
    }

    @Test
    void testDefaultToRunNoColorOnly() {
        // Only --no-color, no real args — should not prepend
        String[] result = defaultToRun(new String[]{"--no-color"});
        assertArrayEquals(new String[]{"--no-color"}, result);
    }

}
