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
package com.intuit.karate.data;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Json {

    private final DocumentContext doc;
    private final boolean array;
    private final String prefix;

    private String prefix(String path) {
        return path.charAt(0) == '$' ? path : prefix + path;
    }

    public static Json of(String json) {
        return new Json(json);
    }

    public Json() {
        this("{}");
    }

    public Json(String json) {
        this(JsonPath.parse(json));
    }

    public Json(Object o) {
        this(JsonPath.parse(o));
    }

    private Json(DocumentContext doc) {
        this.doc = doc;
        array = (doc.json() instanceof List);
        prefix = array ? "$" : "$.";
    }

    public Json getJson(String path) {
        return new Json(get(path, String.class));
    }

    public <T> T get(String path) {
        return (T) doc.read(prefix(path));
    }

    public <T> T get(String path, Class<T> clazz) {
        return doc.read(prefix(path), clazz);
    }

    @Override
    public String toString() {
        return doc.jsonString();
    }

    public boolean isArray() {
        return array;
    }

    public Object asMapOrList() {
        return doc.read("$");
    }

    public Map<String, Object> asMap() {
        return doc.read("$");
    }

    public List asList() {
        return doc.read("$");
    }

}
