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

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpRequest {

    private long startTimeMillis;
    private long endTimeMillis;
    private String url;
    private String method;
    private Map<String, List<String>> headers;
    private byte[] body;
    private String bodyForDisplay;

    public void putHeader(String name, String... values) {
        putHeader(name, Arrays.asList(values));
    }

    public void putHeader(String name, List<String> values) {
        if (headers == null) {
            headers = new HashMap();
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        headers.put(name, values);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public void setEndTimeMillis(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return FileUtils.toString(body);
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getBodyForDisplay() {
        return bodyForDisplay;
    }

    public void setBodyForDisplay(String bodyForDisplay) {
        this.bodyForDisplay = bodyForDisplay;
    }

    public List<String> getHeaderValues(String name) { // TOTO optimize
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public void removeHeader(String name) {
        if (headers == null) {
            return;
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        headers.remove(name);
    }

    public String getHeader(String name) {
        List<String> values = getHeaderValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public String getContentType() {
        return getHeader(HttpConstants.HDR_CONTENT_TYPE);
    }

    public void setContentType(String contentType) {
        putHeader(HttpConstants.HDR_CONTENT_TYPE, contentType);
    }

    public Request toRequest() {
        Request request = new Request();
        request.setMethod(method);
        request.setUrl(url);
        request.setHeaders(headers);
        request.setBody(body);
        return request;
    }

}
