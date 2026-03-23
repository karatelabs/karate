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
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"Karate " + Globals.KARATE_VERSION};
        }
    }

    @Parameters(
            arity = "0..*",
            hidden = true,
            description = "Legacy: feature files or directories (use 'karate run' instead)"
    )
    List<String> unknownArgs;

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

        int exitCode = new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Handle color settings
        if (noColor) {
            Console.setColorsEnabled(false);
        }

        // No subcommand specified - check for legacy args or karate.json
        if (unknownArgs != null && !unknownArgs.isEmpty()) {
            // Legacy mode: treat args as paths and delegate to run
            RunCommand runCommand = new RunCommand();
            return runCommand.runWithPaths(unknownArgs);
        }

        // No args - look for karate-pom.json in current directory
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
