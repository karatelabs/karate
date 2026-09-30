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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The test classpath registers one provider, {@link RowsFileReaderProvider} claiming {@code rows}. */
class FileReaderProviderTest {

    @Test
    void testSplitFragmentOnlyInTheFinalPathComponent() {
        assertArrayEquals(new String[]{"data.rows", "Sheet 2"}, FileReaderProvider.splitFragment("data.rows#Sheet 2"));
        assertArrayEquals(new String[]{"dir/data.rows", "S"}, FileReaderProvider.splitFragment("dir/data.rows#S"));
        assertArrayEquals(new String[]{"dir\\data.rows", "S"}, FileReaderProvider.splitFragment("dir\\data.rows#S"));
        // a '#' followed by a separator names a directory, whatever precedes it
        assertNull(FileReaderProvider.splitFragment("archive.rows#old/notes.txt"));
        assertNull(FileReaderProvider.splitFragment("archive.rows#old\\notes.txt"));
        assertArrayEquals(new String[]{"archive.rows#old/data.rows", "S"},
                FileReaderProvider.splitFragment("archive.rows#old/data.rows#S"));
        // the extension is the file's, not a directory's
        assertNull(FileReaderProvider.splitFragment("dir.rows/notes.txt#1"));
        assertNull(FileReaderProvider.splitFragment("notes.txt#1"));
    }

    @Test
    void testSplitFragmentLeadingMultipleAndEmptyHash() {
        assertArrayEquals(new String[]{"#fixtures/data.rows", "Sheet 2"}, FileReaderProvider.splitFragment("#fixtures/data.rows#Sheet 2"));
        assertNull(FileReaderProvider.splitFragment("#data.rows"));
        assertArrayEquals(new String[]{"data.rows", "a#b"}, FileReaderProvider.splitFragment("data.rows#a#b"));
        assertArrayEquals(new String[]{"data.rows", ""}, FileReaderProvider.splitFragment("data.rows#"));
        assertArrayEquals(new String[]{"data.rows", "S.feature@tag"}, FileReaderProvider.splitFragment("data.rows#S.feature@tag"));
        assertNull(FileReaderProvider.splitFragment("data.rows"));
        assertNull(FileReaderProvider.splitFragment(""));
    }

    @Test
    void testForExtensionIsCaseInsensitive() {
        assertTrue(FileReaderProvider.forExtension("ROWS") instanceof RowsFileReaderProvider);
        assertNull(FileReaderProvider.forExtension("cells"));
        assertNull(FileReaderProvider.forExtension(""));
        assertNull(FileReaderProvider.forExtension(null));
    }

    @Test
    void testProviderRegisteredOnlyInTheContextClassLoaderIsDiscovered(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("grid.cells"), "a,b");
        Files.writeString(tempDir.resolve("data.rows"), "x\n");
        KarateJs context = new KarateJs(Resource.path(tempDir.toString()));
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader child = childLoader(tempDir, Set.of(ChildOnlyFileReaderProvider.class.getName()))) {
            thread.setContextClassLoader(child);
            context.engine.eval("var grid = read('grid.cells#Q1');");
            assertEquals(Map.of("cells", "a,b", "fragment", "Q1"), context.engine.get("grid"));
            assertEquals(child, FileReaderProvider.forExtension("cells").getClass().getClassLoader());
            // the parent's registration is visible through the child too
            assertTrue(FileReaderProvider.forExtension("rows") instanceof RowsFileReaderProvider);
        } finally {
            thread.setContextClassLoader(original);
        }
        // the child's registration never reached the parent loader's cache: .cells is unclaimed text again
        assertNull(FileReaderProvider.forExtension("cells"));
        context.engine.eval("var plain = read('grid.cells');");
        assertEquals("a,b", context.engine.get("plain"));
        assertThrows(RuntimeException.class, () -> context.engine.eval("read('grid.cells#Q1')"));
    }

    @Test
    void testTwoProvidersClaimingOneExtensionFailTheRead(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("data.rows"), "x\n");
        Files.writeString(tempDir.resolve("notes.txt"), "still readable");
        KarateJs context = new KarateJs(Resource.path(tempDir.toString()));
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader child = childLoader(tempDir, Set.of(DuplicateRowsFileReaderProvider.class.getName()))) {
            thread.setContextClassLoader(child);
            RuntimeException e = assertThrows(RuntimeException.class, () -> context.engine.eval("read('data.rows')"));
            assertTrue(e.getMessage().contains(RowsFileReaderProvider.class.getName()), e.getMessage());
            assertTrue(e.getMessage().contains(DuplicateRowsFileReaderProvider.class.getName()), e.getMessage());
            context.engine.eval("var notes = read('notes.txt');");
            assertEquals("still readable", context.engine.get("notes"));
        } finally {
            thread.setContextClassLoader(original);
        }
        assertTrue(FileReaderProvider.forExtension("rows") instanceof RowsFileReaderProvider);
    }

    /**
     * A child of the test class loader that defines {@code names} itself (from the parent's class bytes) and
     * registers them in its own {@code META-INF/services} file: core's loader sees neither.
     */
    private static URLClassLoader childLoader(Path dir, Set<String> names) throws IOException {
        Path services = dir.resolve("META-INF/services/" + FileReaderProvider.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, String.join("\n", names) + "\n");
        ClassLoader parent = FileReaderProviderTest.class.getClassLoader();
        return new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!names.contains(name)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (InputStream in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            byte[] bytes = in.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    return loaded;
                }
            }
        };
    }

}
