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
package com.intuit.karate.web.chrome;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {id: 1, method: 'Page.navigate', params: {url: 'https://www.github.com/'}}
 * 
 * @author pthomas3
 */
public class ChromeMessage {        
    
    private int id;
    private String method;
    private Map<String, Object> params;
    private Map<String, Object> result;
    
    public ChromeMessage() {
        // for json marshalling
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
    
    public ChromeMessage(int id, String method) {
        this.id = id;
        this.method = method;        
    }
    
    public ChromeMessage param(String key, Object value) {
        if (params == null) {
            params = new LinkedHashMap();
        }
        params.put(key, value);
        return this;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(4);
        map.put("id", id);
        map.put("method", method);
        if (params != null) {
            map.put("params", params);
        }
        if (result != null) {
            map.put("result", result);
        }
        return map;
    }
    
    public void send(Chrome chrome) {
        chrome.sendAndWait(this);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[id: ").append(id);
        if (method != null) {
            sb.append(", method: ").append(method);
        }
        if (params != null) {
            sb.append(", params: ").append(params);
        }       
        if (result != null) {
            sb.append(", result: ").append(result);
        }        
        sb.append("]");
        return sb.toString();
    }    
    
}
