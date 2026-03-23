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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resource implementation for HTTP/HTTPS URLs.
 * Content is fetched once and cached in memory.
 * Preserves the original URL for reference (getUri(), getSimpleName()).
 */
public class UrlResource implements Resource {

    private static final Path SYSTEM_TEMP = Path.of(System.getProperty("java.io.tmpdir"));

    private final URL url;
    private final URI uri;
    private final Path root;
    private final byte[] bytes;

    private String text;
    private String[] lines;

    UrlResource(URL url, byte[] bytes) {
        this(url, bytes, null);
    }

    UrlResource(URL url, byte[] bytes, Path root) {
        this.url = url;
        this.uri = toUri(url);
        this.root = root != null ? root : SYSTEM_TEMP;
        this.bytes = bytes;
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the original URL this resource was loaded from.
     */
    public URL getUrl() {
        return url;
    }

    @Override
    public String getText() {
        if (text == null) {
            text = FileUtils.toString(bytes);
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
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean isClassPath() {
        return false;
    }

    @Override
    public boolean exists() {
        return true; // Content already loaded in constructor
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
        return uri;
    }

    @Override
    public String getRelativePath() {
        if (uri != null && uri.getPath() != null) {
            String path = uri.getPath();
            // Remove leading slash for consistency
            return path.startsWith("/") ? path.substring(1) : path;
        }
        return "";
    }

    @Override
    public Resource resolve(String path) {
        // Handle classpath: prefix - delegate to Resource.path() for classpath lookup
        if (path.startsWith(Resource.CLASSPATH_COLON)) {
            return Resource.path(path);
        }
        // For URL resources, resolve creates a new URL relative to this one
        // Note: leading "/" is URL-relative (e.g., https://host/path), not working dir
        try {
            URI baseUri = url.toURI();
            URI resolvedUri = baseUri.resolve(path);
            URL resolvedUrl = resolvedUri.toURL();
            return Resource.from(resolvedUrl, root);
        } catch (Exception e) {
            // Fall back to path-based resolution from root
            return new PathResource(root.resolve(path), root);
        }
    }

    /**
     * Materializes this URL resource to disk at the specified filename.
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
        return url != null ? url.toString() : getPrefixedPath();
    }

}
