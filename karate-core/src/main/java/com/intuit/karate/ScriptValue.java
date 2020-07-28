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

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.ScenarioContext;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class ScriptValue {

    public static final ScriptValue NULL = new ScriptValue(null);
    public static final ScriptValue FALSE = new ScriptValue(false);
    public static final ScriptValue ZERO = new ScriptValue(0);

    public static enum Type {
        NULL,
        UNKNOWN,
        PRIMITIVE,
        STRING,
        MAP,
        LIST,
        JSON,
        XML,
        JS_FUNCTION,
        BYTE_ARRAY,
        INPUT_STREAM,
        FEATURE,
        JAVA_FUNCTION
    }

    private final Object value;
    private final Type type;
    private boolean listLike;
    private boolean mapLike;
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
            case JS_FUNCTION:
                return "js()";
            case BYTE_ARRAY:
                return "byte[]";
            case INPUT_STREAM:
                return "stream";
            case FEATURE:
                return "feature";
            case JAVA_FUNCTION:
                return "java()";
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

    public boolean isFeature() {
        return type == Type.FEATURE;
    }

    public boolean isUnknown() {
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
        return listLike;
    }

    public List getAsList() {
        switch (type) {
            case LIST:
                return getValue(List.class);
            case JSON:
                DocumentContext doc = (DocumentContext) value;
                return doc.json();
            default:
                throw new RuntimeException("cannot convert to list: " + this);
        }
    }

    public boolean isJson() {
        return type == Type.JSON;
    }

    public boolean isJsonLike() {
        switch (type) {
            case JSON:
            case MAP:
            case LIST:
                return true;
            default:
                return false;
        }
    }

    // here deep means even for java List and Map, we convert to JSON and re-marshal
    public ScriptValue copy(boolean deep) {
        switch (type) {
            case NULL:
            case UNKNOWN:
            case PRIMITIVE:
            case STRING:
            case BYTE_ARRAY:
            case INPUT_STREAM:
            case FEATURE:
            case JS_FUNCTION:
            case JAVA_FUNCTION:
                return this;
            case XML:
                String xml = XmlUtils.toString(getValue(Node.class));
                return new ScriptValue(XmlUtils.toXmlDoc(xml));
            case JSON:
                String json = getValue(DocumentContext.class).jsonString();
                return new ScriptValue(JsonPath.parse(json));
            case MAP:
                if (deep) {
                    Map mapSource = getValue(Map.class);
                    Map mapDest;
                    try {
                        String strSource = JsonPath.parse(mapSource).jsonString();
                        mapDest = JsonPath.parse(strSource).read("$");
                        // only care about JS functions for treating specially
                        retainRootKeyValuesWhichAreFunctions(mapSource, mapDest, false);
                    } catch (Throwable t) { // json serialization failed, fall-back
                        mapDest = new LinkedHashMap(mapSource);
                    }
                    return new ScriptValue(mapDest);
                } else {
                    return new ScriptValue(new LinkedHashMap(getValue(Map.class)));
                }
            case LIST:
                if (deep) {
                    String strList = getAsJsonDocument().jsonString();
                    return new ScriptValue(JsonPath.parse(strList));
                } else {
                    return new ScriptValue(new ArrayList(getValue(List.class)));
                }
            default:
                return this;
        }
    }

    public DocumentContext getAsJsonDocument() {
        switch (type) {
            case JSON:
                return getValue(DocumentContext.class);
            case MAP:
                Map<String, Object> map = getValue(Map.class);
                return JsonPath.parse(map);
            case LIST:
                List list = getValue(List.class);
                return JsonPath.parse(list);
            default:
                throw new RuntimeException("cannot convert to json: " + this);
        }
    }

    public boolean isMapLike() {
        return mapLike;
    }

    public Map<String, Object> getAsMap() {
        switch (type) {
            case MAP:
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
        return Script.evalJsFunctionCall(som, callArg, context);
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
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return JsonUtils.toPrettyJsonString(listDoc);
            case MAP:
                Map map = getAsMap();
                DocumentContext mapDoc = JsonPath.parse(map);
                return JsonUtils.toPrettyJsonString(mapDoc);
            case BYTE_ARRAY:
                return "(..bytes..)";
            case INPUT_STREAM:
                return "(..stream..)";
            case UNKNOWN: // fall through
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
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return JsonUtils.toJsonDoc(listDoc.jsonString().toLowerCase()).read("$");
            case MAP:
                DocumentContext mapDoc = JsonPath.parse(getAsMap());
                return JsonUtils.toJsonDoc(mapDoc.jsonString().toLowerCase()).read("$");
            case INPUT_STREAM:
                return FileUtils.toString(getValue(InputStream.class)).toLowerCase();
            case STRING:
                return value.toString().toLowerCase();
            default: // NULL, UNKNOWN, JS_FUNCTION, JAVA_FUNCTION, BYTE_ARRAY, PRIMITIVE
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

    public String getAsStringRemovingCyclicReferences() {
        switch (type) {
            case JSON:
            case MAP:
                Map map = JsonUtils.removeCyclicReferences(getAsMap());
                return JsonUtils.toJsonDoc(map).jsonString();
            default:
                return getAsString();
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
            case LIST:
                List list = getAsList();
                DocumentContext listDoc = JsonPath.parse(list);
                return listDoc.jsonString();
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

    private static void retainRootKeyValuesWhichAreFunctions(Map source, Map target, boolean overWriteAll) {
        source.forEach((k, v) -> { // check if any special objects need to be preserved
            if (v instanceof ScriptObjectMirror) {
                ScriptObjectMirror child = (ScriptObjectMirror) v;
                if (child.isFunction()) { // only 1st level JS functions will be retained
                    target.put(k, child);
                }
            } else if (overWriteAll) { // only 1st level non-JS (e.g. Java) objects will be retained
                target.put(k, v);
            }
        });
    }

    public ScriptValue(Object value, String source) {
        // pre-process and convert any nashorn js objects into vanilla Map / List
        if (value instanceof ScriptObjectMirror) {
            ScriptObjectMirror som = (ScriptObjectMirror) value;
            if (!som.isFunction()) {
                Object o = JsonUtils.nashornObjectToJavaJSON(value);
                value = JsonPath.parse(o).read("$"); // results in Map or List
                if (value instanceof Map) {
                    Map map = (Map) value;
                    retainRootKeyValuesWhichAreFunctions(som, map, true);
                }
            }
        }
        this.value = value;
        this.source = source;
        if (value == null) {
            type = Type.NULL;
        } else if (value instanceof DocumentContext) {
            DocumentContext doc = (DocumentContext) value;
            listLike = doc.json() instanceof List;
            mapLike = !listLike;
            type = Type.JSON;
        } else if (value instanceof Node) {
            mapLike = true;
            type = Type.XML;
        } else if (value instanceof List) {
            listLike = true;
            type = Type.LIST;
        } else if (value instanceof ScriptObjectMirror) { // has to be before Map
            type = Type.JS_FUNCTION;
        } else if (value instanceof Map) {
            mapLike = true;
            type = Type.MAP;
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
        } else if (value instanceof Function) {
            type = Type.JAVA_FUNCTION;
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
