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

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Engine;
import java.util.List;

/**
 * this is only for capturing what was actually sent on the wire, read-only
 *
 * @author pthomas3
 */
public class HttpRequest {

    private String requestId;
    private long startTime;
    private long endTime;
    private double responseTime;
    private String urlBase; // used in mock since uri may start with '/'
    private String uri; // will be full uri including query string
    private String method;
    private MultiValuedMap headers = new MultiValuedMap();
    private MultiValuedMap params; // only used in mock
    private byte[] body;

    private long startTimeNanos;

    public long getStartTime() {
        return startTime;
    }

    public void startTimer() {
        startTime = System.currentTimeMillis();
        startTimeNanos = System.nanoTime();
    }

    public long getEndTime() {
        return endTime;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public String getResponseTimeFormatted() {
        return String.format("%.2f", responseTime);
    }

    public void stopTimer() {
        responseTime = Engine.nanosToMillis(System.nanoTime() - startTimeNanos);
        endTime = startTime + Math.round(responseTime);
    }

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }

    public void addHeader(String key, String value) {
        headers.add(key, value);
    }

    public void putHeader(String key, List<String> values) {
        headers.put(key, values);
    }

    public void setParams(MultiValuedMap params) {
        this.params = params;
    }

    public MultiValuedMap getParams() {
        return params;
    }

    public void putParam(String key, List<String> values) {
        if (params == null) {
            params = new MultiValuedMap();
        }
        params.put(key, values);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getRequestId(){
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public MultiValuedMap getHeaders() {
        return headers;
    }

    public void setHeaders(MultiValuedMap headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[method: ").append(method);
        sb.append(", responseTime: ").append(responseTime);
        if (body != null) {
            sb.append(", body: ").append(FileUtils.toString(body));
        }
        sb.append("]");
        return sb.toString();        
    }    
    
}
