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

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathResource implements Resource {

    private final Path path;
    private final Path root;
    private final boolean classpath;
    private final String relativePath;

    // lazy
    private byte[] bytes;
    private String text;
    private String[] lines;

    /**
     * Creates a PathResource with working directory as root.
     *
     * @param path the path
     */
    public PathResource(Path path) {
        this(path, FileUtils.WORKING_DIR.toPath(), false);
    }

    /**
     * Creates a PathResource with a custom root.
     *
     * @param path the path
     * @param root the root path for computing relative paths
     */
    public PathResource(Path path, Path root) {
        this(path, root, false);
    }

    /**
     * Creates a PathResource with custom root and classpath flag.
     *
     * @param path the path
     * @param root the root path for computing relative paths
     * @param classpath whether this is a classpath resource
     */
    public PathResource(Path path, Path root, boolean classpath) {
        // Always work with absolute normalized paths internally for consistency
        this.path = path.toAbsolutePath().normalize();
        this.root = root != null ? root.toAbsolutePath().normalize() : FileUtils.WORKING_DIR.toPath();
        this.classpath = classpath;
        this.relativePath = computeRelativePath();
    }

    private String computeRelativePath() {
        try {
            // Check if paths share the same root (Windows cross-drive check)
            if (root.getRoot() != null && path.getRoot() != null
                    && !root.getRoot().equals(path.getRoot())) {
                // Cross-drive on Windows - use absolute path
                return path.toString().replace('\\', '/');
            }

            // Check if path is under root - if so, relativize normally
            if (path.startsWith(root)) {
                return root.relativize(path).toString().replace('\\', '/');
            }

            // Path is outside root - try relativizing from current working directory
            Path cwd = FileUtils.WORKING_DIR.toPath().toAbsolutePath().normalize();
            if (path.startsWith(cwd)) {
                return cwd.relativize(path).toString().replace('\\', '/');
            }

            // Path is outside both root and cwd - use absolute path
            // (file name alone would break path resolution for templates)
            return path.toString().replace('\\', '/');
        } catch (Exception e) {
            // Fallback to absolute path
            return path.toString().replace('\\', '/');
        }
    }

    @Override
    public boolean isFile() {
        // Returns true if backed by file system (even if file doesn't exist yet)
        // This is semantically different from Files.exists() or Files.isRegularFile()
        return path != null;
    }

    @Override
    public boolean isClassPath() {
        return classpath;
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public URI getUri() {
        return path.toUri();
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public Path getRoot() {
        return root;
    }

    @Override
    public Resource resolve(String childPath) {
        // Handle classpath: prefix - delegate to Resource.path() for classpath lookup
        if (childPath.startsWith(Resource.CLASSPATH_COLON)) {
            return Resource.path(childPath);
        }
        // Handle file: prefix - delegate to Resource.path()
        if (childPath.startsWith(Resource.FILE_COLON)) {
            return Resource.path(childPath);
        }
        // Handle Windows absolute paths (e.g., C:\path\file.txt, D:/path/file.js)
        // Only check on Windows to avoid regex overhead on other platforms
        if (OsUtils.isWindows() && childPath.length() > 1 && childPath.charAt(1) == ':') {
            return Resource.path(childPath);
        }
        // Leading "/" resolves relative to working directory (root), not filesystem root
        // Users who want filesystem root should use "file:" prefix
        if (childPath.startsWith("/")) {
            Path resolved = root.resolve(childPath.substring(1));
            return new PathResource(resolved, root, classpath);
        }

        // Resolve from parent if this path is explicitly a file, otherwise from itself
        // Check: isDirectory OR has extension (heuristic for non-existent files)
        Path base;
        if (Files.isDirectory(path)) {
            base = path;  // It's a directory, resolve from it
        } else if (Files.exists(path)) {
            base = path.getParent();  // It's a file, resolve from parent
        } else {
            // Path doesn't exist yet - use heuristic: has extension = file
            String pathStr = path.toString();
            boolean hasExtension = pathStr.matches(".*\\.[a-zA-Z0-9]+$");
            base = hasExtension ? path.getParent() : path;
        }
        Path resolved = base.resolve(childPath);
        return new PathResource(resolved, root, classpath);
    }

    @Override
    public Resource getParent() {
        Path parentPath = path.getParent();
        return parentPath != null ? new PathResource(parentPath, root, classpath) : null;
    }

    @Override
    public InputStream getStream() {
        try {
            return Files.newInputStream(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open stream for: " + path, e);
        }
    }

    @Override
    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public String getText() {
        if (text == null) {
            try {
                bytes = Files.readAllBytes(path);
                text = FileUtils.toString(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read text from: " + path, e);
            }
        }
        return text;
    }

    @Override
    public String getLine(int index) {
        if (lines == null) {
            lines = getText().split("\\r?\\n");
        }
        return lines[index];
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return relativePath;
    }

}
