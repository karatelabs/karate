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

import com.intuit.karate.XmlUtils;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.core.Feature;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class Variable {        

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

    public final Type type;
    private final Object value;
    private final String description;

    public Variable(Object value) {
        this(value, null);
    }

    public Variable(Object o, String description) {
        this.description = description;
        Object tempValue = o;
        if (o == null) {
            type = Type.NULL;
        } else if (o instanceof JsValue) {
            JsValue jsValue = (JsValue) o;
            switch (jsValue.type) {
                case ARRAY:
                    tempValue = jsValue.getAsList();
                    type = Type.LIST;
                    break;
                case OBJECT:
                    tempValue = jsValue.getAsMap();
                    type = Type.MAP;
                    break;
                case FUNCTION:
                    tempValue = jsValue;
                    type = Type.JS_FUNCTION;
                    break;
                default:
                    tempValue = jsValue.getValue();
                    type = tempValue == null ? Type.NULL : Type.OTHER;
            }
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
        this.value = tempValue;
    }

    public <T> T getValue() {
        return (T) value;
    }

    public boolean isList() {
        return type == Type.LIST;
    }

    public boolean isMap() {
        return type == Type.MAP;
    }
    
    public boolean isXml() {
        return type == Type.XML;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isFunction() {
        return type == Type.JS_FUNCTION || type == Type.JAVA_FUNCTION;
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
    
    public String getAsString() {
        switch (type) {
            case NULL:
                return "null";
            default:
                return value.toString();
        }        
    }

    public String getAsPrettyString() {
        switch (type) {
            case NULL:
                return "(null)";
            default:
                return value.toString();
        }
    }

    public Object getValueForJsEngine() {
        switch (type) {
            case XML:
                return XmlUtils.toObject(getValue());
            default:
                return value;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[type: ").append(type);
        sb.append(", value: ").append(value);
        if (description != null) {
            sb.append(", description: ").append(description);
        }
        sb.append("]");
        return sb.toString();
    }

}
