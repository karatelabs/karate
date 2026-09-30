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
package io.karatelabs.core;

import io.karatelabs.common.Resource;

import java.util.Set;

/**
 * SPI for an ext JAR to own {@code read()} of a file type core does not parse (e.g. {@code .xlsx}).
 *
 * <p>Registered the standard ServiceLoader way — a file
 * {@code META-INF/services/io.karatelabs.core.FileReaderProvider} naming the implementation. Discovery runs
 * through the thread context class loader (the defining loader when unset, and as a fallback), once per loader;
 * a provider may therefore be instantiated more than once per JVM. A provider claiming an extension is
 * consulted before the built-in handling; two providers claiming one extension make {@code read()} of that
 * extension fail naming both — ServiceLoader order is unspecified, so there is no silent precedence.</p>
 *
 * <p>Only a claimed extension takes a {@code #fragment}: the path is split at the first {@code #} that sits in
 * its final component (no {@code /} or {@code \} after it, never index 0) and whose preceding file name has a
 * claimed extension — {@code read('data.xlsx#Sheet 2')}, {@code read('#in/data.xlsx#a#b')} (fragment
 * {@code a#b}). A {@code .feature@} selector before the {@code #} wins ({@code suite.feature@tag.xlsx#S} is a
 * feature call); after it, it is fragment text. Any other path, {@code archive.xlsx#old/notes.txt} included,
 * reads exactly as without providers.</p>
 *
 * <p>{@link #read} may be called concurrently, so an implementation is thread-safe; the value it returns is
 * owned by the caller, who may mutate it — return fresh values, never a shared or cached structure.</p>
 */
public interface FileReaderProvider {

    /** The lower-case extensions this provider reads, without the dot ({@code xlsx}). */
    Set<String> extensions();

    /**
     * The value {@code read()} returns for {@code resource}.
     *
     * @param fragment the text after the {@code #} ({@code ""} when nothing follows it), or {@code null} without one
     */
    Object read(Resource resource, String fragment);

    /**
     * The provider claiming {@code extension} (any case), or {@code null}.
     *
     * @throws IllegalStateException when two providers claim it
     */
    static FileReaderProvider forExtension(String extension) {
        return FileReaderProviders.forExtension(extension);
    }

    /**
     * {@code path} split into {@code [file, fragment]} per the rule above, or {@code null} when no provider
     * claims it (the path is then read as is).
     */
    static String[] splitFragment(String path) {
        return FileReaderProviders.splitFragment(path);
    }

}
