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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * SPI for an ext JAR to own {@code read()} of a file type core does not parse (e.g. {@code .xlsx}).
 *
 * <p>Registered the standard ServiceLoader way — a file
 * {@code META-INF/services/io.karatelabs.core.FileReaderProvider} naming the implementation. A provider
 * claiming an extension is consulted before the built-in handling, and only a claimed extension takes a
 * {@code #fragment} ({@code read('data.xlsx#Sheet 2')}); any other path reads exactly as without providers.</p>
 */
public interface FileReaderProvider {

    /** The lower-case extensions this provider reads, without the dot ({@code xlsx}). */
    Set<String> extensions();

    /**
     * The value {@code read()} returns for {@code resource}.
     *
     * @param fragment the text after the first {@code #} following the file name, or {@code null} when absent
     */
    Object read(Resource resource, String fragment);

    /** The provider on the classpath claiming {@code extension} (any case), or {@code null}. */
    static FileReaderProvider forExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        for (FileReaderProvider p : Loaded.PROVIDERS) {
            if (p.extensions().contains(ext)) {
                return p;
            }
        }
        return null;
    }

    /**
     * {@code path} split at the first {@code #} whose preceding file name has a claimed extension:
     * {@code [path, fragment]}, or {@code null} when no provider claims it (the path is then read as is).
     */
    static String[] splitFragment(String path) {
        for (int i = path.indexOf('#'); i > 0; i = path.indexOf('#', i + 1)) {
            String file = path.substring(0, i);
            int dot = file.lastIndexOf('.');
            if (dot > file.lastIndexOf('/') && forExtension(file.substring(dot + 1)) != null) {
                return new String[]{file, path.substring(i + 1)};
            }
        }
        return null;
    }

    final class Loaded {

        static final List<FileReaderProvider> PROVIDERS = load();

        private Loaded() {
        }

        private static List<FileReaderProvider> load() {
            List<FileReaderProvider> out = new ArrayList<>();
            ServiceLoader.load(FileReaderProvider.class, FileReaderProvider.class.getClassLoader()).forEach(out::add);
            return List.copyOf(out);
        }
    }

}
