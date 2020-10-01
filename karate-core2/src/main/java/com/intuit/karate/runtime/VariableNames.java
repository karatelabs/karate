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
package com.intuit.karate.runtime;

/**
 *
 * @author pthomas3
 */
public class VariableNames {
    
    private VariableNames() {
        // only static methods
    }
    
    public static final String RESPONSE = "response";
    public static final String RESPONSE_BYTES = "responseBytes";
    public static final String RESPONSE_COOKIES = "responseCookies";
    public static final String RESPONSE_HEADERS = "responseHeaders";
    public static final String RESPONSE_STATUS = "responseStatus";
    public static final String RESPONSE_DELAY = "responseDelay";
    public static final String RESPONSE_TIME = "responseTime";
    public static final String RESPONSE_TYPE = "responseType";

    public static final String REQUEST = "request";
    public static final String REQUEST_BYTES = "requestBytes";
    public static final String REQUEST_URL_BASE = "requestUrlBase";
    public static final String REQUEST_URI = "requestUri";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String REQUEST_HEADERS = "requestHeaders";
    public static final String REQUEST_PARAMS = "requestParams";
    public static final String REQUEST_BODY = "requestBody";
    public static final String REQUEST_TIME_STAMP = "requestTimeStamp";
    
}
