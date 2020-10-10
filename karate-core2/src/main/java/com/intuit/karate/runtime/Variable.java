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
package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.core.Feature;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
        KARATE_FEATURE,
        OTHER
    }

    public static final Variable NULL = new Variable(null);
    public static final Variable NOT_PRESENT = new Variable("#notpresent");

    public final Type type;
    private final Object value;

    public Variable(Object o) {
        if (o instanceof JsValue) {
            JsValue jsValue = (JsValue) o;
            if (!jsValue.isFunction()) { // only in case of JS_FUNCTION keep the JsValue as-is
                o = jsValue.getValue();
            }
        }
        value = o;
        if (o == null) {
            type = Type.NULL;
        } else if (o instanceof JsValue) {
            type = Type.JS_FUNCTION; // see logic above
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
            type = Type.KARATE_FEATURE;
        } else if (o instanceof Function) {
            type = Type.JAVA_FUNCTION;
        } else {
            type = Type.OTHER;
        }
    }

    public <T> T getValue() {
        return (T) value;
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

    public boolean isFunction() {
        return type == Type.JS_FUNCTION || type == Type.JAVA_FUNCTION;
    }

    public boolean isKarateFeature() {
        return type == Type.KARATE_FEATURE;
    }

    public boolean isTrue() {
        return type == Type.BOOLEAN && ((Boolean) value);
    }

    public Variable invokeFunction(Object... args) {
        if (type == Type.JS_FUNCTION) {
            JsValue jsValue = getValue();
            JsValue result = jsValue.execute(args);
            return new Variable(result);
        } else {
            Function function = getValue();
            Object result = function.apply(args);
            return new Variable(result);
        }
    }

    public Map<String, Object> evalAsMap() {
        if (isFunction()) {
            Variable v = invokeFunction();
            return v.isMap() ? v.getValue() : null;
        } else {
            return isMap() ? getValue() : null;
        }
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

    public Object getValueForJsonConversion() {
        switch (type) {
            case LIST:
            case MAP:
                return value;
            case STRING:
            case BYTES:
                String json = getAsString();
                return JsonUtils.fromJsonString(json);
            case XML:
                return XmlUtils.toObject(getValue());
            case OTHER: // pojo
                return new Json(value).asMapOrList();
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
                return JsonUtils.toJson(value);
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
            default:
                return getAsString();
        }
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[type: ").append(type);
        sb.append(", value: ").append(value);
        sb.append("]");
        return sb.toString();
    }

}
