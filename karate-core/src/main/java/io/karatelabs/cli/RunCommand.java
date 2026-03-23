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
package io.karatelabs.cli;

import io.karatelabs.output.Console;
import io.karatelabs.output.LogContext;
import io.karatelabs.core.Globals;
import io.karatelabs.core.KaratePom;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The 'run' subcommand for executing Karate tests.
 * <p>
 * Usage examples:
 * <pre>
 * # Run all tests (uses karate-pom.json if present)
 * karate run
 *
 * # Run specific paths (inherits other settings from karate-pom.json)
 * karate run src/test/resources
 *
 * # Run with specific tags and environment
 * karate run -t @smoke -e dev src/test/resources
 *
 * # Run with custom pom file
 * karate run -p custom-pom.json
 *
 * # Run ignoring karate-pom.json
 * karate run --no-pom src/test/resources
 * </pre>
 */
@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "Run Karate tests"
)
public class RunCommand implements Callable<Integer> {

    public static final String DEFAULT_POM_FILE = "karate-pom.json";

    @Parameters(
            description = "Feature files or directories to run",
            arity = "0..*"
    )
    List<String> paths;

    @Option(
            names = {"-t", "--tags"},
            description = "Tag expression to filter scenarios (e.g., '@smoke', '~@slow')"
    )
    List<String> tags;

    @Option(
            names = {"-T", "--threads"},
            description = "Number of parallel threads (default: 1)"
    )
    Integer threads;

    @Option(
            names = {"-e", "--env"},
            description = "Value of 'karate.env'"
    )
    String env;

    @Option(
            names = {"-n", "--name"},
            description = "Scenario name filter (regex)"
    )
    String scenarioName;

    @Option(
            names = {"-o", "--output"},
            description = "Output directory for reports (default: target/karate-reports)"
    )
    String outputDir;

    @Option(
            names = {"-g", "--configdir"},
            description = "Directory containing karate-config.js"
    )
    String configDir;

    @Option(
            names = {"-w", "--workdir"},
            description = "Working directory for relative path resolution (default: current directory)"
    )
    String workingDir;

    @Option(
            names = {"-C", "--clean"},
            description = "Clean output directory before running"
    )
    Boolean clean;

    @Option(
            names = {"-B", "--backup-reportdir"},
            defaultValue = "true",
            fallbackValue = "true",
            arity = "0..1",
            description = "Backup report directory before running tests (default: true)"
    )
    Boolean backup;

    @Option(
            names = {"-D", "--dryrun"},
            description = "Dry run mode (parse but don't execute)"
    )
    Boolean dryRun;

    @Option(
            names = {"-p", "--pom"},
            description = "Path to project file (default: karate-pom.json)"
    )
    String pomFile;

    @Option(
            names = {"--no-pom"},
            description = "Ignore karate-pom.json even if present"
    )
    boolean noPom;

    @Option(
            names = {"--report-log-level"},
            description = "Minimum log level for HTML reports: trace, debug, info, warn, error (default: info)"
    )
    String reportLogLevel;

    @Option(
            names = {"--runtime-log-level"},
            description = "Runtime log level for console/JVM output: trace, debug, info, warn, error"
    )
    String runtimeLogLevel;

    @Option(
            names = {"-f", "--format"},
            split = ",",
            description = "Comma-separated output formats. Use ~ to disable. "
                    + "e.g. '-f ~html,cucumber:json' "
                    + "Formats: html (default), cucumber:json, junit:xml, karate:jsonl"
    )
    List<String> formats;

    @Option(
            names = {"--listener"},
            split = ",",
            description = "Comma-separated RunListener class names (must have no-arg constructor)"
    )
    List<String> listeners;

    @Option(
            names = {"--listener-factory"},
            split = ",",
            description = "Comma-separated RunListenerFactory class names (must have no-arg constructor). "
                    + "Creates per-thread listener instances."
    )
    List<String> listenerFactories;

    // Loaded pom config
    private KaratePom pom;

