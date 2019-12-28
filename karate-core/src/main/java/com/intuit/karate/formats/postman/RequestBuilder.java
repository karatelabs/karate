/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.formats.postman;

import com.intuit.karate.StringUtils;

import java.util.Map;

/**
 * Created by rkumar32 on 5/24/17.
 */
public class RequestBuilder {

    private String url;
    private String method;
    private String headers;
    private String body;

    private final String REQUEST_TEMPLATE = "\t\tGiven url " + "%s" + // url
            "%s" +                                                    // Headers
            "%s" +                                                    // Body
            "\t\tWhen method %s" + System.lineSeparator();            // Method

    public RequestBuilder addUrl(String url) {
        if (url != null) {
            this.url = "'" + url + "'" + System.lineSeparator();
        } else {
            throw new IllegalArgumentException("Url is null");
        }
        return this;
    }

    public RequestBuilder addMethod(String method) {
        if (url != null) {
            this.method = method + System.lineSeparator();
        } else {
            throw new IllegalArgumentException("Method is null");
        }
        return this;
    }

    public RequestBuilder addHeaders(Map<String, String> headers) {
        this.headers = "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            this.headers += "\t\tAnd header " + entry.getKey() + " = " + "'" +
                    entry.getValue() + "'" + System.lineSeparator();
        }
        return this;
    }

    public RequestBuilder addBody(String body) {
        if (body != null) {
            this.body = "\t\tAnd request " + body + System.lineSeparator();
        } else {
            this.body = "";
        }
        return this;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public String build() {
        if ("POST".equals(method) && StringUtils.isBlank(body)) {
            throw new IllegalArgumentException("Body can't be null if method is POST");
        }
        return String.format(REQUEST_TEMPLATE, url,
                headers,
                body,
                method);
    }

}
