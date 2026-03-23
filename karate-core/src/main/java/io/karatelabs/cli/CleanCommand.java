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

import io.karatelabs.common.FileUtils;
import io.karatelabs.output.Console;
import io.karatelabs.core.KaratePom;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The 'clean' subcommand for cleaning Karate output directories.
 * <p>
 * Usage examples:
 * <pre>
 * # Clean default output directory (honors karate-pom.json if present)
 * karate clean
 *
 * # Clean specific output directory
 * karate clean -o custom-reports
 *
 * # Clean with working directory
 * karate clean -w /path/to/project
 *
 * # Clean ignoring karate-pom.json
 * karate clean --no-pom
 * </pre>
 */
@Command(
        name = "clean",
        mixinStandardHelpOptions = true,
        description = "Clean output directories"
)
public class CleanCommand implements Callable<Integer> {

    @Option(
            names = {"-o", "--output"},
            description = "Output directory to clean (default: from karate-pom.json or target/karate-reports)"
    )
    String outputDir;

    @Option(
            names = {"-w", "--workdir"},
            description = "Working directory for relative path resolution (default: current directory)"
    )
    String workingDir;

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

    // Loaded pom config
    private KaratePom pom;

    @Override
    public Integer call() {
        // Load pom (unless --no-pom)
        if (!noPom) {
            loadPom();
        }

        String effectiveOutputDir = resolveOutputDir();

        // outputDir is NOT relative to workingDir (consistent with Suite behavior)
        // workingDir is only for resolving feature paths and config files
        Path output = Path.of(effectiveOutputDir);

        int cleanedCount = 0;

        // Clean main output directory
        String outputDirName = output.getFileName().toString();
        if (Files.exists(output)) {
            try {
                deleteDirectory(output.toFile());
                Console.println(Console.info("Cleaned: " + outputDirName));
                cleanedCount++;
            } catch (Exception e) {
                Console.println(Console.fail("Failed to clean: " + outputDirName + " - " + e.getMessage()));
                return 1;
            }
        }

        // Clean backup directories (e.g., karate-reports_20260125_135442)
        // These are created by Suite.backupReportDirIfExists() with pattern: <outputDir>_<timestamp>
        Path parent = output.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        String backupPrefix = outputDirName + "_";

        try {
            File parentDir = parent.toFile();
            File[] backupDirs = parentDir.listFiles((dir, name) ->
                    name.startsWith(backupPrefix) && new File(dir, name).isDirectory());

            if (backupDirs != null) {
                for (File backupDir : backupDirs) {
                    try {
                        deleteDirectory(backupDir);
                        Console.println(Console.info("Cleaned: " + backupDir.getName()));
                        cleanedCount++;
                    } catch (Exception e) {
                        Console.println(Console.warn("Failed to clean: " + backupDir.getName()));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors when scanning for backups
        }

        // Clean karate-temp directory (Chrome user data, caches, etc.)
        // Located at <buildDir>/karate-temp (sibling to karate-reports)
        Path tempDir = parent.resolve("karate-temp");
        if (Files.exists(tempDir) && Files.isDirectory(tempDir)) {
            try {
                deleteDirectory(tempDir.toFile());
                Console.println(Console.info("Cleaned: karate-temp"));
                cleanedCount++;
            } catch (Exception e) {
                Console.println(Console.warn("Failed to clean: karate-temp"));
            }
        }

        if (cleanedCount == 0) {
            Console.println(Console.info("Nothing to clean"));
        }

        return 0;
    }

    private void loadPom() {
        String file = pomFile != null ? pomFile : RunCommand.DEFAULT_POM_FILE;
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
            } catch (Exception e) {
                // Ignore pom errors for clean command
            }
        }
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

    private String resolveOutputDir() {
        if (outputDir != null) {
            return outputDir;
        }
        if (pom != null) {
            String pomOutputDir = pom.getOutput().getDir();
            if (pomOutputDir != null && !pomOutputDir.isEmpty()) {
                return pomOutputDir;
            }
        }
        // Default: detect build tool (Gradle uses "build", Maven uses "target")
        return FileUtils.getBuildDir() + "/karate-reports";
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

    public String getOutputDir() {
        return outputDir;
    }

    public String getWorkingDir() {
        return workingDir;
    }

}
