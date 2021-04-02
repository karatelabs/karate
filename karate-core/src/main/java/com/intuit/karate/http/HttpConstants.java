/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author pthomas3
 */
public class HttpConstants {

    private HttpConstants() {
        // only static methods
    }

    public static final Set<String> HTTP_METHODS
            = Stream.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "CONNECT", "TRACE")
                    .collect(Collectors.toSet());

    public static final String HDR_COOKIE = "Cookie";
    public static final String HDR_SET_COOKIE = "Set-Cookie";
    public static final String HDR_CONTENT_TYPE = "Content-Type";
    public static final String HDR_LOCATION = "Location";
    public static final String HDR_CONTENT_LENGTH = "Content-Length";
    public static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String HDR_ACCEPT = "Accept";
    public static final String HDR_ALLOW = "Allow";
    public static final String HDR_CACHE_CONTROL = "Cache-Control";

    public static final String HDR_HX_TRIGGER = "HX-Trigger";
    public static final String HDR_HX_REQUEST = "HX-Request";
    public static final String HDR_HX_REDIRECT = "HX-Redirect";

}
