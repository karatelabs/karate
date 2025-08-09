/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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

import com.intuit.karate.MatchOperator.CoreOperator;
import com.intuit.karate.graal.JsEngine;
import java.lang.reflect.Array;
import java.util.ArrayList;
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

import static com.intuit.karate.Match.MatchOperatorFactory.*;

/**
 *
 * @author pthomas3
 */
public class Match {


    // Enum constant with that value should never be returned by Match.macroToMatchType.
    private static final int TYPE_DOES_NOT_SUPPORT_MACRO_SHORTCUT = -1;

    interface MatchOperatorFactory {
        MatchOperator create(boolean matchEachEmptyAllowed);

        static MatchOperatorFactory not(CoreOperatorFactory delegateFactory, String failureMessage) {
            return matchEachEmptyAllowed -> new MatchOperator.NotOperator(delegateFactory.create(matchEachEmptyAllowed), failureMessage);
        }

        static MatchOperatorFactory deep(CoreOperatorFactory delegateFactory) {
            return matchEachEmptyAllowed -> delegateFactory.create(matchEachEmptyAllowed).deep();
        }

        static MatchOperatorFactory each(MatchOperatorFactory delegateFactory) {
            return matchEachEmptyAllowed -> new MatchOperator.EachOperator(delegateFactory.create(matchEachEmptyAllowed), matchEachEmptyAllowed);
        }
    }

    interface CoreOperatorFactory extends MatchOperatorFactory {
        @Override
        CoreOperator create(boolean matchEachEmptyAllowed);
    }

    public static enum Type {

        EQUALS(CoreOperator::equalsOperator, 0),
        NOT_EQUALS(not(CoreOperator::equalsOperator, "equals"), 2),
        CONTAINS(CoreOperator::containsOperator, 1),
        NOT_CONTAINS(not(CoreOperator::containsOperator, "actual contains expected"), 2),
        CONTAINS_ONLY(CoreOperator::containsOnlyOperator, 2),
        CONTAINS_ANY(CoreOperator::containsAnyOperator, 2),
        CONTAINS_DEEP(deep(CoreOperator::containsOperator), 2),
        CONTAINS_ONLY_DEEP(deep(CoreOperator::containsOnlyOperator), TYPE_DOES_NOT_SUPPORT_MACRO_SHORTCUT),
        CONTAINS_ANY_DEEP(deep(CoreOperator::containsAnyOperator), TYPE_DOES_NOT_SUPPORT_MACRO_SHORTCUT),
        EACH_EQUALS(each(EQUALS.operatorFactory), 0),
        EACH_NOT_EQUALS(each(NOT_EQUALS.operatorFactory), 2),
        EACH_CONTAINS(each(CONTAINS.operatorFactory), 1),
        EACH_NOT_CONTAINS(each(NOT_CONTAINS.operatorFactory), 2),
        EACH_CONTAINS_ONLY(each(CONTAINS_ONLY.operatorFactory), 2),
        EACH_CONTAINS_ANY(each(CONTAINS_ANY.operatorFactory), 2),
        EACH_CONTAINS_DEEP(each(CONTAINS_DEEP.operatorFactory), 2);


        final MatchOperatorFactory operatorFactory;
        final int shortcutLength;

        Type(MatchOperatorFactory operatorFactory, int shortcutLength) {
            this.operatorFactory = operatorFactory;
            this.shortcutLength = shortcutLength;
        }

        MatchOperator operator(boolean matchEachEmptyAllowed) {
            return operatorFactory.create(matchEachEmptyAllowed);
        }
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

    static final Map<String, Validator> VALIDATORS = new HashMap<>(11);

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
            Map<String, Object> map = new HashMap<>(2);
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
            if (value instanceof Set<?> set) {
                value = new ArrayList<Object>(set);
            } else if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(Array.get(value, i));
                }
                value = list;
            }
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

        boolean isArrayObjectOrReference() {
            String temp = value.toString();
            return temp.startsWith("#[")
                || temp.startsWith("##[")
                || temp.startsWith("#(")
                || temp.startsWith("##(")                  
                || "#array".equals(temp)
                || "##array".equals(temp)
                || "#object".equals(temp)
                || "##object".equals(temp);
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
                Set<String> remainder = new LinkedHashSet<>(source.keySet());
                Map<String, Object> result = new LinkedHashMap<>(source.size());
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
            return "[type: " + type +
                    ", value: " + value +
                    "]";
        }

        public Result is(Type matchType, Object expected) {
            MatchOperation mo = new MatchOperation(matchType.operator(false), this, new Value(parseIfJsonOrXmlString(expected), exceptionOnMatchFailure));
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
        public Result isEqualTo(Object expected) {
            return is(Type.EQUALS, expected);
        }

        public Result contains(Object expected) {
            return is(Type.CONTAINS, expected);
        }

        public Result containsDeep(Object expected) {
            return is(Type.CONTAINS_DEEP, expected);
        }

        public Result containsOnly(Object expected) {
            return is(Type.CONTAINS_ONLY, expected);
        }
        
        public Result containsOnlyDeep(Object expected) {
            return is(Type.CONTAINS_ONLY_DEEP, expected);
        }        

        public Result containsAny(Object expected) {
            return is(Type.CONTAINS_ANY, expected);
        }

        public Result isNotEqualTo(Object expected) {
            return is(Type.NOT_EQUALS, expected);
        }

        public Result isNotContaining(Object expected) {
            return is(Type.NOT_CONTAINS, expected);
        }

        public Result isEachEqualTo(Object expected) {
            return is(Type.EACH_EQUALS, expected);
        }

        public Result isEachNotEqualTo(Object expected) {
            return is(Type.EACH_NOT_EQUALS, expected);
        }

        public Result isEachContaining(Object expected) {
            return is(Type.EACH_CONTAINS, expected);
        }

        public Result isEachNotContaining(Object expected) {
            return is(Type.EACH_NOT_CONTAINS, expected);
        }

        public Result isEachContainingDeep(Object expected) {
            return is(Type.EACH_CONTAINS_DEEP, expected);
        }

        public Result isEachContainingOnly(Object expected) {
            return is(Type.EACH_CONTAINS_ONLY, expected);
        }

        public Result isEachContainingAny(Object expected) {
            return is(Type.EACH_CONTAINS_ANY, expected);
        }

    }

    public static Result execute(JsEngine js, Type matchType, Object actual, Object expected, boolean matchEachEmptyAllowed) {
        MatchOperation mo = new MatchOperation(js, matchType.operator(matchEachEmptyAllowed), new Value(actual), new Value(expected));
        mo.execute();
        if (mo.pass) {
            return PASS;
        } else {
            return fail(mo.getFailureReasons());
        }
    }

    public static Object parseIfJsonOrXmlString(Object o) {
        if (o instanceof String s) {
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

    public static Value evaluate(Object actual) {
        return new Value(parseIfJsonOrXmlString(actual), false);
    }

    public static Value that(Object actual) {
        return new Value(parseIfJsonOrXmlString(actual), true);
    }

}
