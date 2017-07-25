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
import java.util.Collections;
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

    public void removeHeader(String name) {
        if (headers == null) {
            return;
        }
        headers.remove(name);
    }

    public void setHeader(String name, String value) {
        setHeader(name, Collections.singletonList(value));
    }

    public void setHeader(String name, List<String> values) {
        if (headers == null) {
            headers = new MultiValuedMap();
        }
        headers.put(name, values);
    }

    public MultiValuedMap getHeaders() {
        return headers;
    }

    public void removeParam(String name) {
        if (params == null) {
            return;
        }
        params.remove(name);
    }

    public void setParam(String name, String value) {
        setParam(name, Collections.singletonList(value));
    }

    public void setParam(String name, List<String> values) {
        if (params == null) {
            params = new MultiValuedMap();
        }
        params.put(name, values);
    }

    public MultiValuedMap getParams() {
        return params;
    }

    public void removeCookie(String name) {
        if (cookies == null) {
            return;
        }
        cookies.remove(name);
    }

    public void setCookie(Cookie cookie) {
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

    public void removeFormField(String name) {
        if (formFields == null) {
            return;
        }
        formFields.remove(name);
    }

    public void setFormField(String name, String value) {
        setFormField(name, Collections.singletonList(value));
    }

    public void setFormField(String name, List<String> values) {
        if (formFields == null) {
            formFields = new MultiValuedMap();
        }
        formFields.put(name, values);
    }

    public MultiValuedMap getFormFields() {
        return formFields;
    }

    public void addMultiPartItem(String name, ScriptValue value) {
        MultiPartItem item = new MultiPartItem(name, value);
        if (value.isStream()) { // short-cut, assume that intent is file-upload
            item.setFilename(name);
        }
        addMultiPartItem(item);
    }
    
    public void addMultiPartItem(MultiPartItem item) {
        if (multiPartItems == null) {
            multiPartItems = new ArrayList<>();
        }
        multiPartItems.add(item);
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
