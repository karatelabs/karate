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
package com.intuit.karate.match;

import com.intuit.karate.XmlUtils;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class MatchValue {

    public static enum Type {
        NULL,
        BOOLEAN,
        NUMBER,
        STRING,
        BYTES,
        LIST,
        MAP,
        XML,
        OTHER
    }

    public final Type type;
    private final Object value;

    public MatchValue(Object value) {
        this.value = value;
        if (value == null) {
            type = Type.NULL;
        } else if (value instanceof Node) {
            type = Type.XML;
        } else if (value instanceof List) {
            type = Type.LIST;
        } else if (value instanceof Map) {
            type = Type.MAP;
        } else if (value instanceof String) {
            type = Type.STRING;
        } else if (Number.class.isAssignableFrom(value.getClass())) {
            type = Type.NUMBER;
        } else if (Boolean.class.equals(value.getClass())) {
            type = Type.BOOLEAN;
        } else if (value instanceof byte[]) {
            type = Type.BYTES;
        } else {
            type = Type.OTHER;
        }
    }

    public boolean isBoolean() {
        return type == Type.BOOLEAN;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isMap() {
        return type == Type.MAP;
    }

    public boolean isList() {
        return type == Type.LIST;
    }

    public boolean isXml() {
        return type == Type.XML;
    }

    public boolean isNotPresent() {
        return "#notpresent".equals(value);
    }

    public boolean isMapOrListOrXml() {
        switch (type) {
            case MAP:
            case LIST:
            case XML:
                return true;
            default:
                return false;
        }
    }

    public <T> T getValue() {
        return (T) value;
    }

    public static Object parseIfJsonOrXml(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            if (s.isEmpty()) {
                return o;
            } else if (JsonUtils.isJson(s)) {
                return new Json(s).asMapOrList();
            } else if (XmlUtils.isXml(s)) {
                return XmlUtils.toXmlDoc(s);
            } else {
                if (s.charAt(0) == '\\') {
                    return s.substring(1);
                }
            }
        }
        return o;
    }

    public MatchResult is(MatchType mt, Object o) {
        MatchOperation mo = new MatchOperation(mt, this, new MatchValue(parseIfJsonOrXml(o)));
        mo.execute();
        return mo.pass ? MatchResult.PASS : MatchResult.fail(mo.getFailureReasons());
    }
    
    public MatchResult contains(Object o) {
        return is(MatchType.CONTAINS, o);
    }

    public MatchResult isEqualTo(Object o) {
        return is(MatchType.EQUALS, o);
    }

    public String getWithinSingleQuotesIfString() {
        if (type == Type.STRING) {
            return "'" + value + "'";
        } else {
            return getAsString();
        }
    }

    public String getAsString() {
        switch (type) {
            case LIST:
            case MAP:
                return JsonUtils.toJsonSafe(value, false);
            case XML:
                return XmlUtils.toString(getValue());
            default:
                return value + "";
        }
    }

    public String getAsXmlString() {
        if (type == Type.MAP) {
            Node node = XmlUtils.fromMap(getValue());
            return XmlUtils.toString(node);
        } else {
            return getAsString();
        }
    }
    
    public MatchValue getSortedLike(MatchValue other) {
        if (isMap() && other.isMap()) {
            Map<String, Object> reference = other.getValue();
            Map<String, Object> source = getValue();
            Set<String> remainder = new LinkedHashSet(source.keySet());
            Map<String, Object> result = new LinkedHashMap(source.size());
            reference.keySet().forEach(key -> {
                if (source.containsKey(key)) {
                    result.put(key, source.get(key));
                    remainder.remove(key);
                }
            });
            for (String key : remainder) {
                result.put(key, source.get(key));
            }
            return new MatchValue(result);
        } else {
            return this;
        }
    } 

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[type: ").append(type);
        sb.append(", value: ").append(value);
        sb.append("]");
        return sb.toString();
    }

}
