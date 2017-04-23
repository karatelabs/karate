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
package com.intuit.karate;

import com.intuit.karate.cucumber.FeatureWrapper;
import com.jayway.jsonpath.DocumentContext;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ClassUtils;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class ScriptValue {

    public static final ScriptValue NULL = new ScriptValue(null);

    public static enum Type {
        NULL,
        UNKNOWN,
        PRIMITIVE,
        STRING,
        MAP,
        LIST,
        JSON,
        XML,
        JS_ARRAY,
        JS_OBJECT,
        JS_FUNCTION,
        INPUT_STREAM,
        FEATURE_WRAPPER
    }

    private final Object value;
    private final Type type;

    public Object getValue() {
        return value;
    }
    
    public String getTypeAsShortString() {
        switch(type) {
            case NULL: return "null";
            case UNKNOWN: return "?";
            case PRIMITIVE: return "num";
            case STRING: return "str";
            case MAP: return "map";
            case LIST: return "list";
            case JSON: return "json";
            case XML: return "xml";
            case JS_ARRAY: return "js[]";
            case JS_OBJECT: return "js{}";
            case JS_FUNCTION: return "js()";
            case INPUT_STREAM: return "stream";
            case FEATURE_WRAPPER: return "feat";
            default: return "??";
        }
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isString() {
        return type == Type.STRING;
    }
    
    public boolean isBooleanTrue() {
        return type == Type.PRIMITIVE && "true".equals(value.toString());
    }
    
    public String getAsString() {
        switch (type) {
            case NULL:
                return null;
            case JSON:
                DocumentContext doc = getValue(DocumentContext.class);
                return doc.jsonString();                
            case XML:
                Node node = getValue(Node.class);
                if (node.getTextContent() != null) {
                    return node.getTextContent();
                } else {
                    return XmlUtils.toString(node);
                }
            case INPUT_STREAM:
                try {
                    return IOUtils.toString(getValue(InputStream.class), "utf-8");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            default:
                return value.toString();
        }
    }    

    public Object getAfterConvertingFromJsonOrXmlIfNeeded() {
        switch (type) {
            case JSON:
                DocumentContext json = getValue(DocumentContext.class);
                return json.read("$");
            case XML:
                Node xml = getValue(Node.class);
                return XmlUtils.toObject(xml);
            default:
                return getValue();
        }
    }

    public Type getType() {
        return type;
    }

    public <T> T getValue(Class<T> clazz) {
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    public ScriptValue(Object value) {
        this.value = value;
        if (value == null) {
            type = Type.NULL;
        } else if (value instanceof DocumentContext) {
            type = Type.JSON;
        } else if (value instanceof Node) {
            type = Type.XML;
        } else if (value instanceof List) {
            type = Type.LIST;
        } else if (value instanceof Map) {
            if (value instanceof ScriptObjectMirror) {
                ScriptObjectMirror som = (ScriptObjectMirror) value;
                if (som.isArray()) {
                    type = Type.JS_ARRAY;
                } else if (som.isFunction()) {
                    type = Type.JS_FUNCTION;
                } else {
                    type = Type.JS_OBJECT;
                }
            } else {
                type = Type.MAP;
            }
        } else if (value instanceof String) {
            type = Type.STRING;
        } else if (value instanceof InputStream) {
            type = Type.INPUT_STREAM;
        } else if (ClassUtils.isPrimitiveOrWrapper(value.getClass())) {
            type = Type.PRIMITIVE;
        } else if (value instanceof FeatureWrapper) {
            type = Type.FEATURE_WRAPPER;
        } else {
            type = Type.UNKNOWN;
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
