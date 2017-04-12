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

import java.io.InputStream;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class DummyHttpClient extends HttpClient<String> {

    @Override
    public void configure(HttpConfig config) {
        
    }

    @Override
    protected String getEntity(List<MultiPartItem> items, String mediaType) {
        return "";
    }

    @Override
    protected String getEntity(MultiValuedMap fields, String mediaType) {
        return "";
    }

    @Override
    protected String getEntity(String value, String mediaType) {
        return "";
    }
    
    @Override
    protected String getEntity(InputStream value, String mediaType) {
        return "";
    }    

    @Override
    protected void buildUrl(String url) {
        
    }

    @Override
    protected void buildPath(String path) {
        
    }

    @Override
    protected void buildParam(String name, Object... values) {
        
    }

    @Override
    protected void buildHeader(String name, Object value, boolean replace) {
        
    }

    @Override
    protected void buildCookie(Cookie cookie) {
        
    }

    @Override
    protected HttpResponse makeHttpRequest(String method, String entity, long startTime) {
        return new HttpResponse();
    }

    @Override
    protected String getRequestUri() {
        return "";
    }
    
}
