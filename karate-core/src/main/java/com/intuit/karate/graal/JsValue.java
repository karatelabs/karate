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
package com.intuit.karate.graal;

import com.intuit.karate.FileUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.http.ResourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class JsValue {

    private static final Logger logger = LoggerFactory.getLogger(JsValue.class);

    public static enum Type {
        OBJECT,
        ARRAY,
        FUNCTION,
        XML,
        NULL,
        OTHER
    }

    public static final JsValue NULL = new JsValue(Value.asValue(null));

    private final Value original;
    private final Object value;
    public final Type type;

    public JsValue(Value v) {
        if (v == null) {
            throw new RuntimeException("JsValue() constructor argument has to be not-null");
        }
        this.original = v;
        try {
            if (v.isNull()) {
                value = null;
                type = Type.NULL;
            } else if (v.isHostObject()) {
                if (v.isMetaObject()) { // java.lang.Class !
                    value = v; // special case, keep around as graal value
                } else {
                    value = v.asHostObject();
                }
                type = Type.OTHER;
            } else if (v.isProxyObject()) {
                Object o = v.asProxyObject();
                if (o instanceof JsXml) {
                    value = ((JsXml) o).getNode();
                    type = Type.XML;
                } else if (o instanceof JsMap) {
                    value = ((JsMap) o).getMap();
                    type = Type.OBJECT;
                } else if (o instanceof JsList) {
                    value = ((JsList) o).getList();
                    type = Type.ARRAY;
                } else if (o instanceof ProxyExecutable) {
                    value = o;
                    type = Type.FUNCTION;
                } else { // e.g. custom bridge, e.g. Request
                    value = v.as(Object.class);
                    type = Type.OTHER;
                }
            } else if (v.hasArrayElements()) {
                int size = (int) v.getArraySize();
                List list = new ArrayList(size);
                for (int i = 0; i < size; i++) {
                    Value child = v.getArrayElement(i);
                    list.add(new JsValue(child).value);
                }
                value = list;
                type = Type.ARRAY;
            } else if (v.hasMembers()) {
                if (v.canExecute()) {
                    if (v.canInstantiate()) {
                        // js functions have members, can be executed and are instantiable
                        value = new SharableMembersAndInstantiable(v);
                    } else {
                        value = new SharableMembersAndExecutable(v);
                    }
                    type = Type.FUNCTION;
                } else {
                    Set<String> keys = v.getMemberKeys();
                    Map<String, Object> map = new LinkedHashMap(keys.size());
                    for (String key : keys) {
                        Value child = v.getMember(key);
                        map.put(key, new JsValue(child).value);
                    }
                    value = map;
                    type = Type.OBJECT;
                }
            } else if (v.isNumber()) {
                value = v.as(Number.class);
                type = Type.OTHER;
            } else if (v.isBoolean()) {
                value = v.asBoolean();
                type = Type.OTHER;
            } else if (v.isString()) {
                value = v.asString();
                type = Type.OTHER;
            } else {
                value = v.as(Object.class);
                if (value instanceof Function) {
                    type = Type.FUNCTION;
                } else {
                    type = Type.OTHER;
                }                
            }
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace("js conversion failed", e);
            }
            throw e;
        }
    }

    public <T> T getValue() {
        return (T) value;
    }

    public Map<String, Object> getAsMap() {
        return (Map) value;
    }

    public List getAsList() {
        return (List) value;
    }

    public Value getOriginal() {
        return original;
    }

    public boolean isXml() {
        return type == Type.XML;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isTrue() {
        if (type != Type.OTHER || !Boolean.class.equals(value.getClass())) {
            return false;
        }
        return (Boolean) value;
    }

    public boolean isFunction() {
        return type == Type.FUNCTION;
    }

    public boolean isOther() {
        return type == Type.OTHER;
    }

    @Override
    public String toString() {
        return original.toString();
    }

    public String toJsonOrXmlString(boolean pretty) {
        return toString(value, pretty);
    }

    public String getAsString() {
        return JsValue.toString(value);
    }

    public static Object fromJava(Object o) {
        if (o instanceof Function || o instanceof Proxy) {
            return o;
        } else if (o instanceof List) {
            return new JsList((List) o);
        } else if (o instanceof Map) {
            return new JsMap((Map) o);
        } else if (o instanceof Node) {
            return new JsXml((Node) o);
        } else {
            return o;
        }
    }

    public static Object toJava(Value v) {
        return new JsValue(v).getValue();
    }

    public static Object unWrap(Object o) {
        if (o instanceof JsXml) {
            return ((JsXml) o).getNode();
        } else if (o instanceof JsMap) {
            return ((JsMap) o).getMap();
        } else if (o instanceof JsList) {
            return ((JsList) o).getList();
        } else {
            return o;
        }
    }

    public static byte[] toBytes(Value v) {
        return toBytes(toJava(v));
    }

    public static String toString(Object o) {
        return toString(o, false);
    }

    public static String toString(Object o, boolean pretty) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map || o instanceof List) {
            return JsonUtils.toJson(o, pretty);
        } else if (o instanceof Node) {
            return XmlUtils.toString((Node) o, pretty);
        } else if (o instanceof byte[]) {
            return FileUtils.toString((byte[]) o);
        } else {
            return o.toString();
        }
    }

    public static byte[] toBytes(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Map || o instanceof List) {
            return FileUtils.toBytes(JsonUtils.toJson(o));
        } else if (o instanceof Node) {
            return FileUtils.toBytes(XmlUtils.toString((Node) o));
        } else if (o instanceof byte[]) {
            return (byte[]) o;
        } else {
            return FileUtils.toBytes(o.toString());
        }
    }

    public static Object fromBytes(byte[] bytes, boolean strict, ResourceType resourceType) {
        if (bytes == null) {
            return null;
        }
        String raw = FileUtils.toString(bytes);
        return fromString(raw, strict, resourceType);
    }

    public static Object fromString(String raw, boolean strict, ResourceType resourceType) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return raw;
        }
        if (resourceType != null && resourceType.isBinary()) {
            return raw;
        }
        switch (trimmed.charAt(0)) {
            case '{':
            case '[':
                if (strict) {
                    return JsonUtils.fromJsonStrict(raw);
                }
                try {
                    return JsonUtils.fromJson(raw);
                } catch (Exception e) {
                    logger.trace("failed to parse json: {}", e.getMessage());
                    return raw;
                }
            case '<':
                if (resourceType == null || resourceType.isXml()) {
                    try {
                        return XmlUtils.toXmlDoc(raw);
                    } catch (Exception e) {
                        logger.trace("failed to parse xml: {}", e.getMessage());
                        if (strict) {
                            throw e;
                        }
                        return raw;
                    }
                } else {
                    return raw;
                }
            default:
                return raw;
        }
    }

    public static Object fromStringSafe(String raw) {
        try {
            return fromString(raw, false, null);
        } catch (Exception e) {
            logger.trace("failed to auto convert: {}", e + "");
            return raw;
        }
    }

    public static boolean isTruthy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean) {
            return ((Boolean) o);
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue() != 0.0;
        }
        return true;
    }

    static class SharableMembers implements ProxyObject {

        final Value v;

        SharableMembers(Value v) {
            this.v = v;
        }

        @Override
        public void putMember(String key, Value value) {
            v.putMember(key, new JsValue(value).value);
        }

        @Override
        public boolean hasMember(String key) {
            return v.hasMember(key);
        }

        @Override
        public Object getMemberKeys() {
            return v.getMemberKeys().toArray(new String[0]);
        }

        @Override
        public Object getMember(String key) {
            return new JsValue(v.getMember(key)).value;
        }

        @Override
        public boolean removeMember(String key) {
            return v.removeMember(key);
        }
    }
    
    public static final Object LOCK = new Object();

    static class SharableMembersAndExecutable extends SharableMembers implements ProxyExecutable {

        SharableMembersAndExecutable(Value v) {
            super(v);
        }

        @Override
        public Object execute(Value... args) {
            Object[] newArgs = new Object[args.length];
            // the synchronized block should include the pre-processing of arguments
            synchronized (LOCK) {
                for (int i = 0; i < newArgs.length; i++) {
                    newArgs[i] = new JsValue(args[i]).value;
                }
                Value result = v.execute(newArgs);
                return new JsValue(result).value;
            }
        }                

    }

    static class SharableMembersAndInstantiable extends SharableMembersAndExecutable implements ProxyInstantiable {

        SharableMembersAndInstantiable(Value v) {
            super(v);
        }

        @Override
        public Object newInstance(Value... args) {
            Object[] newArgs = new Object[args.length];
            // the synchronized block should include the pre-processing of arguments
            synchronized (LOCK) {            
                for (int i = 0; i < newArgs.length; i++) {
                    newArgs[i] = new JsValue(args[i]).value;
                }
                return new JsValue(v.execute(newArgs)).value;
            }
        }

    }

}
