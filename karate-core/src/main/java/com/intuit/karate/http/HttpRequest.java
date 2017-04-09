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

import com.intuit.karate.ScriptValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpRequest {

    private String url;
    private List<String> paths;
    private MultiValuedMap headers;
    private MultiValuedMap params;
    private Map<String, Cookie> cookies;
    private MultiValuedMap formFields;
    private List<MultiPartItem> multiPartItems;
    private ScriptValue body;
    private String method;
    private String soapAction; 

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void addPath(String path) {
        if (paths == null) {
            paths = new ArrayList<>();
        }
        paths.add(path);
    }

    public List<String> getPaths() {
        return paths;
    }

    public void addHeader(String name, String value) {
        if (headers == null) {
            headers = new MultiValuedMap();
        }
        headers.add(name, value);
    }

    public MultiValuedMap getHeaders() {
        return headers;
    }

    public void addParam(String name, String value) {
        if (params == null) {
            params = new MultiValuedMap();
        }
        params.add(name, value);
    }

    public MultiValuedMap getParams() {
        return params;
    }

    public void addCookie(Cookie cookie) {
        if (cookies == null) {
            cookies = new LinkedHashMap<>();
        }
        cookies.put(cookie.getName(), cookie);       
    }

    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    public void setCookies(Map<String, Cookie> cookies) {
        this.cookies = cookies;
    }   

    public void addFormField(String name, String value) {
        if (formFields == null) {
            formFields = new MultiValuedMap();
        }
        formFields.add(name, value);
    }

    public MultiValuedMap getFormFields() {
        return formFields;
    }

    public void addMultiPartItem(String name, ScriptValue value) {
        if (multiPartItems == null) {
            multiPartItems = new ArrayList<>();
        }
        multiPartItems.add(new MultiPartItem(name, value));
    }

    public List<MultiPartItem> getMultiPartItems() {
        return multiPartItems;
    }    

    public ScriptValue getBody() {
        return body;
    }

    public void setBody(ScriptValue body) {
        this.body = body;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public String getContentType() {
        if (headers == null) {
            return null;
        }
        Object contentType = headers.getFirst("Content-Type");
        if (contentType == null) {
            return null;
        }
        return contentType.toString();
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public String getSoapAction() {
        return soapAction;
    }        

}
