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
import java.net.URL;
import java.nio.file.Path;

public interface Resource {

    String CLASSPATH_COLON = "classpath:";
    String FILE_COLON = "file:";
    String THIS_COLON = "this:";

    /**
     * Returns true if this resource is backed by the file system.
     * PathResource returns true, MemoryResource returns false.
     */
    boolean isFile();

    /**
     * Returns true if this resource is in-memory only (not backed by file system).
     * Inverse of isFile() - useful for error messages and debugging.
     */
    default boolean isInMemory() {
        return !isFile();
    }

    /**
     * Returns true if this is a regular file on the local filesystem.
     * This excludes JAR resources and in-memory resources.
     * Use this when you need to know if the resource can be modified on disk.
     *
     * @return true if this is a local file (file:// scheme)
     */
    default boolean isLocalFile() {
        if (!isFile()) {
            return false;
        }
        try {
            URI uri = getUri();
            return uri != null && "file".equals(uri.getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if this resource is inside a JAR or ZIP file.
     * JAR resources are read-only and cannot be modified.
     *
     * @return true if this is a JAR resource (jar: scheme)
     */
    default boolean isJarResource() {
        if (!isFile()) {
            return false;
        }
        try {
            URI uri = getUri();
            return uri != null && "jar".equals(uri.getScheme());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if this resource was loaded from the classpath.
     */
    boolean isClassPath();

    /**
     * Returns true if this resource exists and can be read.
     * For PathResource, checks if the file exists on disk.
     * For UrlResource and MemoryResource, always returns true since content is already loaded.
     */
    boolean exists();

    URI getUri();

    /**
     * Returns the Path representation of this resource if it's file-based.
     * Returns null for in-memory resources.
     *
     * @return Path object or null
     */
    Path getPath();

    /**
     * Returns the root path used for relative path computation.
     * For PathResource, this is the configured root (or working directory).
     * For MemoryResource, this is where the resource would be materialized (or system temp).
     *
     * @return root Path, never null (falls back to system temp)
     */
    Path getRoot();

    /**
     * Computes the relative path from a given root to this resource.
     * Only works for file-based resources.
     * Falls back to absolute path if relativization fails (e.g., cross-drive on Windows).
     *
     * @param root the root path to compute relative path from
     * @return relative path string with forward slashes, or absolute path if relativization fails, or null for in-memory resources
     */
    default String getRelativePathFrom(Path root) {
        if (!isFile() || root == null) {
            return null;
        }
        try {
            Path thisPath = getPath();
            if (thisPath == null) {
                return null;
            }

            // Normalize paths
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedThis = thisPath.toAbsolutePath().normalize();

            // Check if they share the same root (Windows cross-drive check)
            if (normalizedRoot.getRoot() != null && normalizedThis.getRoot() != null
                    && !normalizedRoot.getRoot().equals(normalizedThis.getRoot())) {
                // Fallback to absolute path for cross-drive
                return normalizedThis.toString().replace('\\', '/');
            }

            String relativePath = normalizedRoot.relativize(normalizedThis).toString();
            return relativePath.replace('\\', '/');
        } catch (Exception e) {
            // Fallback to absolute path if relativization fails
            Path thisPath = getPath();
            return thisPath != null ? thisPath.toAbsolutePath().normalize().toString().replace('\\', '/') : null;
        }
    }

    Resource resolve(String path);

    /**
     * Returns the parent of this resource, or null if no parent exists.
     * For PathResource, preserves root and classpath context.
     * For MemoryResource, returns null.
     *
     * @return parent Resource or null
     */
    default Resource getParent() {
        return null;
    }

    InputStream getStream();

    String getRelativePath();

    String getText();

    String getLine(int index);

    /**
     * Returns the line offset for embedded code (e.g., JS inside a feature file).
     * The lexer uses this as the starting line number so tokens get absolute line numbers.
     * Default is 0 (no offset).
     */
    default int getLineOffset() {
        return 0;
    }

    default long getLastModified() {
        if (isFile()) {
            try {
                Path path = getPath();
                if (path != null) {
                    return java.nio.file.Files.getLastModifiedTime(path).toMillis();
                }
            } catch (Exception e) {
                // Fall through
            }
        }
        try {
            URI uri = getUri();
            if (uri != null) {
                return uri.toURL().openConnection().getLastModified();
            }
        } catch (Exception e) {
            // Fall through
        }
        return 0;
    }

    default String getPackageQualifiedName() {
        String path = getRelativePath();
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.endsWith(".feature")) {
            path = path.substring(0, path.length() - 8);
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path.replace('/', '.').replaceAll("\\.[.]+", ".");
    }

    default String getExtension() {
        URI uri = getUri();
        if (uri == null) {
            return "";
        }
        String path = uri.getPath();
        int pos = path.lastIndexOf('.');
        if (pos == -1 || pos == path.length() - 1) {
            return "";
        }
        return path.substring(pos + 1);
    }

    /**
     * Returns just the file name (last path segment) of this resource.
     * Works for both file-based and URL-based resources.
     *
     * @return the simple file name, or empty string if unavailable
     */
    default String getSimpleName() {
        URI uri = getUri();
        if (uri == null) {
            return "";
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return "";
        }
        // Remove trailing slash if present
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int pos = path.lastIndexOf('/');
        return pos == -1 ? path : path.substring(pos + 1);
    }

    default String getFileNameWithoutExtension() {
        String path = getRelativePath();
        int pos = path.lastIndexOf('.');
        if (pos == -1) {
            return path;
        } else {
            return path.substring(0, pos);
        }
    }

    default String getPrefixedPath() {
        return isClassPath() ? CLASSPATH_COLON + getRelativePath() : getRelativePath();
    }

    /**
     * Computes the relative path from this resource to another resource.
     * Only works for file-based resources.
     * Falls back to absolute path if relativization fails (e.g., cross-drive on Windows).
     * <p>
     * Path separators are normalized to forward slashes (/) for cross-platform consistency.
     *
     * @param other the target resource
     * @return relative path string with forward slashes, or absolute path if relativization fails, or null for in-memory resources
     */
    default String getRelativePathTo(Resource other) {
        if (!this.isFile() || !other.isFile()) {
            return null; // Only works for file-based resources
        }
        try {
            Path thisPath = this.getPath();
            Path otherPath = other.getPath();

            if (thisPath == null || otherPath == null) {
                return null;
            }

            // Normalize to absolute paths
            thisPath = thisPath.toAbsolutePath().normalize();
            otherPath = otherPath.toAbsolutePath().normalize();

            // Check if they share the same root (critical for Windows cross-drive)
            if (thisPath.getRoot() != null && otherPath.getRoot() != null
                    && !thisPath.getRoot().equals(otherPath.getRoot())) {
                // Fallback to absolute path for cross-drive
                return otherPath.toString().replace('\\', '/');
            }

            String relativePath = thisPath.relativize(otherPath).toString();
            // Normalize to forward slashes for cross-platform consistency
            return relativePath.replace('\\', '/');
        } catch (Exception e) {
            // Fallback to absolute path if relativization fails
            Path otherPath = other.getPath();
            return otherPath != null ? otherPath.toAbsolutePath().normalize().toString().replace('\\', '/') : null;
        }
    }

    static String removePrefix(String text) {
        if (text.startsWith(CLASSPATH_COLON) || text.startsWith(FILE_COLON)) {
            return text.substring(text.indexOf(':') + 1);
        } else {
            return text;
        }
    }

    static String getParentPath(String relativePath) {
        int pos = relativePath.lastIndexOf('/');
        return pos == -1 ? "" : relativePath.substring(0, pos + 1);
    }

    /**
     * Helper method to convert a URL to a Path, handling JAR URLs specially.
     * Falls back to MemoryResource if JAR file system provider is not available.
     *
     * @param url  the URL to convert
     * @param root optional root path for the resource
     * @return Path object, or null if fallback to MemoryResource is needed
     * @throws Exception if streaming fallback is required (caller should catch and create MemoryResource)
     */
    static Path urlToPath(URL url, Path root) throws Exception {
        URI uri = url.toURI();

        if (!"jar".equals(uri.getScheme())) {
            return Path.of(uri);
        }

        // Special handling for JAR URLs
        try {
            return Path.of(uri);
        } catch (java.nio.file.FileSystemNotFoundException e) {
            // File system doesn't exist yet, create it
            try {
                java.nio.file.FileSystems.newFileSystem(uri, java.util.Collections.emptyMap());
                return Path.of(uri);
            } catch (java.nio.file.FileSystemAlreadyExistsException ex) {
                // Another thread created it concurrently, that's fine
                return Path.of(uri);
            } catch (java.nio.file.ProviderNotFoundException ex) {
                // JAR file system provider not available - signal fallback needed
                throw new java.nio.file.ProviderNotFoundException("JAR provider not available");
            }
        } catch (java.nio.file.ProviderNotFoundException e) {
            // JAR file system provider not available - signal fallback needed
            throw e;
        }
    }

    static Resource path(String path) {
        return path(path, null);
    }

    /**
     * Creates a Resource from a path string, with optional ClassLoader for classpath resources.
     *
     * @param path        the resource path (supports "classpath:" prefix)
     * @param classLoader optional ClassLoader for classpath resources (null = use default)
     * @return Resource instance
     */
    static Resource path(String path, ClassLoader classLoader) {
        if (path == null) {
            path = "";
        }
        if (path.startsWith(CLASSPATH_COLON)) {
            String relativePath = removePrefix(path);
            // Remove leading slash for classloader compatibility
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            // Try provided classloader, then context classloader, then system classloader
            URL url = null;
            if (classLoader != null) {
                url = classLoader.getResource(relativePath);
            }
            if (url == null) {
                ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
                if (contextCL != null) {
                    url = contextCL.getResource(relativePath);
                }
            }
            if (url == null) {
                url = ClassLoader.getSystemResource(relativePath);
            }
            if (url == null) {
                throw new ResourceNotFoundException(path);
            }
            // Convert URL to Path for classpath resources
            try {
                Path resourcePath = urlToPath(url, null);
                return new PathResource(resourcePath, FileUtils.WORKING_DIR.toPath(), true);
            } catch (java.nio.file.ProviderNotFoundException e) {
                // JAR file system provider not available (common in jpackage/JavaFX apps)
                // Fall back to streaming the resource content (root defaults to SYSTEM_TEMP)
                try (java.io.InputStream is = url.openStream()) {
                    String content = FileUtils.toString(is);
                    return new MemoryResource(content);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to create resource from classpath: " + path, ex);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create resource from classpath: " + path, e);
            }
        } else if (path.startsWith(FILE_COLON)) {
            // Handle file: prefix by stripping it and creating PathResource
            String filePath = removePrefix(path);
            return new PathResource(Path.of(filePath));
        } else {
            return new PathResource(Path.of(path));
        }
    }

    /**
     * Scans classpath directories for resources with the given extension.
     * Handles both file system classpath entries and JARs.
     *
     * @param classpathDir the classpath directory (without "classpath:" prefix)
     * @param extension    file extension to filter (e.g., "feature")
     * @return list of matching Resources
     */
    static java.util.List<Resource> scanClasspath(String classpathDir, String extension) {
        return scanClasspath(classpathDir, extension, null, null);
    }

    /**
     * Scans classpath directories for resources with the given extension.
     * Handles both file system classpath entries and JARs.
     *
     * @param classpathDir the classpath directory (without "classpath:" prefix)
     * @param extension    file extension to filter (e.g., "feature")
     * @param classLoader  optional ClassLoader (null = use default)
     * @return list of matching Resources
     */
    static java.util.List<Resource> scanClasspath(String classpathDir, String extension, ClassLoader classLoader) {
        return scanClasspath(classpathDir, extension, classLoader, null);
    }

    /**
     * Scans classpath directories for resources with the given extension.
     * Handles both file system classpath entries and JARs.
     *
     * @param classpathDir the classpath directory (without "classpath:" prefix)
     * @param extension    file extension to filter (e.g., "feature")
     * @param classLoader  optional ClassLoader (null = use default)
     * @param root         optional root Path for relative path computation (null = use working dir)
     * @return list of matching Resources
     */
    static java.util.List<Resource> scanClasspath(String classpathDir, String extension, ClassLoader classLoader, Path root) {
        Path effectiveRoot = root != null ? root : FileUtils.WORKING_DIR.toPath();
        java.util.List<Resource> results = new java.util.ArrayList<>();

        // Normalize directory path
        String normalizedDir = classpathDir;
        if (normalizedDir.startsWith("/")) {
            normalizedDir = normalizedDir.substring(1);
        }
        if (!normalizedDir.isEmpty() && !normalizedDir.endsWith("/")) {
            normalizedDir = normalizedDir + "/";
        }

        try {
            // Get all resources matching this path from all classloaders
            java.util.Enumeration<URL> urls = null;
            if (classLoader != null) {
                urls = classLoader.getResources(normalizedDir);
            }
            if (urls == null || !urls.hasMoreElements()) {
                ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
                if (contextCL != null) {
                    urls = contextCL.getResources(normalizedDir);
                }
            }
            if (urls == null || !urls.hasMoreElements()) {
                urls = ClassLoader.getSystemResources(normalizedDir);
            }

            while (urls != null && urls.hasMoreElements()) {
                URL url = urls.nextElement();
                scanUrl(url, normalizedDir, extension, results, effectiveRoot);
            }
        } catch (Exception e) {
            // Silently ignore scan failures
        }

        return results;
    }

    /**
     * Scans a single URL for resources with the given extension.
     */
    private static void scanUrl(URL url, String baseDir, String extension, java.util.List<Resource> results, Path root) {
        try {
            Path dirPath = urlToPath(url, null);
            if (dirPath != null && java.nio.file.Files.isDirectory(dirPath)) {
                String suffix = "." + extension;
                try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(dirPath)) {
                    stream.filter(p -> java.nio.file.Files.isRegularFile(p) && p.toString().endsWith(suffix))
                            .forEach(p -> {
                                results.add(new PathResource(p, root, true));
                            });
                }
            }
        } catch (java.nio.file.ProviderNotFoundException e) {
            // JAR file system not available - fallback to manual JAR scanning
            scanJarManually(url, baseDir, extension, results);
        } catch (Exception e) {
            // Silently ignore scan failures for this URL
        }
    }

    /**
     * Manually scans a JAR for resources when NIO FileSystem is not available.
     */
    private static void scanJarManually(URL url, String baseDir, String extension, java.util.List<Resource> results) {
        String urlStr = url.toString();
        if (!urlStr.startsWith("jar:")) {
            return;
        }
        try {
            // Extract JAR path from URL like "jar:file:/path/to/file.jar!/some/dir/"
            String jarPath = urlStr.substring(4); // Remove "jar:"
            int bangIndex = jarPath.indexOf("!/");
            if (bangIndex == -1) {
                return;
            }
            String jarFilePath = jarPath.substring(0, bangIndex);
            if (jarFilePath.startsWith("file:")) {
                jarFilePath = jarFilePath.substring(5);
            }

            String suffix = "." + extension;
            try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarFilePath)) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.startsWith(baseDir) && entryName.endsWith(suffix)) {
                        // Read content and create MemoryResource
                        try (java.io.InputStream is = jarFile.getInputStream(entry)) {
                            String content = FileUtils.toString(is);
                            results.add(new MemoryResource(content, entryName));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore scan failures
        }
    }

    static Resource text(String text) {
        return new MemoryResource(text);
    }

    /**
     * Creates an in-memory Resource with a custom root.
     * Useful for planning where the resource would be materialized to disk.
     *
     * @param text the text content
     * @param root the root path (where this would live if saved)
     * @return MemoryResource instance
     */
    static Resource text(String text, Path root) {
        return new MemoryResource(text, root);
    }

    /**
     * Creates an in-memory Resource for code embedded within another source file.
     * The lineOffset shifts the lexer's starting line so tokens get absolute line numbers
     * relative to the host file. Use lineOffset=0 when the content starts at the
     * beginning of the parent file (e.g., wrapping karate-config.js in parentheses).
     *
     * @param text       the embedded code text
     * @param parent     the host file Resource (for source path)
     * @param lineOffset 0-indexed line number where the embedded code starts in the host file
     */
    static Resource embedded(String text, Resource parent, int lineOffset) {
        if (parent == null) {
            return new MemoryResource(text);
        }
        String relativePath = parent.getRelativePath();
        if (relativePath == null || relativePath.isEmpty()) {
            relativePath = parent.getPrefixedPath();
        }
        return new MemoryResource(text, relativePath, lineOffset);
    }

    /**
     * Creates a modern NIO Path-based Resource.
     * Recommended for new code - better performance and FileSystem support.
     *
     * @param path the path
     * @return PathResource instance
     */
    static Resource from(Path path) {
        return new PathResource(path);
    }

    /**
     * Creates a modern NIO Path-based Resource with custom root.
     *
     * @param path the path
     * @param root the root path for relative path computation
     * @return PathResource instance
     */
    static Resource from(Path path, Path root) {
        return new PathResource(path, root);
    }

    /**
     * Creates a Resource from a URL.
     * Supports file://, jar://, http://, and https:// schemes.
     *
     * @param url the URL to convert
     * @return Resource instance (PathResource for file/jar, UrlResource for http/https)
     */
    static Resource from(URL url) {
        return from(url, null);
    }

    /**
     * Creates a Resource from a URL with custom root.
     * Supports file://, jar://, http://, and https:// schemes.
     *
     * @param url  the URL to convert
     * @param root the root path for relative path computation
     * @return Resource instance (PathResource for file/jar, UrlResource for http/https)
     */
    static Resource from(URL url, Path root) {
        String protocol = url.getProtocol();

        // Handle HTTP/HTTPS URLs by streaming content into UrlResource
        if ("http".equals(protocol) || "https".equals(protocol)) {
            try (java.io.InputStream is = url.openStream()) {
                byte[] bytes = FileUtils.toBytes(is);
                return root != null ? new UrlResource(url, bytes, root) : new UrlResource(url, bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch content from URL: " + url, e);
            }
        }

        // Handle file:// and jar:// URLs
        try {
            Path path = urlToPath(url, root);
            return root != null ? new PathResource(path, root) : new PathResource(path);
        } catch (java.nio.file.ProviderNotFoundException e) {
            // JAR file system provider not available (common in jpackage/JavaFX apps)
            // Fall back to streaming the resource content
            try (java.io.InputStream is = url.openStream()) {
                String content = FileUtils.toString(is);
                return root != null ? new MemoryResource(content, root) : new MemoryResource(content);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create resource from URL: " + url, ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create resource from URL: " + url, e);
        }
    }

}
