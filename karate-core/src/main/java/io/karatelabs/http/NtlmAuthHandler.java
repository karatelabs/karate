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
 * HTTP NTLM auth. {@link #apply} adds nothing: NTLM is a handshake over one connection, which
 * {@link ApacheHttpClient} performs from the credentials that
 * {@code configure auth = { type: 'ntlm', ... }} gives it. This handler carries the type, the
 * credentials and the curl export.
 */
public class NtlmAuthHandler implements AuthHandler {

    private final String username;
    private final String password;
    private final String domain;
    private final String workstation;

    public NtlmAuthHandler(String username, String password, String domain, String workstation) {
        this.username = username;
        this.password = password;
        this.domain = domain;
        this.workstation = workstation;
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
    }

    @Override
    public String getType() {
        return "ntlm";
    }

    @Override
    public String toCurlArgument(String platform) {
        String user = domain == null || domain.isEmpty() ? username : domain + "\\" + username;
        return "--ntlm -u " + StringUtils.shellEscapeForPlatform(user + ":" + password, platform);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDomain() {
        return domain;
    }

    public String getWorkstation() {
        return workstation;
    }

}
