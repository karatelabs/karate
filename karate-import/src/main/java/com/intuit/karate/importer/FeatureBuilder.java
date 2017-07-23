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
package com.intuit.karate.importer;

/**
 * Created by rkumar32 on 7/5/17.
 */
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by rkumar32 on 5/24/17.
 */
public class FeatureBuilder {

    private String name;
    private String url;
    private String method;
    private String headers;
    private String body;

    private final String SCENARIO_TEMPLATE = "Scenario: %s" +                                   // Scenario Description
            "Given url " + "%s" +                                                               // Url
            "%s" +                                                                              // Headers
            "%s" +                                                                              // Body
            "When method %s" + System.lineSeparator();                                          // Method


    public FeatureBuilder() {
    }

    public FeatureBuilder addName(String name) {
        if (name != null) {
            this.name = name + System.lineSeparator();
        }
        else {
            this.name = "";
        }
        return this;
    }

    public FeatureBuilder addUrl(String url) {
        if (url != null) {
            this.url = "'" + url + "'" + System.lineSeparator();
        }
        else {
            throw new IllegalArgumentException("Url is null");
        }
        return this;
    }

    public FeatureBuilder addMethod(String method) {
        if (url != null) {
            this.method = method + System.lineSeparator();
        }
        else {
            throw new IllegalArgumentException("Method is null");
        }
        return this;
    }

    public FeatureBuilder addHeaders(Map<String, String > headers) {
        this.headers = "";
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            this.headers += "And header " + entry.getKey() + " = " + "'" +
                    entry.getValue() + "'" + System.lineSeparator();
        }
        return this;
    }

    public FeatureBuilder addBody(String body) {
        if (body != null) {
            this.body = "And request " + body + System.lineSeparator();
        }
        else {
            this.body = "";
        }
        return this;
    }

    public String getName() {
        return name;
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
        return String.format(SCENARIO_TEMPLATE,  name,
                url,
                headers,
                body,
                method);
    }
}

