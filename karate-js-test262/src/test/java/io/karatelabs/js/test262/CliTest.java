package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {

    @Test
    void testRawArgsDoesNotDuplicateFlagValues() {
        // regression: value-bearing flags used to append their value twice to rawArgs,
        // producing corrupted `cli_args` in run-meta.json.
        Cli c = Cli.parse(new String[] {
                "--timeout-ms", "5000",
                "--only", "test/language/**",
                "--run-dir", "target/test262/run-x",
                "--single", "test/foo.js"
        });
        assertEquals(8, c.rawArgs.size(), c.rawArgs.toString());
        assertEquals("--timeout-ms", c.rawArgs.get(0));
        assertEquals("5000", c.rawArgs.get(1));
        assertEquals("--only", c.rawArgs.get(2));
        assertEquals("test/language/**", c.rawArgs.get(3));
        assertEquals("--run-dir", c.rawArgs.get(4));
        assertEquals("target/test262/run-x", c.rawArgs.get(5));
        assertEquals("--single", c.rawArgs.get(6));
        assertEquals("test/foo.js", c.rawArgs.get(7));
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
        assertNull(c.runDir);
    }

    @Test
    void testMaxDurationFlag() {
        Cli c = Cli.parse(new String[] { "--max-duration", "30000" });
        assertEquals(30_000L, c.maxDurationMs);
    }

    @Test
    void testRunDirFlag() {
        Cli c = Cli.parse(new String[] { "--run-dir", "target/test262/run-foo" });
        assertEquals(Paths.get("target/test262/run-foo"), c.runDir);
    }
}
