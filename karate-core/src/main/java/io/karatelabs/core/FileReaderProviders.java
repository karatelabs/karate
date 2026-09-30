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

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

/** Discovery and path splitting behind {@link FileReaderProvider}'s static entry points. */
final class FileReaderProviders {

    // keyed by the loader discovery ran through; a provider's class pins that loader (Class -> ClassLoader),
    // so a weak key alone would never clear - the value is soft, so a discarded loader goes on memory pressure
    private static final Map<ClassLoader, SoftReference<List<FileReaderProvider>>> CACHE = new WeakHashMap<>();

    private FileReaderProviders() {
    }

    static FileReaderProvider forExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        FileReaderProvider found = null;
        for (FileReaderProvider p : providers()) {
            if (p.extensions().contains(ext)) {
                if (found != null) {
                    throw new IllegalStateException("read(): extension '" + ext + "' is claimed by both "
                            + found.getClass().getName() + " and " + p.getClass().getName() + " - remove one from the classpath");
                }
                found = p;
            }
        }
        return found;
    }

    static String[] splitFragment(String path) {
        // index 0 cannot end a file name
        for (int i = path.indexOf('#', 1); i != -1; i = path.indexOf('#', i + 1)) {
            if (path.indexOf('/', i) != -1 || path.indexOf('\\', i) != -1) {
                continue; // a '#' inside a directory name
            }
            String file = path.substring(0, i);
            int dot = file.lastIndexOf('.');
            if (dot > Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) && forExtension(file.substring(dot + 1)) != null) {
                return new String[]{file, path.substring(i + 1)};
            }
        }
        return null;
    }

    private static List<FileReaderProvider> providers() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = context != null ? context : FileReaderProvider.class.getClassLoader();
        synchronized (CACHE) {
            SoftReference<List<FileReaderProvider>> ref = CACHE.get(loader);
            List<FileReaderProvider> cached = ref == null ? null : ref.get();
            if (cached != null) {
                return cached;
            }
        }
        List<FileReaderProvider> found = discover(loader); // outside the lock: loads classes, and racing calls agree
        synchronized (CACHE) {
            CACHE.put(loader, new SoftReference<>(found));
        }
        return found;
    }

    /** {@code loader}'s registrations in ServiceLoader order, then the defining loader's not already seen by class name. */
    private static List<FileReaderProvider> discover(ClassLoader loader) {
        Map<String, FileReaderProvider> byName = new LinkedHashMap<>();
        ClassLoader defining = FileReaderProvider.class.getClassLoader();
        for (ClassLoader cl : loader == defining ? List.of(loader) : Arrays.asList(loader, defining)) {
            for (FileReaderProvider p : ServiceLoader.load(FileReaderProvider.class, cl)) {
                byName.putIfAbsent(p.getClass().getName(), p);
            }
        }
        return List.copyOf(byName.values());
    }

}
