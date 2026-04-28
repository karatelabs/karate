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
package io.karatelabs.core;

import io.karatelabs.cli.RunCommand;
import io.karatelabs.output.LogContext;
import io.karatelabs.output.LogLevel;
import io.karatelabs.process.ProcessBuilder;
import org.slf4j.Logger;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Applies system-property and environment-variable overrides to a {@link Runner.Builder}
 * at the start of {@code parallel()}. This preserves the v1 "CI override" contract where
 * Maven/Gradle users pass {@code -Dkarate.options="--tags @smoke"} to override a Runner
 * class's hard-coded values without a code change.
 *
 * <p>Precedence (highest first):
 * <ol>
 *   <li>{@code -Dkarate.options="..."} system property</li>
 *   <li>{@code KARATE_OPTIONS} environment variable (fallback)</li>
 *   <li>Individual sysprops: {@code karate.env}, {@code karate.config.dir}, with env var
 *       fallbacks ({@code KARATE_ENV}, {@code KARATE_CONFIG_DIR})</li>
 *   <li>Builder values set programmatically</li>
 *   <li>{@code karate-pom.json} values (applied upstream by {@code RunCommand})</li>
 *   <li>Defaults</li>
 * </ol>
 *
 * <p>Implementation reads each override and applies it via the Builder's package-private
 * setters, reusing the v2 CLI grammar through {@link CommandLine#populateCommand} on a
 * {@link RunCommand} instance.
 */
public final class KarateOptionsHandler {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    public static final String PROP_OPTIONS = "karate.options";
    public static final String PROP_ENV = "karate.env";
    public static final String PROP_CONFIG_DIR = "karate.config.dir";
    public static final String ENV_OPTIONS = "KARATE_OPTIONS";
    public static final String ENV_ENV = "KARATE_ENV";
    public static final String ENV_CONFIG_DIR = "KARATE_CONFIG_DIR";

    // Overridable for tests
    static Function<String, String> sysPropReader = System::getProperty;
    static Function<String, String> envVarReader = System::getenv;

    private KarateOptionsHandler() {
    }

    /**
     * Apply sysprop/env overrides to the given Builder. Returns the effective thread count:
     * if {@code karate.options} specified {@code --threads N}, returns {@code N}; otherwise
     * returns the {@code threadCount} argument unchanged.
     */
    public static int apply(Runner.Builder builder, int threadCount) {
        // Step 1: individual sysprops (lower precedence than karate.options)
        applyIndividualEnv(builder);
        applyIndividualConfigDir(builder);

        // Step 2: karate.options sysprop / env var (higher precedence — applied last)
        String source = null;
        String raw = trimToNull(sysPropReader.apply(PROP_OPTIONS));
        if (raw != null) {
            source = "system property '" + PROP_OPTIONS + "'";
        } else {
            raw = trimToNull(envVarReader.apply(ENV_OPTIONS));
            if (raw != null) {
                source = "environment variable '" + ENV_OPTIONS + "'";
            }
        }
        if (raw == null) {
            return threadCount;
        }
        logger.info("using {}: {}", source, raw);
        return parseAndApplyOptions(builder, raw, threadCount);
    }

    private static void applyIndividualEnv(Runner.Builder builder) {
        String value = trimToNull(sysPropReader.apply(PROP_ENV));
        String source = null;
        if (value != null) {
            source = "system property '" + PROP_ENV + "'";
        } else {
            value = trimToNull(envVarReader.apply(ENV_ENV));
            if (value != null) {
                source = "environment variable '" + ENV_ENV + "'";
            }
        }
        if (value != null) {
            logger.info("using {}: {}", source, value);
            if (logger.isDebugEnabled()) {
                logger.debug("karate.env override: '{}' (Builder) -> '{}' ({})",
                        builder.getEnv(), value, source);
            }
            builder.setEnv(value);
        }
    }

    private static void applyIndividualConfigDir(Runner.Builder builder) {
        String value = trimToNull(sysPropReader.apply(PROP_CONFIG_DIR));
        String source = null;
        if (value != null) {
            source = "system property '" + PROP_CONFIG_DIR + "'";
        } else {
            value = trimToNull(envVarReader.apply(ENV_CONFIG_DIR));
            if (value != null) {
                source = "environment variable '" + ENV_CONFIG_DIR + "'";
            }
        }
        if (value != null) {
            logger.info("using {}: {}", source, value);
            if (logger.isDebugEnabled()) {
                logger.debug("karate.config.dir override: '{}' (Builder) -> '{}' ({})",
                        builder.getConfigDir(), value, source);
            }
            builder.setConfigDir(value);
        }
    }

    /**
     * Parse the options string using the v2 CLI grammar and apply it to the Builder.
     * Package-private for direct testing.
     */
    static int parseAndApplyOptions(Runner.Builder builder, String raw, int threadCount) {
        List<String> tokens = ProcessBuilder.tokenize(raw);
        String[] argv = tokens.toArray(new String[0]);
        RunCommand parsed = new RunCommand();
        try {
            new CommandLine(parsed).parseArgs(argv);
        } catch (CommandLine.ParameterException e) {
            logger.warn("invalid karate.options ignored: {}", e.getMessage());
            return threadCount;
        }

        // Collect applied summary for the INFO line
        List<String> summary = new ArrayList<>();

        // --- Paths (positional + -P) — REPLACE (v1 parity) ---
        List<String> combinedPaths = new ArrayList<>();
        if (parsed.getPaths() != null) combinedPaths.addAll(parsed.getPaths());
        if (parsed.getPathOptions() != null) combinedPaths.addAll(parsed.getPathOptions());
        if (!combinedPaths.isEmpty()) {
            List<String> before = new ArrayList<>(builder.getPaths());
            builder.clearPaths();
            for (String p : combinedPaths) {
                builder.path(p);
            }
            summary.add("paths=" + combinedPaths);
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: paths: {} (Builder) -> {} (karate.options)",
                        before, combinedPaths);
            }
        }

        // --- Tags — REPLACE ---
        if (parsed.getTags() != null && !parsed.getTags().isEmpty()) {
            List<String> before = builder.getTags() == null ? null : new ArrayList<>(builder.getTags());
            builder.setTags(new ArrayList<>(parsed.getTags()));
            summary.add("tags=" + parsed.getTags());
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: tags: {} (Builder) -> {} (karate.options)",
                        before, parsed.getTags());
            }
        }

        // --- Env ---
        if (parsed.getEnv() != null) {
            String before = builder.getEnv();
            builder.setEnv(parsed.getEnv());
            summary.add("env=" + parsed.getEnv());
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: env: '{}' (Builder) -> '{}' (karate.options)",
                        before, parsed.getEnv());
            }
        }

        // --- Threads — override the local param ---
        int effectiveThreads = threadCount;
        if (parsed.getThreads() != null) {
            effectiveThreads = parsed.getThreads();
            summary.add("threads=" + parsed.getThreads());
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: threads: {} (parallel param) -> {} (karate.options)",
                        threadCount, parsed.getThreads());
            }
        }

        // --- Scenario name (stub today; see issue #2522) ---
        if (parsed.getScenarioName() != null) {
            builder.setScenarioName(parsed.getScenarioName());
            summary.add("name=" + parsed.getScenarioName());
        }

        // --- Output dir (wins over karate.output.dir sysprop) ---
        if (parsed.getOutputDir() != null) {
            Path before = builder.getOutputDir();
            builder.setOutputDir(Path.of(parsed.getOutputDir()));
            summary.add("output=" + parsed.getOutputDir());
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: outputDir: '{}' (Builder) -> '{}' (karate.options)",
                        before, parsed.getOutputDir());
            }
        }

        // --- Config dir ---
        if (parsed.getConfigDir() != null) {
            String before = builder.getConfigDir();
            builder.setConfigDir(parsed.getConfigDir());
            summary.add("configDir=" + parsed.getConfigDir());
            if (logger.isDebugEnabled()) {
                logger.debug("karate.options override: configDir: '{}' (Builder) -> '{}' (karate.options)",
                        before, parsed.getConfigDir());
            }
        }

        // --- Dry run (Boolean, nullable — only apply if user specified the flag) ---
        if (parsed.isDryRun() != null) {
            builder.setDryRun(parsed.isDryRun());
            summary.add("dryRun=" + parsed.isDryRun());
        }

        // --- Formats (--format / -f) — only apply if the user set something ---
        List<String> parsedFormats = parsed.getFormats();
        if (parsedFormats != null && !parsedFormats.isEmpty()) {
            builder.setOutputHtmlReport(RunCommand.isFormatEnabled(parsedFormats, "html", true));
            builder.setOutputCucumberJson(RunCommand.isFormatEnabled(parsedFormats, "cucumber:json", false));
            builder.setOutputJunitXml(RunCommand.isFormatEnabled(parsedFormats, "junit:xml", false));
            builder.setOutputJsonLines(RunCommand.isFormatEnabled(parsedFormats, "karate:jsonl", false));
            summary.add("formats=" + parsedFormats);
        }

        // --- Log levels ---
        if (parsed.getLogReport() != null) {
            try {
                builder.setLogLevel(LogLevel.valueOf(parsed.getLogReport().toUpperCase()));
                summary.add("logReport=" + parsed.getLogReport());
            } catch (IllegalArgumentException ex) {
                logger.warn("invalid karate.options log-report ignored: {}", parsed.getLogReport());
            }
        }
        if (parsed.getLogConsole() != null) {
            io.karatelabs.output.LogContext.setRuntimeLogLevel(parsed.getLogConsole());
            summary.add("logConsole=" + parsed.getLogConsole());
        }

        if (!summary.isEmpty()) {
            logger.info("karate.options applied: {}", String.join(", ", summary));
        }

        return effectiveThreads;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
