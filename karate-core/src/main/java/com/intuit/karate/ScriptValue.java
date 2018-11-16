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

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.Feature;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
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
        BYTE_ARRAY,
        INPUT_STREAM,
        FEATURE
    }

    private final Object value;
    private final Type type;
    private final String source; // file this came from, for better debug / logging

    public Object getValue() {
        return value;
    }

    public String getTypeAsShortString() {
        switch (type) {
            case NULL:
                return "null";
            case UNKNOWN:
                return "?";
            case PRIMITIVE:
                return "num";
            case STRING:
                return "str";
            case MAP:
                return "map";
            case LIST:
                return "list";
            case JSON:
                return "json";
            case XML:
                return "xml";
            case JS_ARRAY:
                return "js[]";
            case JS_OBJECT:
                return "js{}";
            case JS_FUNCTION:
                return "js()";
            case BYTE_ARRAY:
                return "byte[]";
            case INPUT_STREAM:
                return "stream";
            case FEATURE:
                return "feature";
            default:
                return "???";
        }
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isStringOrStream() {
        return isString() || isStream();
    }

    public boolean isXml() {
        return type == Type.XML;
    }

    public boolean isStream() {
        return type == Type.INPUT_STREAM;
    }

    public boolean isByteArray() {
        return type == Type.BYTE_ARRAY;
    }

    public boolean isUnknownType() {
        return type == Type.UNKNOWN;
    }

    public boolean isBooleanTrue() {
        return type == Type.PRIMITIVE && "true".equals(value.toString());
    }

    public boolean isPrimitive() {
        return type == Type.PRIMITIVE;
    }

    public Number getAsNumber() {
        return getValue(Number.class);
    }

    public boolean isNumber() {
        return type == Type.PRIMITIVE && Number.class.isAssignableFrom(value.getClass());
    }

    public boolean isFunction() {
        return type == Type.JS_FUNCTION;
    }

    public boolean isListLike() {
        switch (type) {
            case JS_ARRAY:
            case LIST:
                return true;
            case JSON:
                DocumentContext doc = (DocumentContext) value;
                return doc.json() instanceof List;
            default:
                return false;
        }
    }

    public List getAsList() {
        switch (type) {
            case JS_ARRAY:
                ScriptObjectMirror som = (ScriptObjectMirror) value;
                return new ArrayList(som.values());
            case LIST:
                return getValue(List.class);
            case JSON:
                DocumentContext doc = (DocumentContext) value;
                return doc.json();
            default:
                throw new RuntimeException("cannot convert to list: " + this);
        }
    }

    public boolean isJsonLike() {
        switch (type) {
            case JSON:
            case MAP:
            case JS_OBJECT:
            case JS_ARRAY:
            case LIST:
                return true;
            default:
                return false;
        }
    }

    public ScriptValue copy() {
        switch (type) {
            case NULL:
            case UNKNOWN:
            case PRIMITIVE:
            case STRING:
            case BYTE_ARRAY:
            case INPUT_STREAM:
            case FEATURE:
            case JS_FUNCTION:
                return this;
            case XML:
                String xml = XmlUtils.toString(getValue(Node.class));
                return new ScriptValue(XmlUtils.toXmlDoc(xml));
            case JSON:
                String json = getValue(DocumentContext.class).jsonString();
                return new ScriptValue(JsonPath.parse(json));
            case JS_OBJECT: // is a map-like object, happens for json resulting from nashorn
            case MAP:
                Map map = getValue(Map.class);
                return new ScriptValue(new LinkedHashMap(map));
            case JS_ARRAY:
                ScriptObjectMirror som = getValue(ScriptObjectMirror.class);
                return new ScriptValue(new ArrayList(som.values()));
            case LIST:
                List list = getValue(List.class);
                return new ScriptValue(new ArrayList(list));
            default:
                return this;
        }
    }

    public DocumentContext getAsJsonDocument() {
        switch (type) {
            case JSON:
                return getValue(DocumentContext.class);
            case JS_ARRAY: // happens for json resulting from nashorn
                ScriptObjectMirror som = getValue(ScriptObjectMirror.class);
                return JsonPath.parse(som.values());
            case JS_OBJECT: // is a map-like object, happens for json resulting from nashorn
            case MAP: // this happens because some jsonpath operations result in Map
                Map<String, Object> map = getValue(Map.class);
                return JsonPath.parse(map);
            case LIST: // this also happens because some jsonpath operations result in List
                List list = getValue(List.class);
                return JsonPath.parse(list);
            default:
                throw new RuntimeException("cannot convert to json: " + this);
        }
    }

    public boolean isMapLike() {
        switch (type) {
            case JSON:
                DocumentContext doc = (DocumentContext) value;
                return doc.json() instanceof Map;
            case MAP:
            case XML:
            case JS_OBJECT:
                return true;
            default:
                return false;
        }
    }

    public Map<String, Object> getAsMap() {
        switch (type) {
            case MAP:
            case JS_OBJECT:
                return getValue(Map.class);
            case JSON:
                DocumentContext json = getValue(DocumentContext.class);
                return json.read("$");
            case XML:
                Node xml = getValue(Node.class);
                return (Map) XmlUtils.toObject(xml);
            default:
                throw new RuntimeException("cannot convert to map: " + this);
        }
    }

    public ScriptValue invokeFunction(ScenarioContext context, Object callArg) {
        ScriptObjectMirror som = getValue(ScriptObjectMirror.class);
        return Script.evalFunctionCall(som, callArg, context);
    }

    public Map<String, Object> evalAsMap(ScenarioContext context) {
        if (isFunction()) {
            ScriptValue sv = invokeFunction(context, null);
            return sv.isMapLike() ? sv.getAsMap() : null;
        } else {
            return isMapLike() ? getAsMap() : null;
        }
    }

    public String getAsPrettyString() {
        switch (type) {
            case NULL:
                return "";
            case XML:
                Node node = getValue(Node.class);
                return XmlUtils.toString(node, true);
            case JSON:
                DocumentContext doc = getValue(DocumentContext.class);
                return JsonUtils.toPrettyJsonString(doc);
            case JS_ARRAY:
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return JsonUtils.toPrettyJsonString(listDoc);
            case JS_OBJECT:
            case MAP:
                Map map = getAsMap();
                DocumentContext mapDoc = JsonPath.parse(map);
                return JsonUtils.toPrettyJsonString(mapDoc);
            case BYTE_ARRAY:
                return "(..bytes..)";
            case INPUT_STREAM:
                return "(..stream..)";
            case UNKNOWN:
                return "(..???..)";
            default:
                return value.toString();
        }
    }

    public Object toLowerCase() {
        switch (type) {
            case JSON:
                DocumentContext doc = getValue(DocumentContext.class);
                return JsonUtils.toJsonDoc(doc.jsonString().toLowerCase());
            case XML:
                Node node = getValue(Node.class);
                return XmlUtils.toXmlDoc(XmlUtils.toString(node).toLowerCase());
            case JS_ARRAY:
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return JsonUtils.toJsonDoc(listDoc.jsonString().toLowerCase()).read("$");
            case JS_OBJECT:
            case MAP:
                DocumentContext mapDoc = JsonPath.parse(getAsMap());
                return JsonUtils.toJsonDoc(mapDoc.jsonString().toLowerCase()).read("$");
            case INPUT_STREAM:
                return FileUtils.toString(getValue(InputStream.class)).toLowerCase();
            case STRING:
                return value.toString().toLowerCase();
            default: // NULL, UNKNOWN, JS_FUNCTION, BYTE_ARRAY, PRIMITIVE
                return value;
        }
    }

    public int getAsInt() {
        if (isNumber()) {
            return getAsNumber().intValue();
        } else {
            return Integer.valueOf(getAsString());
        }
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
                if (node.getTextContent() != null) { // for attributes, text() etc
                    return node.getTextContent();
                } else {
                    return XmlUtils.toString(node);
                }
            case JS_ARRAY:
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return listDoc.jsonString();
            case JS_OBJECT:
            case MAP:
                DocumentContext mapDoc = JsonPath.parse(getAsMap());
                return mapDoc.jsonString();
            case JS_FUNCTION:
                return value.toString().replace("\n", " ");
            case BYTE_ARRAY:
                return FileUtils.toString(getValue(byte[].class));
            case INPUT_STREAM:
                return FileUtils.toString(getValue(InputStream.class));
            default:
                return value.toString();
        }
    }

    public byte[] getAsByteArray() {
        switch (type) {
            case NULL:
                return null;
            case INPUT_STREAM:
                return FileUtils.toBytes(getValue(InputStream.class));
            case BYTE_ARRAY:
                return getValue(byte[].class);
            default:
                return getAsString().getBytes();
        }
    }

    public InputStream getAsStream() {
        switch (type) {
            case NULL:
                return null;
            case INPUT_STREAM:
                return getValue(InputStream.class);
            default:
                return new ByteArrayInputStream(getAsString().getBytes());
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
        this(value, null);
    }

    public ScriptValue(Object value, String source) {
        this.value = value;
        this.source = source;
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
        } else if (value instanceof byte[]) {
            type = Type.BYTE_ARRAY;
        } else if (value instanceof InputStream) {
            type = Type.INPUT_STREAM;
        } else if (Script.isPrimitive(value.getClass())) {
            type = Type.PRIMITIVE;
        } else if (value instanceof Feature) {
            type = Type.FEATURE;
        } else {
            type = Type.UNKNOWN;
        }
    }

    public String toPrettyString(String key) {
        StringBuilder sb = new StringBuilder();
        String description = key + " (" + getTypeAsShortString() + "): ";
        sb.append(description);
        String temp = null;
        try {
            temp = getAsPrettyString();
        } catch (Exception e) {
            e.printStackTrace();
            temp = e.getMessage();
        }
        if (temp != null && temp.indexOf('\n') != -1) {
            String dashes = StringUtils.repeat('-', description.length() - 1);
            sb.append('\n').append(dashes).append('\n');
        }
        sb.append(temp).append('\n');
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[type: ").append(type);
        sb.append(", value: ").append(value);
        if (source != null) {
            sb.append(", source: ").append(source);
        }
        sb.append("]");
        return sb.toString();
    }

}
