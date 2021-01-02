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

import com.intuit.karate.graal.JsEngine;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class Match {

    public static enum Type {

        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        CONTAINS_ONLY,
        CONTAINS_ANY,
        CONTAINS_DEEP,
        EACH_EQUALS,
        EACH_NOT_EQUALS,
        EACH_CONTAINS,
        EACH_NOT_CONTAINS,
        EACH_CONTAINS_ONLY,
        EACH_CONTAINS_ANY,
        EACH_CONTAINS_DEEP

    }

    static final Result PASS = new Result(true, null);

    static Result fail(String message) {
        return new Result(false, message);
    }

    interface Validator extends Function<Value, Result> {
        //
    }

    static class RegexValidator implements Validator {

        private final Pattern pattern;

        public RegexValidator(String regex) {
            regex = StringUtils.trimToEmpty(regex);
            pattern = Pattern.compile(regex);
        }

        @Override
        public Result apply(Value v) {
            if (!v.isString()) {
                return fail("not a string");
            }
            String strValue = v.getValue();
            Matcher matcher = pattern.matcher(strValue);
            return matcher.matches() ? PASS : fail("regex match failed");
        }

    }

    static final Map<String, Validator> VALIDATORS = new HashMap(11);

    static {
        VALIDATORS.put("array", v -> v.isList() ? PASS : fail("not an array or list"));
        VALIDATORS.put("boolean", v -> v.isBoolean() ? PASS : fail("not a boolean"));
        VALIDATORS.put("ignore", v -> PASS);
        VALIDATORS.put("notnull", v -> v.isNull() ? fail("null") : PASS);
        VALIDATORS.put("null", v -> v.isNull() ? PASS : fail("not null"));
        VALIDATORS.put("number", v -> v.isNumber() ? PASS : fail("not a number"));
        VALIDATORS.put("object", v -> v.isMap() ? PASS : fail("not an object or map"));
        VALIDATORS.put("present", v -> v.isNotPresent() ? fail("not present") : PASS);
        VALIDATORS.put("notpresent", v -> v.isNotPresent() ? PASS : fail("present"));
        VALIDATORS.put("string", v -> v.isNotPresent() ? fail("not present") : v.isString() ? PASS : fail("not a string"));
        VALIDATORS.put("uuid", v -> {
            if (!v.isString()) {
                return fail("not a string");
            }
            try {
                UUID.fromString(v.getValue());
                return PASS;
            } catch (Exception e) {
                return fail("not a valid uuid");
            }
        });
    }

    public static class Result {

        public final String message;
        public final boolean pass;

        private Result(boolean pass, String message) {
            this.pass = pass;
            this.message = message;
        }

        @Override
        public String toString() {
            return pass ? "[pass]" : message;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap(2);
            map.put("pass", pass);
            map.put("message", message);
            return map;
        }

    }

    static class Context {

        final JsEngine JS;
        final MatchOperation root;
        final int depth;
        final boolean xml;
        final String path;
        final String name;
        final int index;

        Context(JsEngine js, MatchOperation root, boolean xml, int depth, String path, String name, int index) {
            this.JS = js;
            this.root = root;
            this.xml = xml;
            this.depth = depth;
            this.path = path;
            this.name = name;
            this.index = index;
        }

        Context descend(String name) {
            if (xml) {
                String childPath = path.endsWith("/@") ? path + name : (depth == 0 ? "" : path) + "/" + name;
                return new Context(JS, root, xml, depth + 1, childPath, name, -1);
            } else {
                boolean needsQuotes = name.indexOf('-') != -1 || name.indexOf(' ') != -1 || name.indexOf('.') != -1;
                String childPath = needsQuotes ? path + "['" + name + "']" : path + '.' + name;
                return new Context(JS, root, xml, depth + 1, childPath, name, -1);
            }
        }

        Context descend(int index) {
            if (xml) {
                return new Context(JS, root, xml, depth + 1, path + "[" + (index + 1) + "]", name, index);
            } else {
                return new Context(JS, root, xml, depth + 1, path + "[" + index + "]", name, index);
            }
        }

    }

    static enum ValueType {
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

    public static class Value {

        final ValueType type;
        final boolean exceptionOnMatchFailure;

        private final Object value;

        Value(Object value) {
            this(value, false);
        }

        Value(Object value, boolean exceptionOnMatchFailure) {
            this.value = value;
            this.exceptionOnMatchFailure = exceptionOnMatchFailure;
            if (value == null) {
                type = ValueType.NULL;
            } else if (value instanceof Node) {
                type = ValueType.XML;
            } else if (value instanceof List) {
                type = ValueType.LIST;
            } else if (value instanceof Map) {
                type = ValueType.MAP;
            } else if (value instanceof String) {
                type = ValueType.STRING;
            } else if (Number.class.isAssignableFrom(value.getClass())) {
                type = ValueType.NUMBER;
            } else if (Boolean.class.equals(value.getClass())) {
                type = ValueType.BOOLEAN;
            } else if (value instanceof byte[]) {
                type = ValueType.BYTES;
            } else {
                type = ValueType.OTHER;
            }
        }

        public boolean isBoolean() {
            return type == ValueType.BOOLEAN;
        }

        public boolean isNumber() {
            return type == ValueType.NUMBER;
        }

        public boolean isString() {
            return type == ValueType.STRING;
        }

        public boolean isNull() {
            return type == ValueType.NULL;
        }

        public boolean isMap() {
            return type == ValueType.MAP;
        }

        public boolean isList() {
            return type == ValueType.LIST;
        }

        public boolean isXml() {
            return type == ValueType.XML;
        }

        boolean isNotPresent() {
            return "#notpresent".equals(value);
        }

        boolean isMapOrListOrXml() {
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

        String getWithinSingleQuotesIfString() {
            if (type == ValueType.STRING) {
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

        String getAsXmlString() {
            if (type == ValueType.MAP) {
                Node node = XmlUtils.fromMap(getValue());
                return XmlUtils.toString(node);
            } else {
                return getAsString();
            }
        }

        Value getSortedLike(Value other) {
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
                return new Value(result, other.exceptionOnMatchFailure);
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

        public Result is(Type mt, Object o) {
            MatchOperation mo = new MatchOperation(mt, this, new Value(parseIfJsonOrXmlString(o), exceptionOnMatchFailure));
            mo.execute();
            if (mo.pass) {
                return Match.PASS;
            } else {
                if (exceptionOnMatchFailure) {
                    throw new RuntimeException(mo.getFailureReasons());
                }
                return Match.fail(mo.getFailureReasons());
            }
        }

        //======================================================================
        //
        public Result isEqualTo(Object o) {
            return is(Type.EQUALS, o);
        }

        public Result contains(Object o) {
            return is(Type.CONTAINS, o);
        }

        public Result containsDeep(Object o) {
            return is(Type.CONTAINS_DEEP, o);
        }

        public Result containsOnly(Object o) {
            return is(Type.CONTAINS_ONLY, o);
        }

        public Result containsAny(Object o) {
            return is(Type.CONTAINS_ANY, o);
        }

        public Result isNotEqualTo(Object o) {
            return is(Type.NOT_EQUALS, o);
        }

        public Result isNotContaining(Object o) {
            return is(Type.NOT_CONTAINS, o);
        }

        public Result isEachEqualTo(Object o) {
            return is(Type.EACH_EQUALS, o);
        }

        public Result isEachNotEqualTo(Object o) {
            return is(Type.EACH_NOT_EQUALS, o);
        }

        public Result isEachContaining(Object o) {
            return is(Type.EACH_CONTAINS, o);
        }

        public Result isEachNotContaining(Object o) {
            return is(Type.EACH_NOT_CONTAINS, o);
        }

        public Result isEachContainingDeep(Object o) {
            return is(Type.EACH_CONTAINS_DEEP, o);
        }

        public Result isEachContainingOnly(Object o) {
            return is(Type.EACH_CONTAINS_ONLY, o);
        }

        public Result isEachContainingAny(Object o) {
            return is(Type.EACH_CONTAINS_ANY, o);
        }

    }

    public static Result execute(JsEngine js, Type matchType, Object actual, Object expected) {
        MatchOperation mo = new MatchOperation(js, matchType, new Value(actual), new Value(expected));
        mo.execute();
        if (mo.pass) {
            return PASS;
        } else {
            return fail(mo.getFailureReasons());
        }
    }

    public static Object parseIfJsonOrXmlString(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            if (s.isEmpty()) {
                return o;
            } else if (JsonUtils.isJson(s)) {
                return Json.of(s).value();
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

    public static Value evaluate(Object o) {
        return new Value(parseIfJsonOrXmlString(o), false);
    }

    public static Value that(Object o) {
        return new Value(parseIfJsonOrXmlString(o), true);
    }

}