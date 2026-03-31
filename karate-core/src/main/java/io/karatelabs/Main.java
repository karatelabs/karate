/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs;

import io.karatelabs.cli.CleanCommand;
import io.karatelabs.cli.MockCommand;
import io.karatelabs.cli.RunCommand;
import io.karatelabs.output.Console;
import io.karatelabs.core.Globals;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Main entry point for the Karate CLI.
 * <p>
 * Supports two usage modes:
 * <ul>
 *   <li>Subcommand mode: {@code karate run src/test/features}</li>
 *   <li>Legacy mode: {@code karate src/test/features} (delegates to run)</li>
 * </ul>
 * <p>
 * When invoked without arguments, looks for {@code karate.json} in the current directory.
 */
@Command(
        name = "karate",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Karate API testing framework",
        subcommands = {
                RunCommand.class,
                MockCommand.class,
                CleanCommand.class
        }
)
public class Main implements Callable<Integer> {

    private static final Set<String> SUBCOMMANDS = Set.of("run", "mock", "clean");

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"Karate " + Globals.KARATE_VERSION};
        }
    }

    @Option(
            names = {"--no-color"},
            description = "Disable colored output"
    )
    boolean noColor;

    public static void main(String[] args) {
        // Handle color settings early
        for (String arg : args) {
            if ("--no-color".equals(arg)) {
                Console.setColorsEnabled(false);
                break;
            }
        }

        // Default to "run" subcommand when no subcommand is specified.
        // This allows v1-style commands like: karate -g tests tests/
        // to work the same as: karate run -g tests tests/
        args = defaultToRun(args);

        int exitCode = new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    /**
     * If args don't start with a known subcommand (or --help/-h/--version/-V),
     * prepend "run" so PicoCLI routes through RunCommand with full flag parsing.
     */
    static String[] defaultToRun(String[] args) {
        if (args.length == 0) {
            return args;
        }
        // Find the first arg that isn't a Main-level option
        String first = null;
        for (String arg : args) {
            if ("--no-color".equals(arg)) {
                continue;
            }
            first = arg;
            break;
        }
        if (first == null) {
            return args;
        }
        // Don't interfere with help/version flags or known subcommands
        if (first.equals("-h") || first.equals("--help")
                || first.equals("-V") || first.equals("--version")
                || SUBCOMMANDS.contains(first)) {
            return args;
        }
        // Prepend "run" so all flags are parsed by RunCommand
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "run";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return newArgs;
    }

    @Override
    public Integer call() {
        // Handle color settings
        if (noColor) {
            Console.setColorsEnabled(false);
        }

        // No args at all - look for karate-pom.json in current directory
        if (Files.exists(Path.of(RunCommand.DEFAULT_POM_FILE))) {
            RunCommand runCommand = new RunCommand();
            return runCommand.call();
        }

        // No subcommand, no args, no karate-pom.json - show help
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Parse command-line arguments without executing.
     * Useful for integrating with other tools.
     */
    public static Main parse(String... args) {
        Main main = new Main();
        new CommandLine(main).parseArgs(args);
        return main;
    }

}
