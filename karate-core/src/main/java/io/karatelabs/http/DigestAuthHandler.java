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
package io.karatelabs.http;

import io.karatelabs.common.StringUtils;

/**
 * HTTP Digest auth. {@link #apply} adds nothing: the Authorization header depends on the nonce in
 * the server's 401 challenge, so {@link ApacheHttpClient} answers it from the credentials that
 * {@code configure auth = { type: 'digest', ... }} gives it. This handler carries the type and the
 * curl export.
 */
public class DigestAuthHandler implements AuthHandler {

    private final String username;
    private final String password;

    public DigestAuthHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
    }

    @Override
    public String getType() {
        return "digest";
    }

    @Override
    public String toCurlArgument(String platform) {
        return "--digest -u " + StringUtils.shellEscapeForPlatform(username + ":" + password, platform);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

}
