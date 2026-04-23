package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {

    @Test
    void testRawArgsDoesNotDuplicateFlagValues() {
        // regression: value-bearing flags used to append their value twice to rawArgs,
        // producing corrupted `cli_args` in run-meta.json.
        Cli c = Cli.parse(new String[] {
                "--timeout-ms", "5000",
                "--only", "test/language/**",
                "--resume",
                "--single", "test/foo.js"
        });
        assertEquals(7, c.rawArgs.size(), c.rawArgs.toString());
        assertEquals("--timeout-ms", c.rawArgs.get(0));
        assertEquals("5000", c.rawArgs.get(1));
        assertEquals("--only", c.rawArgs.get(2));
        assertEquals("test/language/**", c.rawArgs.get(3));
        assertEquals("--resume", c.rawArgs.get(4));
        assertEquals("--single", c.rawArgs.get(5));
        assertEquals("test/foo.js", c.rawArgs.get(6));
    }

    @Test
    void testVerboseFlags() {
        assertEquals(1, Cli.parse(new String[] { "-v" }).verbose);
        assertEquals(2, Cli.parse(new String[] { "-vv" }).verbose);
        assertEquals(0, Cli.parse(new String[] {}).verbose);
    }

    @Test
    void testDefaults() {
        Cli c = Cli.parse(new String[] {});
        assertEquals(10_000L, c.timeoutMs);
        assertEquals(0L, c.maxDurationMs);
        assertNull(c.only);
        assertNull(c.single);
        assertFalse(c.resume);
    }

    @Test
    void testMaxDurationFlag() {
        Cli c = Cli.parse(new String[] { "--max-duration", "30000" });
        assertEquals(30_000L, c.maxDurationMs);
    }
}
