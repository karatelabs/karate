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
package io.karatelabs.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Provides protection against path traversal attacks in resource resolution.
 * <p>
 * Path traversal attacks attempt to access files outside the intended directory
 * by using sequences like {@code ../} or encoded variants like {@code %2e%2e/}.
 * <p>
 * This class validates paths before they are used for resource resolution,
 * rejecting any path that contains traversal sequences.
 */
public class PathSecurity {

    /**
     * Check if a path is safe (does not contain traversal sequences).
     * <p>
     * This method checks for:
     * <ul>
     *   <li>{@code ..} - Direct parent directory reference</li>
     *   <li>URL-encoded variants ({@code %2e}, {@code %2E})</li>
     *   <li>Double-encoded variants</li>
     *   <li>Backslash variants (Windows-style)</li>
     * </ul>
     *
     * @param path the path to validate
     * @return true if the path is safe, false if it contains traversal sequences
     */
    public static boolean isSafe(String path) {
        if (path == null) {
            return true;
        }

        // Check raw path for obvious traversal
        if (containsTraversal(path)) {
            return false;
        }

        // Decode URL encoding and check again (handles %2e%2e, %2f, etc.)
        try {
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            if (!decoded.equals(path) && containsTraversal(decoded)) {
                return false;
            }

            // Double-decode to catch double-encoding attacks (%252e%252e)
            String doubleDec = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            if (!doubleDec.equals(decoded) && containsTraversal(doubleDec)) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            // Malformed encoding - treat as unsafe
            return false;
        }

        return true;
    }

    /**
     * Check if the raw string contains path traversal sequences.
     */
    private static boolean containsTraversal(String path) {
        // Normalize backslashes to forward slashes for checking
        String normalized = path.replace('\\', '/');

        // Check for parent directory references
        if (normalized.contains("../") || normalized.contains("/..")) {
            return true;
        }

        // Check for standalone .. (path is exactly ".." or ends with "..")
        if (normalized.equals("..") || normalized.endsWith("/..")) {
            return true;
        }

        // Check for path starting with ..
        if (normalized.startsWith("..")) {
            return true;
        }

        return false;
    }

    /**
     * Validate a path and throw an exception if unsafe.
     *
     * @param path the path to validate
     * @throws PathTraversalException if the path contains traversal sequences
     */
    public static void validate(String path) throws PathTraversalException {
        if (!isSafe(path)) {
            throw new PathTraversalException(path);
        }
    }

    /**
     * Exception thrown when a path traversal attempt is detected.
     */
    public static class PathTraversalException extends RuntimeException {

        private final String path;

        public PathTraversalException(String path) {
            super("Path traversal detected: " + path);
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

}