    @Override
    public Integer call() {
        // Set runtime log level early (before any logging)
        if (runtimeLogLevel != null) {
            LogContext.setRuntimeLogLevel(runtimeLogLevel);
        }

        // Load pom file (unless --no-pom)
        if (!noPom) {
            loadPom();
        }

        // Resolve effective values (CLI overrides pom)
        List<String> effectivePaths = resolvePaths();
        String effectiveOutputDir = resolveOutputDir();
        String effectiveWorkingDir = resolveWorkingDir();
        int effectiveThreads = resolveThreads();
        boolean effectiveClean = resolveClean();
        boolean effectiveDryRun = resolveDryRun();
        boolean effectiveBackup = resolveBackup();

        // Clean output directory if requested
        if (effectiveClean) {
            cleanOutputDir(effectiveOutputDir, effectiveWorkingDir);
        }

        // Check if paths are provided
        if (effectivePaths == null || effectivePaths.isEmpty()) {
            Console.println(Console.yellow("No test paths specified."));
            Console.println("Usage: karate run [options] <paths...>");
            Console.println("       karate run -p karate-pom.json");
            Console.println("Run 'karate run --help' for more information.");
            return 0;
        }

        // Build and run
        try {
            Runner.Builder builder = Runner.builder();

            // Apply pom settings first (if present)
            if (pom != null) {
                pom.applyTo(builder);
            }

            // CLI options override pom
            builder.path(effectivePaths);
            builder.outputDir(effectiveOutputDir);

            if (effectiveDryRun) {
                builder.dryRun(true);
            }

            if (env != null) {
                builder.karateEnv(env);
            } else if (pom != null && pom.getEnv() != null) {
                builder.karateEnv(pom.getEnv());
            }

            if (tags != null && !tags.isEmpty()) {
                builder.tags(tags.toArray(new String[0]));
            } else if (pom != null && !pom.getTags().isEmpty()) {
                builder.tags(pom.getTags().toArray(new String[0]));
            }

            if (scenarioName != null) {
                builder.scenarioName(scenarioName);
            } else if (pom != null && pom.getScenarioName() != null) {
                builder.scenarioName(pom.getScenarioName());
            }

            if (configDir != null) {
                builder.configDir(configDir);
            } else if (pom != null && pom.getConfigDir() != null) {
                builder.configDir(pom.getConfigDir());
            }

            if (effectiveWorkingDir != null) {
                builder.workingDir(effectiveWorkingDir);
            }

            builder.backupOutputDir(effectiveBackup);

            // Log level
            String effectiveLogLevel = resolveLogLevel();
            if (effectiveLogLevel != null) {
                builder.logLevel(effectiveLogLevel);
            }

            // Output formats (HTML is default on, others default off)
            // CLI -f flag overrides pom settings
            if (formats != null) {
                builder.outputHtmlReport(isFormatEnabled("html", true));
                builder.outputCucumberJson(isFormatEnabled("cucumber:json", false));
                builder.outputJunitXml(isFormatEnabled("junit:xml", false));
                builder.outputJsonLines(isFormatEnabled("karate:jsonl", false));
            }

            // Listeners (CLI overrides pom)
            List<String> effectiveListeners = resolveListeners();
            if (effectiveListeners != null) {
                for (String className : effectiveListeners) {
                    builder.listenerFactory(className);  // handles both RunListener and RunListenerFactory
                }
            }

            List<String> effectiveListenerFactories = resolveListenerFactories();
            if (effectiveListenerFactories != null) {
                for (String className : effectiveListenerFactories) {
                    builder.listenerFactory(className);
                }
            }

            // Run tests
            SuiteResult result = builder.parallel(effectiveThreads);

            // Return exit code based on test results
            return result.isFailed() ? 1 : 0;

        } catch (Exception e) {
            Console.println(Console.fail("Error: " + e.getMessage()));
            e.printStackTrace();
            return 3;
        }
    }

    private void loadPom() {
        String file = pomFile != null ? pomFile : DEFAULT_POM_FILE;
        Path pomPath;

        // If workdir specified, look for pom there
        if (workingDir != null) {
            pomPath = Path.of(workingDir).resolve(file);
        } else {
            pomPath = Path.of(file);
        }

        if (Files.exists(pomPath)) {
            try {
                pom = KaratePom.load(pomPath);
                Console.println(Console.info(pomPath.getFileName() + " loaded"));
            } catch (Exception e) {
                Console.println(Console.warn("Failed to load pom: " + e.getMessage()));
            }
        }
    }

