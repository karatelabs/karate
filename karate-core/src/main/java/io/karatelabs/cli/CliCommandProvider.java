/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import java.util.List;

/**
 * SPI for ext JARs to contribute {@code karate} CLI subcommands (e.g. {@code karate serve}).
 *
 * <p>Implementations are discovered by {@link io.karatelabs.Main} via {@link java.util.ServiceLoader}
 * from the flat classpath the launcher composes (core jar + {@code ext/*.jar}). Each contributed
 * command becomes a first-class subcommand of {@code karate}, indistinguishable from the built-in
 * {@code run}/{@code mock}/{@code clean} — so it parses its own flags, shows up in help, and is not
 * hijacked by the legacy "bare path → run" default.</p>
 *
 * <p>Registration: a provider is published the standard ServiceLoader way — a file
 * {@code META-INF/services/io.karatelabs.cli.CliCommandProvider} on the ext JAR containing the
 * implementation's fully-qualified class name.</p>
 *
 * <p>Each object returned by {@link #commands()} must be a <a href="https://picocli.info">picocli</a>
 * {@code @Command}-annotated {@code Callable<Integer>} instance (picocli is a core dependency, so it
 * is on the ext's compile classpath). {@code Main} registers it with
 * {@code CommandLine#addSubcommand(Object)}.</p>
 */
public interface CliCommandProvider {

    /**
     * The subcommands this provider contributes. Each must be a picocli {@code @Command}-annotated
     * {@code Callable<Integer>} (the {@code @Command(name=...)} supplies the subcommand name).
     */
    List<Object> commands();

}
