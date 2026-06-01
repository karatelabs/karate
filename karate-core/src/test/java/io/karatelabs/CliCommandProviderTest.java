package io.karatelabs;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link Main} discovers ext-contributed subcommands via
 * {@code io.karatelabs.cli.CliCommandProvider} (ServiceLoader). The {@code dummy-serve} command is
 * registered by {@code io.karatelabs.cli.DummyCommandProvider} through
 * {@code META-INF/services/io.karatelabs.cli.CliCommandProvider} on the test classpath.
 */
class CliCommandProviderTest {

    @Test
    void contributedSubcommandIsRegistered() {
        CommandLine cmd = Main.buildCommandLine();
        Set<String> names = cmd.getSubcommands().keySet();
        // built-ins still present
        assertTrue(names.contains("run"), names.toString());
        assertTrue(names.contains("mock"), names.toString());
        assertTrue(names.contains("clean"), names.toString());
        // the contributed one
        assertTrue(names.contains("dummy-serve"), "ext-contributed subcommand discovered: " + names);
    }

    @Test
    void contributedSubcommandIsNotHijackedByDefaultToRun() {
        Set<String> names = Main.buildCommandLine().getSubcommands().keySet();
        // a registered subcommand must pass through untouched (not prepended with "run")
        assertArrayEquals(new String[]{"dummy-serve", "--flag"},
                Main.defaultToRun(new String[]{"dummy-serve", "--flag"}, names));
        // a bare path still defaults to run
        assertArrayEquals(new String[]{"run", "some/path"},
                Main.defaultToRun(new String[]{"some/path"}, names));
    }
}
