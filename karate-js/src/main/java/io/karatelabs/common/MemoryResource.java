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
package io.karatelabs.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class MemoryResource implements Resource {

    private static final Path SYSTEM_TEMP = Path.of(System.getProperty("java.io.tmpdir"));

    private final Path root;
    private final Path classpathRoot;
    private final String relativePath;
    private final int lineOffset;

    // exactly one of these is set at construction; the other is derived on demand.
    // Text is what an in-memory resource is nearly always read as (parse, eval), and
    // encoding it to bytes up front was pure cost on the way in: karate-config.js is
    // wrapped into one of these per scenario and nothing ever asks for its bytes.
    // Volatile because this field WAS final: an in-memory resource can be shared by
    // parallel scenarios, and an array handed over by a plain data race can be seen
    // non-null with its contents not yet visible.
    private volatile byte[] bytes;
    private String text;
    // Volatile for the same reason as bytes above, and it is the same kind of field: a lazily
    // assigned array, published by a plain write, readable as non-null with stale elements. `text`
    // needs no such guard — a String's final fields are safe to publish by a data race, an array's
    // elements are not. The path is error reporting, which is exactly where several threads
    // plausibly reach a shared resource for the first time at once.
    private volatile String[] lines;

    MemoryResource(String text) {
        this(text, (Path) null);
    }

    MemoryResource(byte[] bytes) {
        this(bytes, (Path) null);
    }

    MemoryResource(String text, Path root) {
        this.root = root != null ? root : SYSTEM_TEMP;
        this.classpathRoot = this.root;
        this.text = text;
        this.relativePath = "";
        this.lineOffset = 0;
    }

    MemoryResource(byte[] bytes, Path root) {
        this.root = root != null ? root : SYSTEM_TEMP;
        this.classpathRoot = this.root;
        this.bytes = bytes;
        this.relativePath = "";
        this.lineOffset = 0;
    }

    /**
     * Creates an in-memory resource with an explicit relative path.
     * Useful for resources loaded from JARs that need a path identity.
     *
     * @param text         the text content
     * @param relativePath the relative path (e.g., "features/test.feature")
     */
    MemoryResource(String text, String relativePath) {
        this(text, relativePath, 0, null, null);
    }

    /**
     * Creates an in-memory resource with a relative path, line offset and the host file's
     * anchors. Used for code embedded within another source file (e.g., JS inside a feature
     * file, or karate-config.js wrapped for evaluation) — carrying {@code root} /
     * {@code classpathRoot} is what makes a reference made from inside the embedded code
     * resolve against the same project the host file belongs to.
     *
     * @param text          the embedded code text
     * @param relativePath  the host file's relative path
     * @param lineOffset    0-indexed line in the host file where this code starts
     * @param root          THE project root (null = system temp)
     * @param classpathRoot the {@code classpath:}-miss fallback dir (null = root)
     */
    MemoryResource(String text, String relativePath, int lineOffset, Path root, Path classpathRoot) {
        this.root = root != null ? root : SYSTEM_TEMP;
        this.classpathRoot = classpathRoot != null ? classpathRoot : this.root;
        this.text = text;
        this.relativePath = relativePath != null ? relativePath : "";
        this.lineOffset = lineOffset;
    }

    @Override
    public String getText() {
        if (text == null) {
            text = FileUtils.toString(bytes);
        }
        return text;
    }

    /** The content as bytes — encoded from the text on first ask when this resource was built from text. */
    private byte[] bytes() {
        byte[] temp = bytes;
        if (temp == null) {
            temp = FileUtils.toBytes(text);
            bytes = temp;
        }
        return temp;
    }

    public String getLine(int index) {
        // Read the field once into a local: a second read could see a different array, and the
        // bounds check would then be against neither.
        String[] temp = lines;
        if (temp == null) {
            temp = getText().split("\\r?\\n");
            lines = temp;
        }
        int adjusted = index - lineOffset;
        if (adjusted < 0 || adjusted >= temp.length) {
            return "";
        }
        return temp[adjusted];
    }

    @Override
    public int getLineOffset() {
        return lineOffset;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean isClassPath() {
        return false;
    }

    @Override
    public boolean exists() {
        return true; // Content already in memory
    }

    @Override
    public Path getPath() {
        return null;
    }

    @Override
    public Path getRoot() {
        return root;
    }

    @Override
    public Path getClasspathRoot() {
        return classpathRoot;
    }

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public Resource resolve(String path) {
        // Handle classpath: prefix - classloader lookup, falling back to this resource's
        // classpath root in project mode (no Java classpath carries the project's files there)
        if (path.startsWith(Resource.CLASSPATH_COLON)) {
            return Resource.classpathWithRootFallback(path, root, classpathRoot);
        }
        // Handle file: prefix using Resource.path()
        if (path.startsWith(Resource.FILE_COLON)) {
            return Resource.path(path);
        }
        // A leading "/" is root-relative (the project/working root — webapp context-path style), NOT the
        // OS filesystem root: identical to PathResource.resolve. Without this strip, root.resolve("/x")
        // would discard root (Path.resolve of an absolute arg returns the arg) and leak "/x" as OS-absolute
        // — the divergence that broke a leading-"/" read() during config-eval, where the "current resource"
        // is a MemoryResource (use "file:" to force a real filesystem-absolute path).
        return new PathResource(root.resolve(Resource.stripLeadingSlashes(path)), root, false, classpathRoot);
    }

    /**
     * Materializes this in-memory resource to disk at the specified filename.
     * The file is created within the root directory.
     *
     * @param filename the filename to save as
     * @return PathResource pointing to the saved file
     */
    public PathResource materialize(String filename) {
        try {
            Path target = root.resolve(filename);
            // Ensure parent directories exist
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, bytes());
            return new PathResource(target, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to materialize resource to: " + filename, e);
        }
    }

    @Override
    public InputStream getStream() {
        return new ByteArrayInputStream(bytes());
    }

    @Override
    public String toString() {
        return getPrefixedPath();
    }

}
