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
    private final byte[] bytes;
    private final String relativePath;
    private final int lineOffset;

    private String text;
    private String[] lines;

    MemoryResource(String text) {
        this(text, (Path) null);
    }

    MemoryResource(byte[] bytes) {
        this(bytes, (Path) null);
    }

    MemoryResource(String text, Path root) {
        this.root = root != null ? root : SYSTEM_TEMP;
        this.text = text;
        this.bytes = FileUtils.toBytes(text);
        this.relativePath = "";
        this.lineOffset = 0;
    }

    MemoryResource(byte[] bytes, Path root) {
        this.root = root != null ? root : SYSTEM_TEMP;
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
        this.root = SYSTEM_TEMP;
        this.text = text;
        this.bytes = FileUtils.toBytes(text);
        this.relativePath = relativePath != null ? relativePath : "";
        this.lineOffset = 0;
    }

    /**
     * Creates an in-memory resource with a relative path and line offset.
     * Used for code embedded within another source file (e.g., JS inside a feature file).
     *
     * @param text         the embedded code text
     * @param relativePath the host file's relative path
     * @param lineOffset   0-indexed line in the host file where this code starts
     */
    MemoryResource(String text, String relativePath, int lineOffset) {
        this.root = SYSTEM_TEMP;
        this.text = text;
        this.bytes = FileUtils.toBytes(text);
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

    public String getLine(int index) {
        if (lines == null) {
            lines = getText().split("\\r?\\n");
        }
        int adjusted = index - lineOffset;
        if (adjusted < 0 || adjusted >= lines.length) {
            return "";
        }
        return lines[adjusted];
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
    public URI getUri() {
        return null;
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public Resource resolve(String path) {
        // Handle classpath: and file: prefixes using Resource.path()
        if (path.startsWith(Resource.CLASSPATH_COLON) || path.startsWith(Resource.FILE_COLON)) {
            return Resource.path(path);
        }
        return new PathResource(root.resolve(path), root);
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
            Files.write(target, bytes);
            return new PathResource(target, root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to materialize resource to: " + filename, e);
        }
    }

    @Override
    public InputStream getStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public String toString() {
        return getPrefixedPath();
    }

}
