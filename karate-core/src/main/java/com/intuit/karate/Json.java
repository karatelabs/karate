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
package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Json {

    private static final Logger logger = LoggerFactory.getLogger(Json.class);

    private final DocumentContext doc;
    private final boolean array;
    private final String prefix;

    private String prefix(String path) {
        return path.charAt(0) == '$' ? path : prefix + path;
    }

    public static Json object() {
        return Json.of("{}");
    }

    public static Json array() {
        return Json.of("[]");
    }

    public static Json of(Object any) {
        if (any instanceof String) {
            return new Json(JsonPath.parse((String) any));
        } else if (any instanceof List) {
            return new Json(JsonPath.parse((List) any));
        } else if (any instanceof Map) {
            return new Json(JsonPath.parse((Map) any));
        } else {
            String json = JsonUtils.toJson(any);
            return new Json(JsonPath.parse(json));
        }
    }

    private Json(DocumentContext doc) {
        this.doc = doc;
        array = (doc.json() instanceof List);
        prefix = array ? "$" : "$.";
    }

    public Json getJson(String path) {
        return Json.of(get(path, String.class));
    }

    public <T> T get(String path) {
        return (T) doc.read(prefix(path));
    }

    public <T> T getOrNull(String path) {
        return (T) getOptional(path).orElse(null);
    }

    public <T> T getOr(String path, T defaultValue) {
        return (T) getOptional(path).orElse(defaultValue);
    }

    public <T> Optional<T> getOptional(String path) {
        try {
            return Optional.<T>of(get(path));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> T getFirst(String path) {
        List<T> list = get(path);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public <T> T get(String path, Class<T> clazz) {
        return doc.read(prefix(path), clazz);
    }

    @Override
    public String toString() {
        return doc.jsonString();
    }

    public String toStringPretty() {
        return JsonUtils.toJson(value(), true);
    }

    public boolean isArray() {
        return array;
    }

    public <T> T value() {
        return doc.read("$");
    }

    public List asList() {
        return value();
    }

    public Map<String, Object> asMap() {
        return value();
    }

    public Json set(String path, String value) {
        if (JsonUtils.isJson(value)) {
            setInternal(path, Json.of(value).value());
        } else {
            if (value != null && value.charAt(0) == '\\') {
                value = value.substring(1);
            }
            setInternal(path, value);
        }
        return this;
    }

    public Json remove(String path) {
        doc.delete(prefix(path));
        return this;
    }

    public Json set(String path, Object value) {
        setInternal(path, value);
        return this;
    }

    private boolean isArrayPath(String s) {
        return s.endsWith("]") && !s.endsWith("']");
    }

    private String arrayKey(String s) {
        int pos = s.lastIndexOf('[');
        return s.substring(0, pos);
    }

    private int arrayIndex(String s) {
        int leftPos = s.lastIndexOf('[');
        if (leftPos == -1) {
            return -1;
        }
        int rightPos = s.indexOf(']', leftPos);
        if (leftPos == -1) {
            return -1;
        }
        String num = s.substring(leftPos + 1, rightPos);
        if (num.isEmpty()) {
            return -1;
        }
        try {
            return Integer.valueOf(num);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void setInternal(String path, Object o) {
        path = prefix(path);
        if ("$".equals(path)) {
            throw new RuntimeException("cannot replace root path $");
        }
        boolean forArray = isArrayPath(path);
        if (!pathExists(path)) {
            createPath(path, forArray);
        }
        StringUtils.Pair pair = toParentAndLeaf(path);
        if (forArray) {
            int index = arrayIndex(pair.right);
            if (index == -1) {
                doc.add(arrayKey(path), o);
            } else {
                doc.set(path, o);
            }
        } else {
            doc.put(pair.left, pair.right, o);
        }
    }

    public boolean pathExists(String path) {
        if (path.endsWith("[]")) {
            path = path.substring(0, path.length() - 2);
        }
        try {
            Object temp = doc.read(path);
            return temp != null;
        } catch (PathNotFoundException pnfe) {
            return false;
        }
    }

    private void createPath(String path, boolean array) {
        if (isArrayPath(path)) {
            String parentPath = arrayKey(path);
            if (!pathExists(parentPath)) {
                createPath(parentPath, true);
            }
            List list = get(parentPath);
            if (list == null) {
                list = new ArrayList();
                set(parentPath, list);
            }
            int index = arrayIndex(path);
            if (list.size() <= index) {
                for (int i = list.size(); i <= index; i++) {
                    list.add(null);
                }
            }
        } else {
            StringUtils.Pair pair = toParentAndLeaf(path);
            if (!pathExists(pair.left)) {
                createPath(pair.left, false);
            }
            if (isArrayPath(pair.left)) {
                if (isArrayPath(pair.right)) {
                    doc.set(pair.left, new ArrayList());
                } else {
                    if (!pathExists(pair.left)) { // a necessary repetition
                        doc.set(pair.left, new LinkedHashMap());
                    }
                    doc.put(pair.left, pair.right, new LinkedHashMap());
                }
            } else {
                doc.put(pair.left, pair.right, array ? new ArrayList() : new LinkedHashMap());
            }
        }
    }

    public static StringUtils.Pair toParentAndLeaf(String path) {
        int pos = path.lastIndexOf('.');
        int temp = path.lastIndexOf("['");
        if (temp != -1 && temp > pos) {
            pos = temp - 1;
        }
        String right = path.substring(pos + 1);
        if (right.startsWith("[")) {
            pos = pos + 1;
        }
        String left = path.substring(0, pos == -1 ? 0 : pos);
        return StringUtils.pair(left, right);
    }

}
