/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.netty;

import com.intuit.karate.StringUtils;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class ProxyContext {

    public final String host;
    public final int port;
    public final boolean ssl;
    public final String hostColonPort;

    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;

    public ProxyContext(HttpRequest request) {
        this(getHostColonPortFromHeader(request), NettyUtils.isConnect(request));
    }

    public ProxyContext(String uri, boolean ssl) {
        this.ssl = ssl;
        uri = extractHostColonPort(uri);
        int pos = uri.indexOf(':');
        if (pos != -1) {
            host = uri.substring(0, pos);
            port = parsePort(uri.substring(pos + 1), ssl);
        } else {
            host = uri;
            port = ssl ? HTTPS_PORT : HTTP_PORT;
        }
        hostColonPort = host + ':' + port;
    }

    private static int parsePort(String raw, boolean ssl) {
        try {
            return Integer.valueOf(raw);
        } catch (Exception e) {
            return ssl ? HTTPS_PORT : HTTP_PORT;
        }
    }

    private static String getHostColonPortFromHeader(HttpRequest request) {
        String hostColonPort = extractHostColonPort(request.uri());
        if (StringUtils.isBlank(hostColonPort)) {
            List<String> hosts = request.headers().getAll(HttpHeaderNames.HOST);
            if (hosts != null && !hosts.isEmpty()) {
                hostColonPort = hosts.get(0);
            }
        }
        return hostColonPort;
    }

    private static String extractHostColonPort(String uri) {
        if (uri.startsWith("http")) {
            uri = uri.substring(uri.indexOf('/') + 2);
        }
        int pos = uri.indexOf('/');
        if (pos != -1) {
            uri = uri.substring(0, pos);
        }
        return uri;
    }

    public static String removeHostColonPort(String uri) {
        if (!uri.startsWith("http")) {
            return uri;
        }
        uri = uri.substring(uri.indexOf('/') + 2);
        int pos = uri.indexOf("/");
        if (pos == -1) {
            return "/";
        }
        return uri.substring(pos);
    }

}