    private List<String> resolvePaths() {
        if (paths != null && !paths.isEmpty()) {
            return paths;
        }
        if (pom != null && !pom.getPaths().isEmpty()) {
            return pom.getPaths();
        }
        return null;
    }

    private String resolveOutputDir() {
        if (outputDir != null) {
            return outputDir;
        }
        if (pom != null && pom.getOutput().getDir() != null) {
            return pom.getOutput().getDir();
        }
        // Default: detect build tool (Gradle uses "build", Maven uses "target")
        return io.karatelabs.common.FileUtils.getBuildDir() + "/karate-reports";
    }

    private String resolveWorkingDir() {
        if (workingDir != null) {
            return workingDir;
        }
        if (pom != null && pom.getWorkingDir() != null) {
            return pom.getWorkingDir();
        }
        return null;
    }

    private int resolveThreads() {
        if (threads != null) {
            return threads;
        }
        if (pom != null && pom.getThreads() > 0) {
            return pom.getThreads();
        }
        return 1;
    }

    private boolean resolveClean() {
        if (clean != null) {
            return clean;
        }
        if (pom != null) {
            return pom.isClean();
        }
        return false;
    }

    private boolean resolveDryRun() {
        if (dryRun != null) {
            return dryRun;
        }
        if (pom != null) {
            return pom.isDryRun();
        }
        return false;
    }

    private boolean resolveBackup() {
        if (backup != null) {
            return backup;
        }
        // Note: KaratePom doesn't support backup yet
        // Default is true (matches v1 behavior)
        return true;
    }

    private String resolveLogLevel() {
        if (reportLogLevel != null) {
            return reportLogLevel;
        }
        if (pom != null && pom.getOutput().getLogLevel() != null) {
            return pom.getOutput().getLogLevel();
        }
        return null; // Use default (INFO)
    }

    private List<String> resolveListeners() {
        if (listeners != null && !listeners.isEmpty()) {
            return listeners;
        }
        if (pom != null && !pom.getListeners().isEmpty()) {
            return pom.getListeners();
        }
        return null;
    }

    private List<String> resolveListenerFactories() {
        if (listenerFactories != null && !listenerFactories.isEmpty()) {
            return listenerFactories;
        }
        if (pom != null && !pom.getListenerFactories().isEmpty()) {
            return pom.getListenerFactories();
        }
        return null;
    }

    /**
     * Check if a format is enabled based on the -f/--format flag.
     * Supports negation with ~ prefix (e.g., ~html disables html).
     *
     * @param format       the format name (e.g., "html", "cucumber:json")
     * @param defaultValue the default value if format not mentioned
     * @return true if format is enabled
     */
    private boolean isFormatEnabled(String format, boolean defaultValue) {
        if (formats == null) {
            return defaultValue;
        }
        // Check for explicit disable (~format)
        if (formats.contains("~" + format)) {
            return false;
        }
        // Check for explicit enable
        if (formats.contains(format)) {
            return true;
        }
        // Not mentioned, use default
        return defaultValue;
    }

    /**
     * Run with paths provided externally (for legacy command handling).
     *
     * @param legacyPaths paths from legacy command invocation
     * @return exit code
     */
    public Integer runWithPaths(List<String> legacyPaths) {
        this.paths = legacyPaths;
        return call();
    }

    private void cleanOutputDir(String dir, String workDir) {
        Path output;
        if (workDir != null) {
            output = Path.of(workDir).resolve(dir);
        } else {
            output = Path.of(dir);
        }

        if (Files.exists(output)) {
            try {
                deleteDirectory(output.toFile());
                Console.println(Console.info("Cleaned: " + output));
            } catch (Exception e) {
                Console.println(Console.warn("Failed to clean output directory: " + e.getMessage()));
            }
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    // ========== Getters for programmatic access ==========

    public List<String> getPaths() {
        return paths;
    }

    public List<String> getTags() {
        return tags;
    }

    public Integer getThreads() {
        return threads;
    }

    public String getEnv() {
        return env;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public String getConfigDir() {
        return configDir;
    }

    public Boolean isDryRun() {
        return dryRun;
    }

    public String getPomFile() {
        return pomFile;
    }

    public String getWorkingDir() {
        return workingDir;
    }

}
