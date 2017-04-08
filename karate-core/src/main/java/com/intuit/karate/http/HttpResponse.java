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
package com.intuit.karate.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpResponse {
    
    private String uri;
    private Map<String, String> cookies;
    private MultiValuedMap headers;
    private byte[] body;
    private int status;
    private long responseTime;

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }   
    
    public Map<String, String> getCookies() {
        return cookies;
    }

    public MultiValuedMap getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public long getResponseTime() {
        return responseTime;
    }        

    public void setBody(byte[] body) {
        this.body = body;
    }
    
    public void addCookie(String name, String value) {
        if (cookies == null) {
            cookies = new LinkedHashMap<>();
        }
        cookies.put(name, value);
    }
    
    public void addHeader(String name, String value) {
        if (headers == null) {
            headers = new MultiValuedMap();
        }
        headers.add(name, value);
    }    
    
    public void addHeader(String name, List values) {
        if (headers == null) {
            headers = new MultiValuedMap();
        }
        headers.put(name, values);
    }
    
}
