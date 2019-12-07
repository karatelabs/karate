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

import com.jayway.jsonpath.DocumentContext;
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

    public Json() {
        this("{}");
    }

    public Json(String json) {
        this(JsonUtils.toJsonDoc(json));
    }

    public Json(Map map) {
        this(JsonUtils.toJsonDoc(map));
    }

    public Json(Object o) {
        this(JsonUtils.toJsonDoc(o));
    }

    private Json(DocumentContext doc) {
        this.doc = doc;
        array = (doc.json() instanceof List);
        prefix = array ? "$" : "$.";
    }

    public DocumentContext getDoc() {
        return doc;
    }     
    
    public ScriptValue getValue() {
        return new ScriptValue(doc);
    }

    public Json set(String path, Object value) {
        JsonUtils.setValueByPath(doc, prefix(path), value);
        return this;
    }

    public Json set(String path, String value) {
        Object temp = value;
        String trimmed = StringUtils.trimToNull(value);        
        if (Script.isJson(trimmed)) {
            temp = JsonUtils.toJsonDoc(trimmed).read("$");
        }
        set(path, temp);
        return this;
    }
    
    public Match getMatcher(String path) {
        return Match.init(get(path));
    }
    
    public Json getJson(String path) {
        return new Json(get(path));
    }    

    public Object get(String path) {
        return doc.read(prefix(path));
    }

    public <T> T get(String path, Class<T> clazz) {
        return doc.read(prefix(path), clazz);
    }        
    
    public String getString(String path) {
        return get(path, String.class);
    }  
    
    public List getList(String path) {
        return get(path, List.class);
    }    
    
    public Map getMap(String path) {
        return get(path, Map.class);
    }

    public Number getNumber(String path) {
        return get(path, Number.class);
    }    

    public Integer getInteger(String path) {
        return get(path, Integer.class);
    }  
    
    public Boolean getBoolean(String path) {
        return get(path, Boolean.class);
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

    public List<Map<String, Object>> asList() {
        return doc.read("$");
    }

    public Json equals(String exp) {
        Match.equals(doc.read("$"), exp);
        return this;
    }

}
