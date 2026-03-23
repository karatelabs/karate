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

public interface AuthHandler {

    void apply(HttpRequestBuilder builder);

    String getType();

    /**
     * Returns the curl command argument for this auth handler, or null to use the default
     * behavior (including the Authorization header).
     * For example, BasicAuthHandler can return "-u username:password"
     *
     * @param platform "sh" for Unix/Linux/macOS, "cmd" for Windows CMD, "ps" for PowerShell
     */
    default String toCurlArgument(String platform) {
        return null;
    }

    /**
     * Returns the curl preview for this auth handler (without triggering side effects).
     * This is used for UI/preview purposes where we don't want to make network calls.
     * If null, falls back to toCurlArgument() behavior.
     * For OAuth handlers that need network access, this should return a placeholder.
     *
     * @param platform "sh" for Unix/Linux/macOS, "cmd" for Windows CMD, "ps" for PowerShell
     */
    default String toCurlPreview(String platform) {
        return toCurlArgument(platform);
    }

}
