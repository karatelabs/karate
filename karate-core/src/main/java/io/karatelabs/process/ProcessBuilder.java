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
package io.karatelabs.process;

import io.karatelabs.common.OsUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for ProcessConfig. Provides fluent API for process configuration.
 */
public class ProcessBuilder {

    private List<String> args = new ArrayList<>();
    private Path workingDir;
    private Map<String, String> env = new HashMap<>();
    private boolean useShell = false;
    private boolean redirectErrorStream = true;
    private Duration timeout;
    private Consumer<String> listener;
    private Consumer<String> errorListener;
    private boolean logToContext = true;

    private ProcessBuilder() {
    }

    public static ProcessBuilder create() {
        return new ProcessBuilder();
    }

    /**
     * Tokenize a command line string into arguments.
     * Handles single quotes, double quotes, backslash escapes, and whitespace.
     * <p>
     * Behavior mirrors POSIX shell tokenization:
     * - Single quotes preserve literal content (no escape processing)
     * - Double quotes preserve content (backslash escapes work inside)
     * - Backslash outside quotes escapes any character
     * - Adjacent quoted/unquoted segments merge into single token
     */
    public static List<String> tokenize(String command) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            // Backslash escapes (only outside single quotes)
            if (c == '\\' && !inSingleQuote) {
                escaped = true;
                continue;
            }

            // Single quote toggle (only outside double quotes)
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            // Double quote toggle (only outside single quotes)
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            // Whitespace splits tokens (only outside quotes)
            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        // Add final token if present
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    /**
     * Wrap command with shell for shell features (pipes, env expansion, etc.)
     */
    public static List<String> wrapWithShell(List<String> args) {
        List<String> wrapped = new ArrayList<>();
        if (OsUtils.isWindows()) {
            wrapped.add("cmd");
            wrapped.add("/c");
        } else {
            wrapped.add("sh");
            wrapped.add("-c");
        }
        wrapped.add(String.join(" ", args));
        return wrapped;
    }

    // ========== Command Configuration ==========

    public ProcessBuilder line(String command) {
        this.args = tokenize(command);
        return this;
    }

    public ProcessBuilder args(String... args) {
        this.args = new ArrayList<>(List.of(args));
        return this;
    }

    public ProcessBuilder args(List<String> args) {
        this.args = new ArrayList<>(args);
        return this;
    }

    // ========== Environment Configuration ==========

    public ProcessBuilder workingDir(Path dir) {
        this.workingDir = dir;
        return this;
    }

    public ProcessBuilder workingDir(String dir) {
        this.workingDir = dir != null ? Path.of(dir) : null;
        return this;
    }

    public ProcessBuilder env(Map<String, String> env) {
        this.env = new HashMap<>(env);
        return this;
    }

    public ProcessBuilder env(String key, String value) {
        this.env.put(key, value);
        return this;
    }

    public ProcessBuilder useShell(boolean useShell) {
        this.useShell = useShell;
        return this;
    }

    public ProcessBuilder redirectErrorStream(boolean redirect) {
        this.redirectErrorStream = redirect;
        return this;
    }

    // ========== Async Configuration ==========

    public ProcessBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public ProcessBuilder timeoutMillis(long millis) {
        this.timeout = Duration.ofMillis(millis);
        return this;
    }

    public ProcessBuilder listener(Consumer<String> listener) {
        this.listener = listener;
        return this;
    }

    public ProcessBuilder errorListener(Consumer<String> errorListener) {
        this.errorListener = errorListener;
        return this;
    }

    public ProcessBuilder logToContext(boolean log) {
        this.logToContext = log;
        return this;
    }

    // ========== Build ==========

    public ProcessConfig build() {
        List<String> finalArgs = useShell ? wrapWithShell(args) : args;
        return new ProcessConfig(
                finalArgs, workingDir, env, useShell,
                redirectErrorStream, timeout, listener, errorListener, logToContext
        );
    }

    // ========== Factory from Map (for JS bridge) ==========

    @SuppressWarnings("unchecked")
    public static ProcessBuilder fromMap(Map<String, Object> options) {
        ProcessBuilder builder = create();

        // Command: args or line
        if (options.containsKey("args")) {
            Object argsObj = options.get("args");
            if (argsObj instanceof List) {
                builder.args((List<String>) argsObj);
            }
        } else if (options.containsKey("line")) {
            builder.line((String) options.get("line"));
        }

        // Environment
        if (options.containsKey("workingDir")) {
            builder.workingDir((String) options.get("workingDir"));
        }
        if (options.containsKey("env")) {
            builder.env((Map<String, String>) options.get("env"));
        }

        // Flags
        if (options.containsKey("useShell")) {
            Object val = options.get("useShell");
            builder.useShell(val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString()));
        }
        if (options.containsKey("redirectErrorStream")) {
            Object val = options.get("redirectErrorStream");
            builder.redirectErrorStream(val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString()));
        }

        // Timeout
        if (options.containsKey("timeout")) {
            Object timeout = options.get("timeout");
            if (timeout instanceof Number) {
                builder.timeoutMillis(((Number) timeout).longValue());
            }
        }

        // logToContext
        if (options.containsKey("logToContext")) {
            Object val = options.get("logToContext");
            builder.logToContext(val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString()));
        }

        return builder;
    }

}
