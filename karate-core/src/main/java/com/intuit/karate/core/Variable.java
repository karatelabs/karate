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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.graal.JsFunction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class Variable {
    
    private static final Logger logger = LoggerFactory.getLogger(Variable.class);
    
    public static enum Type {
        NULL,
        BOOLEAN,
        NUMBER,
        STRING,
        BYTES,
        LIST,
        MAP,
        XML,
        JS_FUNCTION,
        JAVA_FUNCTION,
        FEATURE,
        OTHER
    }
    
    public static final Variable NULL = new Variable(null);
    public static final Variable NOT_PRESENT = new Variable("#notpresent");
    
    public final Type type;
    private final Object value;
    
    public Variable(Object o) {
        if (o instanceof Value) {
            o = new JsValue((Value) o).getValue();
        } else if (o instanceof JsValue) {
            o = ((JsValue) o).getValue();
        }
        if (o == null) {
            type = Type.NULL;
        } else if (o instanceof Value) {
            Value v = (Value) o;
            if (v.canExecute()) {
                type = Type.JS_FUNCTION;
            } else {
                type = Type.OTHER; // java.lang.Class
            }
        } else if (o instanceof Function) {
            type = Type.JAVA_FUNCTION;
        } else if (o instanceof Node) {
            type = Type.XML;
        } else if (o instanceof List) {
            type = Type.LIST;
        } else if (o instanceof Map) {
            type = Type.MAP;
        } else if (o instanceof String) {
            type = Type.STRING;
        } else if (Number.class.isAssignableFrom(o.getClass())) {
            type = Type.NUMBER;
        } else if (Boolean.class.equals(o.getClass())) {
            type = Type.BOOLEAN;
        } else if (o instanceof byte[]) {
            type = Type.BYTES;
        } else if (o instanceof Feature) {
            type = Type.FEATURE;
        } else {
            type = Type.OTHER;
        }
        value = o;
    }
    
    public <T> T getValue() {
        return (T) value;
    }
    
    public boolean isJsOrJavaFunction() {
        return type == Type.JS_FUNCTION || type == Type.JAVA_FUNCTION;
    }
    
    public boolean isJavaFunction() {
        return type == Type.JAVA_FUNCTION;
    }
    
    public boolean isJsFunction() {
        return type == Type.JS_FUNCTION;
    }
    
    public boolean isJsFunctionWrapper() {
        return value instanceof JsFunction;
    }
    
    public boolean isBytes() {
        return type == Type.BYTES;
    }
    
    public boolean isString() {
        return type == Type.STRING;
    }
    
    public boolean isList() {
        return type == Type.LIST;
    }
    
    public boolean isMap() {
        return type == Type.MAP;
    }
    
    public boolean isMapOrList() {
        return type == Type.MAP || type == Type.LIST;
    }
    
    public boolean isXml() {
        return type == Type.XML;
    }
    
    public boolean isNumber() {
        return type == Type.NUMBER;
    }
    
    public boolean isNull() {
        return type == Type.NULL;
    }
    
    public boolean isOther() {
        return type == Type.OTHER;
    }
    
    public boolean isFeature() {
        return type == Type.FEATURE;
    }
    
    public boolean isTrue() {
        return type == Type.BOOLEAN && ((Boolean) value);
    }
    
    public String getTypeString() {
        return type.name().toLowerCase();
    }
    
    public Node getAsXml() {
        switch (type) {
            case XML:
                return getValue();
            case MAP:
                return XmlUtils.fromMap(getValue());
            case STRING:
            case BYTES:
                String xml = getAsString();
                return XmlUtils.toXmlDoc(xml);
            case OTHER: // POJO
                return XmlUtils.toXmlDoc(value);
            default:
                throw new RuntimeException("cannot convert to xml:" + this);
        }
    }
    
    public Object getValueAndConvertIfXmlToMap() {
        return isXml() ? XmlUtils.toObject(getValue()) : value;
    }
    
    public Object getValueAndForceParsingAsJson() {
        switch (type) {
            case LIST:
            case MAP:
                return value;
            case STRING:
            case BYTES:
                return JsonUtils.fromJson(getAsString());
            case XML:
                return XmlUtils.toObject(getValue());
            case OTHER: // pojo
                return Json.of(value).value();
            default:
                throw new RuntimeException("cannot convert to json: " + this);
        }
        
    }
    
    public byte[] getAsByteArray() {
        if (type == Type.BYTES) {
            return getValue();
        } else {
            return FileUtils.toBytes(getAsString());
        }
    }
    
    public String getAsString() {
        switch (type) {
            case NULL:
                return null;
            case BYTES:
                return FileUtils.toString((byte[]) value);
            case LIST:
            case MAP:
                try {
                return JsonUtils.toJson(value);
            } catch (Throwable t) {
                logger.warn("conversion to json string failed, will attempt to use fall-back approach: {}", t.getMessage());
                return JsonUtils.toJsonSafe(value, false);
            }
            case XML:
                return XmlUtils.toString(getValue());
            default:
                return value.toString();
        }
    }
    
    public String getAsPrettyString() {
        switch (type) {
            case LIST:
            case MAP:
                return JsonUtils.toJsonSafe(value, true);
            case XML:
                return getAsPrettyXmlString();
            default:
                return getAsString();
        }
    }
    
    public String getAsPrettyXmlString() {
        return XmlUtils.toString(getAsXml(), true);
    }
    
    public int getAsInt() {
        if (isNumber()) {
            return ((Number) value).intValue();
        } else {
            return Integer.valueOf(getAsString());
        }
    }
    
    public Variable copy(boolean deep) {
        switch (type) {
            case LIST:
                return deep ? new Variable(JsonUtils.deepCopy(value)) : new Variable(new ArrayList((List) value));
            case MAP:
                return deep ? new Variable(JsonUtils.deepCopy(value)) : new Variable(new LinkedHashMap((Map) value));
            case XML:
                return new Variable(XmlUtils.toXmlDoc(getAsString()));
            default:
                return this;
        }
    }
    
    public Variable toLowerCase() {
        switch (type) {
            case STRING:
                return new Variable(getAsString().toLowerCase());
            case LIST:
            case MAP:
                String json = getAsString().toLowerCase();
                return new Variable(JsonUtils.fromJson(json));
            case XML:
                String xml = getAsString().toLowerCase();
                return new Variable(XmlUtils.toXmlDoc(xml));
            default:
                return this;
        }
    }
    
    public boolean isNotPresent() {
        return "#notpresent".equals(value);
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
