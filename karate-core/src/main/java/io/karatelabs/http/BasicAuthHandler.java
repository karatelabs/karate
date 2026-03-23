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

import io.karatelabs.common.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAuthHandler implements AuthHandler {

    private final String username;
    private final String password;

    public BasicAuthHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + encodedAuth);
    }

    @Override
    public String getType() {
        return "basic";
    }

    @Override
    public String toCurlArgument(String platform) {
        // Use curl's native basic auth flag
        String userPass = username + ":" + password;
        // Escape for shell based on platform
        return "-u " + StringUtils.shellEscapeForPlatform(userPass, platform);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

}
