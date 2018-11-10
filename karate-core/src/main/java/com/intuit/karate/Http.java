/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate;

/**
 *
 * @author pthomas3
 */
public class Http {
    
    private final Match match;
    
    // used in Match
    protected Http(Match match) {
        this.match = match;
    }
    
    public Http url(String url) {
        match.url(url);
        return this;
    }
    
    public Http path(String ... paths) {
        match.path(paths);
        return this;
    }    
    
    public Match get() {
        return match.httpGet();
    }
    
    public Match post(Json json) { // avoid extra eval
        return match.httpPost(json.getValue());
    }    
    
    public Match post(Object body) {
        return match.httpPost(body);
    }
    
    public Match delete() {
        return match.httpDelete();
    }    
    
    public static Http forUrl(Logger logger, String url) {
        Http http = new Http(Match.withHttp(logger));
        return http.url(url);
    }
    
}
