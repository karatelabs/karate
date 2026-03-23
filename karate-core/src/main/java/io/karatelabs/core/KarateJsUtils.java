/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.core;

import io.karatelabs.common.DataUtils;
import io.karatelabs.common.Json;
import io.karatelabs.common.OsUtils;
import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.JavaCallable;
import org.w3c.dom.Node;

import com.jayway.jsonpath.JsonPath;

import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless utility methods for the karate.* JavaScript API.
 * <p>
 * Contains pure functions that don't require access to:
 * - The JavaScript engine or variable scope
 * - Runtime context ({@link KarateJsContext})
 * - HTTP client or resource resolution
 * <p>
 * Methods here operate only on their input arguments and return results.
 * This allows them to be tested in isolation and reused across contexts.
 *
 * @see KarateJs for stateful methods that require engine access
 * @see KarateJsBase for shared state and infrastructure
 * @see KarateJsContext for runtime context interface
 */
public class KarateJsUtils {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    private KarateJsUtils() {
        // utility class
    }

    // ========== Collection Utilities ==========

    static JavaInvokable append() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("append() needs at least two arguments");
            }
            List<Object> result = new ArrayList<>();
            Object first = args[0];
            if (first instanceof List) {
                result.addAll((List<?>) first);
            } else {
                result.add(first);
            }
            for (int i = 1; i < args.length; i++) {
                Object item = args[i];
                if (item instanceof List) {
                    result.addAll((List<?>) item);
                } else {
                    result.add(item);
                }
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable appendTo() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("appendTo() needs at least two arguments: list and item(s)");
            }
            List<Object> list = (List<Object>) args[0];
            for (int i = 1; i < args.length; i++) {
                Object item = args[i];
                if (item instanceof List) {
                    list.addAll((List<?>) item);
                } else {
                    list.add(item);
                }
            }
            return list;
        };
    }

    /**
     * Remove duplicates from a list while preserving order.
     * Usage: karate.distinct([1, 2, 2, 3, 1]) => [1, 2, 3]
     */
    static JavaInvokable distinct() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return new ArrayList<>();
            }
            if (!(args[0] instanceof List<?> list)) {
                return new ArrayList<>();
            }
            Set<Object> seen = new LinkedHashSet<>(list);
            return new ArrayList<>(seen);
        };
    }

    static JavaInvokable extract() {
        return args -> {
            if (args.length < 3) {
                throw new RuntimeException("extract() needs three arguments: text, regex, group");
            }
            String text = args[0].toString();
            String regex = args[1].toString();
            int group = ((Number) args[2]).intValue();
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) {
                return null;
            }
            return matcher.group(group);
        };
    }

    static JavaInvokable extractAll() {
        return args -> {
            if (args.length < 3) {
                throw new RuntimeException("extractAll() needs three arguments: text, regex, group");
            }
            String text = args[0].toString();
            String regex = args[1].toString();
            int group = ((Number) args[2]).intValue();
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            List<String> list = new ArrayList<>();
            while (matcher.find()) {
                list.add(matcher.group(group));
            }
            return list;
        };
    }

    static JavaInvokable filter() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("filter() needs two arguments: list and function");
            }
            List<?> list = (List<?>) args[0];
            JavaCallable fn = (JavaCallable) args[1];
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                Object keep = fn.call(null, new Object[]{item, i});
                if (Boolean.TRUE.equals(keep)) {
                    result.add(item);
                }
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable filterKeys() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("filterKeys() needs at least two arguments");
            }
            Map<String, Object> source = (Map<String, Object>) args[0];
            Map<String, Object> result = new LinkedHashMap<>();

            if (args[1] instanceof Map) {
                // filterKeys(source, keysFromMap) - filter by keys present in the map
                Map<String, Object> keysMap = (Map<String, Object>) args[1];
                for (String key : keysMap.keySet()) {
                    if (source.containsKey(key)) {
                        result.put(key, source.get(key));
                    }
                }
            } else if (args[1] instanceof List) {
                // filterKeys(source, [key1, key2, ...])
                List<String> keys = (List<String>) args[1];
                for (String key : keys) {
                    if (source.containsKey(key)) {
                        result.put(key, source.get(key));
                    }
                }
            } else {
                // filterKeys(source, key1, key2, ...)
                for (int i = 1; i < args.length; i++) {
                    String key = args[i].toString();
                    if (source.containsKey(key)) {
                        result.put(key, source.get(key));
                    }
                }
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable forEach() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("forEach() needs two arguments: collection and function");
            }
            Object collection = args[0];
            JavaCallable fn = (JavaCallable) args[1];
            if (collection instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    fn.call(null, new Object[]{list.get(i), i});
                }
            } else if (collection instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) collection;
                int i = 0;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    fn.call(null, new Object[]{entry.getKey(), entry.getValue(), i++});
                }
            }
            return null;
        };
    }

    static JavaInvokable fromJson() {
        return args -> {
            if (args.length < 1) {
                throw new RuntimeException("fromJson() needs one argument: a JSON string");
            }
            Object arg = args[0];
            if (arg instanceof String str) {
                return Json.of(str).value();
            } else {
                // Already parsed, just return it
                return arg;
            }
        };
    }

    static JavaInvokable jsonPath() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("jsonPath() needs two arguments: object and path");
            }
            Object json = args[0];
            String path = args[1].toString();
            return JsonPath.read(json, path);
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable keysOf() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("keysOf() needs one argument");
            }
            Map<String, Object> map = (Map<String, Object>) args[0];
            return new ArrayList<>(map.keySet());
        };
    }

    static JavaInvokable lowerCase() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("lowerCase() needs one argument");
            }
            Object obj = args[0];
            if (obj instanceof String) {
                return ((String) obj).toLowerCase();
            } else if (obj instanceof Map) {
                // Convert to JSON string, lowercase, parse back
                String json = Json.stringifyStrict(obj).toLowerCase();
                return Json.of(json).value();
            } else if (obj instanceof List) {
                String json = Json.stringifyStrict(obj).toLowerCase();
                return Json.of(json).value();
            } else if (obj instanceof Node) {
                String xml = Xml.toString((Node) obj, false).toLowerCase();
                return Xml.toXmlDoc(xml);
            }
            return obj;
        };
    }

    static JavaInvokable map() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("map() needs two arguments: list and function");
            }
            List<?> list = (List<?>) args[0];
            JavaCallable fn = (JavaCallable) args[1];
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                result.add(fn.call(null, new Object[]{list.get(i), i}));
            }
            return result;
        };
    }

    static JavaInvokable mapWithKey() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("mapWithKey() needs two arguments: list and key name");
            }
            Object listArg = args[0];
            if (listArg == null) {
                return new ArrayList<>();
            }
            List<?> list = (List<?>) listArg;
            String key = args[1].toString();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(key, item);
                result.add(map);
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable merge() {
        return args -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object arg : args) {
                if (arg instanceof Map) {
                    result.putAll((Map<String, Object>) arg);
                }
            }
            return result;
        };
    }

    static JavaInvokable pause() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return null;
            }
            long millis = ((Number) args[0]).longValue();
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
    }

    static JavaInvokable pretty() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("pretty() needs one argument");
            }
            Object obj = args[0];
            if (obj instanceof Map || obj instanceof List) {
                return StringUtils.formatJson(obj);
            } else if (obj instanceof Node) {
                return Xml.toString((Node) obj, true);
            } else {
                return obj != null ? obj.toString() : "null";
            }
        };
    }

    /**
     * Generate a range of integers.
     * Usage: karate.range(0, 5) => [0, 1, 2, 3, 4]
     *        karate.range(0, 10, 2) => [0, 2, 4, 6, 8]
     *        karate.range(5, 0, -1) => [5, 4, 3, 2, 1]
     */
    static JavaInvokable range() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("range() needs at least two arguments: start and end");
            }
            int start = ((Number) args[0]).intValue();
            int end = ((Number) args[1]).intValue();
            int step = args.length > 2 ? ((Number) args[2]).intValue() : 1;
            List<Integer> result = new ArrayList<>();
            if (step > 0) {
                for (int i = start; i < end; i += step) {
                    result.add(i);
                }
            } else if (step < 0) {
                for (int i = start; i > end; i += step) {
                    result.add(i);
                }
            }
            return result;
        };
    }

    static JavaInvokable prettyXml() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("prettyXml() needs one argument");
            }
            Object obj = args[0];
            if (obj instanceof Node) {
                return Xml.toString((Node) obj, true);
            } else if (obj instanceof String) {
                return Xml.toString(Xml.toXmlDoc((String) obj), true);
            } else {
                throw new RuntimeException("prettyXml() argument must be XML node or string");
            }
        };
    }

    static JavaInvokable repeat() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("repeat() needs two arguments: count and function");
            }
            int count = ((Number) args[0]).intValue();
            JavaCallable fn = (JavaCallable) args[1];
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                result.add(fn.call(null, new Object[]{i}));
            }
            return result;
        };
    }

    static JavaInvokable sizeOf() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("sizeOf() needs one argument");
            }
            Object obj = args[0];
            if (obj instanceof List) {
                return ((List<?>) obj).size();
            } else if (obj instanceof Map) {
                return ((Map<?, ?>) obj).size();
            } else if (obj instanceof String) {
                return ((String) obj).length();
            }
            return 0;
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable sort() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("sort() needs two arguments: list and key function");
            }
            List<?> list = (List<?>) args[0];
            JavaCallable fn = (JavaCallable) args[1];
            List<Object> result = new ArrayList<>(list);
            result.sort((a, b) -> {
                Object keyA = fn.call(null, new Object[]{a});
                Object keyB = fn.call(null, new Object[]{b});
                if (keyA instanceof Comparable && keyB instanceof Comparable) {
                    return ((Comparable<Object>) keyA).compareTo(keyB);
                }
                return 0;
            });
            return result;
        };
    }

    static JavaInvokable toBean() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("toBean() needs two arguments: object and class name");
            }
            Object obj = args[0];
            String className = args[1].toString();
            // Convert to JSON string and deserialize to the target class
            String jsonString = Json.of(obj).toString();
            return Json.fromJson(jsonString, className);
        };
    }

    /**
     * Convert a list of maps to CSV string.
     * Usage: karate.toCsv([{a:1,b:2},{a:3,b:4}]) => "a,b\n1,2\n3,4\n"
     */
    @SuppressWarnings("unchecked")
    static JavaInvokable toCsv() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return "";
            }
            if (!(args[0] instanceof List)) {
                throw new RuntimeException("toCsv() argument must be a list of maps, got: " + args[0].getClass().getName());
            }
            List<Map<String, Object>> list = (List<Map<String, Object>>) args[0];
            if (list.isEmpty()) {
                return "";
            }
            return DataUtils.toCsv(list);
        };
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable toBytes() {
        return args -> {
            if (args.length < 1) {
                throw new RuntimeException("toBytes() needs one argument: a list of numbers");
            }
            Object arg = args[0];
            if (arg instanceof byte[]) {
                return arg; // already bytes
            }
            if (!(arg instanceof List)) {
                throw new RuntimeException("toBytes() argument must be a list of numbers, got: " + arg.getClass().getName());
            }
            List<Object> list = (List<Object>) arg;
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number num) {
                    bytes[i] = num.byteValue();
                } else {
                    throw new RuntimeException("toBytes() list must contain only numbers, got: " + item.getClass().getName() + " at index " + i);
                }
            }
            return bytes;
        };
    }

    static JavaInvokable toJson() {
        return args -> {
            if (args.length < 1) {
                throw new RuntimeException("toJson() needs at least one argument");
            }
            Object obj = args[0];
            boolean removeNulls = args.length > 1 && Boolean.TRUE.equals(args[1]);
            Object result = Json.of(obj).value();
            if (removeNulls) {
                removeNullValues(result);
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private static void removeNullValues(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            map.entrySet().removeIf(e -> e.getValue() == null);
            map.values().forEach(KarateJsUtils::removeNullValues);
        } else if (obj instanceof List) {
            ((List<?>) obj).forEach(KarateJsUtils::removeNullValues);
        }
    }

    /**
     * Convert value to its string representation.
     * - JSON (Map/List) -> compact JSON string
     * - XML (Node) -> XML string
     * - byte[] -> string from bytes
     * - Others -> toString()
     */
    static JavaCallable toStringValue() {
        return (thisRef, args) -> {
            if (args.length == 0 || args[0] == null) {
                return null;
            }
            Object value = args[0];
            if (value instanceof Map || value instanceof List) {
                return Json.of(value).toString();
            } else if (value instanceof Node) {
                return Xml.toString((Node) value, false);
            } else if (value instanceof byte[] bytes) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            return value.toString();
        };
    }

    /**
     * Returns the Karate type of a value.
     */
    static JavaInvokable typeOf() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return "null";
            }
            Object value = args[0];
            if (value instanceof Boolean) {
                return "boolean";
            } else if (value instanceof Number) {
                return "number";
            } else if (value instanceof String) {
                return "string";
            } else if (value instanceof byte[]) {
                return "bytes";
            } else if (value instanceof List) {
                return "list";
            } else if (value instanceof JavaCallable) {
                return "function";
            } else if (value instanceof Map) {
                return "map";
            } else if (value instanceof Node) {
                return "xml";
            }
            return "object";
        };
    }

    static JavaInvokable urlEncode() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return "";
            }
            return URLEncoder.encode(args[0].toString(), StandardCharsets.UTF_8);
        };
    }

    static JavaInvokable urlDecode() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return "";
            }
            return URLDecoder.decode(args[0].toString(), StandardCharsets.UTF_8);
        };
    }

    /**
     * Generate a random UUID string.
     * Usage: karate.uuid() => "550e8400-e29b-41d4-a716-446655440000"
     */
    static JavaInvokable uuid() {
        return args -> UUID.randomUUID().toString();
    }

    @SuppressWarnings("unchecked")
    static JavaInvokable valuesOf() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("valuesOf() needs one argument");
            }
            Object obj = args[0];
            if (obj instanceof Map) {
                return new ArrayList<>(((Map<String, Object>) obj).values());
            } else if (obj instanceof List) {
                return new ArrayList<>((List<?>) obj);
            }
            return new ArrayList<>();
        };
    }

    // ========== OS Utilities ==========

    /**
     * Returns OS information for karate.os.
     * Uses OsUtils for platform detection.
     */
    static Map<String, Object> getOsInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", System.getProperty("os.name", "unknown"));
        result.put("type", OsUtils.getOsType());
        return result;
    }

    // ========== XML Utilities ==========

    /**
     * Evaluate an XPath expression on an XML node.
     * Returns the appropriate type: String, Number, Node, or List of nodes.
     */
    static Object evalXmlPath(Node doc, String path) {
        org.w3c.dom.NodeList nodeList;
        try {
            nodeList = Xml.getNodeListByPath(doc, path);
        } catch (Exception e) {
            // XPath functions like count() don't return nodes
            String strValue = Xml.getTextValueByPath(doc, path);
            if (path.startsWith("count")) {
                try {
                    return Integer.parseInt(strValue);
                } catch (NumberFormatException nfe) {
                    return strValue;
                }
            }
            return strValue;
        }
        int count = nodeList.getLength();
        if (count == 0) {
            return null; // Not present
        }
        if (count == 1) {
            return nodeToValue(nodeList.item(0));
        }
        // Multiple nodes - return a list
        List<Object> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(nodeToValue(nodeList.item(i)));
        }
        return list;
    }

    /**
     * Convert an XML node to an appropriate value.
     * Attributes return their value, leaf nodes return text content,
     * complex nodes return as a new XML document.
     */
    static Object nodeToValue(Node node) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            return node.getNodeValue();
        }
        if (Xml.getChildElementCount(node) == 0) {
            // Leaf node - return text content
            return node.getTextContent();
        }
        // Return as a new XML document
        return Xml.toNewDocument(node);
    }

    // ========== Type Conversion Utilities ==========

    /**
     * Parse a string as JSON, XML, or return as-is.
     * - If the string looks like JSON (starts with { or [), parse as JSON
     * - If the string looks like XML (starts with &lt;), parse as XML
     * - Otherwise, return the string unchanged
     */
    static JavaInvokable fromString() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                return null;
            }
            String text = args[0].toString();
            if (text.isEmpty()) {
                return text;
            }
            if (StringUtils.looksLikeJson(text)) {
                try {
                    return Json.of(text).value();
                } catch (Exception e) {
                    logger.warn("fromString JSON parse failed: {}", e.getMessage());
                    return text;
                }
            } else if (StringUtils.isXml(text)) {
                try {
                    return Xml.toXmlDoc(text);
                } catch (Exception e) {
                    logger.warn("fromString XML parse failed: {}", e.getMessage());
                    return text;
                }
            }
            return text;
        };
    }

    /**
     * Auto-detect MIME type from data object.
     */
    static String detectMimeType(Object obj) {
        if (obj instanceof Map || obj instanceof List) {
            return "application/json";
        } else if (obj instanceof Node) {
            return "application/xml";
        } else if (obj instanceof byte[]) {
            return "application/octet-stream";
        } else {
            return "text/plain";
        }
    }

    /**
     * Convert various data types to bytes.
     */
    static byte[] convertToBytes(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        }
        if (obj instanceof String str) {
            return str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (obj instanceof List<?> list) {
            // Check if it's a list of numbers (byte array representation)
            if (!list.isEmpty() && list.getFirst() instanceof Number) {
                byte[] bytes = new byte[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    bytes[i] = ((Number) list.get(i)).byteValue();
                }
                return bytes;
            }
            // Otherwise serialize as JSON
            return StringUtils.formatJson(list).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (obj instanceof Map) {
            return StringUtils.formatJson(obj).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (obj instanceof Node) {
            return Xml.toString((Node) obj, true).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        // Fallback: convert to string
        return obj.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ========== JSON Path Utilities ==========

    /**
     * Navigate to a nested path in a Map structure.
     * Path is dot-separated like "foo.bar.baz".
     */
    @SuppressWarnings("unchecked")
    static Object navigateToPath(Object target, String path) {
        String[] parts = path.split("\\.");
        Object current = target;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Set a value at a nested path in a Map structure.
     * Creates intermediate maps as needed.
     */
    @SuppressWarnings("unchecked")
    static void setAtPath(Object target, String path, Object value) {
        String[] parts = path.split("\\.");
        Object current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            if (current instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) current;
                Object next = map.get(parts[i]);
                if (next == null) {
                    next = new java.util.LinkedHashMap<>();
                    map.put(parts[i], next);
                }
                current = next;
            }
        }
        if (current instanceof Map) {
            ((Map<String, Object>) current).put(parts[parts.length - 1], value);
        }
    }

    // ========== String Parsing Utilities ==========

    /**
     * Find the matching closing parenthesis for an open paren.
     * Used for parsing embedded expressions like #(expr).
     */
    static int findMatchingParen(String str, int openPos) {
        int depth = 1;
        for (int i = openPos + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Convert a value to string, handling XML nodes properly.
     */
    static String valueToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Node) {
            return Xml.toString((Node) value, false);
        }
        return value.toString();
    }

    // ========== XML Utilities (Invokable) ==========

    /**
     * karate.xmlPath(xml, path) - Evaluate XPath on XML.
     * First argument can be XML Node or String.
     */
    static JavaInvokable xmlPath() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("xmlPath() needs two arguments: xml and path");
            }
            Object xmlObj = args[0];
            String path = args[1].toString();
            Node doc;
            if (xmlObj instanceof Node) {
                doc = (Node) xmlObj;
            } else if (xmlObj instanceof String) {
                doc = Xml.toXmlDoc((String) xmlObj);
            } else {
                throw new RuntimeException("xmlPath() first argument must be XML node or string, but was: " + (xmlObj == null ? "null" : xmlObj.getClass()));
            }
            try {
                return evalXmlPath(doc, path);
            } catch (Exception e) {
                throw new RuntimeException("xmlPath failed for path: " + path + " - " + e.getMessage(), e);
            }
        };
    }

    // ========== Control Flow Utilities ==========

    /**
     * karate.fail(message) - Explicitly fail the scenario with a message.
     */
    static JavaInvokable fail() {
        return args -> {
            String message = args.length > 0 && args[0] != null ? args[0].toString() : "karate.fail() called";
            throw new RuntimeException(message);
        };
    }

    // ========== Type Conversion Utilities (Invokable) ==========

    /**
     * karate.toJava() - Deprecated no-op for V1 compatibility.
     * In V2, JavaScript arrays work directly with Java, so this is unnecessary.
     */
    static JavaInvokable toJava() {
        return args -> {
            logger.warn("karate.toJava() is deprecated and a no-op in V2 - JavaScript arrays work directly with Java");
            if (args.length < 1) {
                return null;
            }
            return args[0]; // no-op, just return the input
        };
    }

    // ========== Debugging Utilities ==========

    /**
     * karate.stop(port) - Debugging breakpoint that pauses test execution.
     * Opens a server socket and waits for a connection (e.g., curl or browser).
     * Prints instructions to console. NEVER forget to remove after debugging!
     */
    static JavaInvokable stop() {
        return args -> {
            int port = 0; // 0 means pick any available port
            if (args.length > 0 && args[0] != null) {
                if (args[0] instanceof Number) {
                    port = ((Number) args[0]).intValue();
                } else {
                    port = Integer.parseInt(args[0].toString());
                }
            }
            try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port, 1,
                    java.net.InetAddress.getByName("127.0.0.1"))) {
                int actualPort = serverSocket.getLocalPort();
                System.out.println("*** waiting for socket, type the command below:\ncurl http://localhost:"
                        + actualPort + "\nin a new terminal (or open the URL in a web-browser) to proceed ...");
                // Block until a connection is received
                try (Socket clientSocket = serverSocket.accept()) {
                    // Send a simple HTTP response
                    java.io.OutputStream out = clientSocket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".getBytes());
                    out.flush();
                }
                logger.info("*** exited socket wait successfully");
                return true;
            } catch (Exception e) {
                throw new RuntimeException("stop() failed: " + e.getMessage(), e);
            }
        };
    }

    // ========== Wait Utilities ==========

    /**
     * karate.waitForHttp(url) - Wait for an HTTP endpoint to become available.
     * Polls until a successful response (2xx or 3xx) is received.
     */
    static JavaInvokable waitForHttp() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("waitForHttp() needs a URL argument");
            }
            String url = args[0] + "";
            int timeoutMs = 30000;
            int pollMs = 250;
            if (args.length > 1 && args[1] instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> options = (Map<String, Object>) args[1];
                if (options.containsKey("timeout")) {
                    timeoutMs = ((Number) options.get("timeout")).intValue();
                }
                if (options.containsKey("interval")) {
                    pollMs = ((Number) options.get("interval")).intValue();
                }
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(pollMs))
                    .build();
            while (System.currentTimeMillis() < deadline) {
                try {
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofMillis(pollMs))
                            .GET()
                            .build();
                    var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
                    int status = response.statusCode();
                    if (status >= 200 && status < 400) {
                        return true;
                    }
                } catch (Exception e) {
                    // Connection failed, continue polling
                }
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            throw new RuntimeException("HTTP endpoint not available: " + url);
        };
    }

    /**
     * karate.waitForPort(host, port) - Wait for a TCP port to become available.
     * Polls until a TCP connection can be established.
     */
    static JavaInvokable waitForPort() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("waitForPort() needs host and port arguments");
            }
            String host = args[0] + "";
            int port;
            if (args[1] instanceof Number) {
                port = ((Number) args[1]).intValue();
            } else {
                port = Integer.parseInt(args[1].toString());
            }
            int timeoutMs = 30000;
            int pollMs = 250;
            if (args.length > 2 && args[2] instanceof Number) {
                timeoutMs = ((Number) args[2]).intValue();
            }
            if (args.length > 3 && args[3] instanceof Number) {
                pollMs = ((Number) args[3]).intValue();
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try (Socket socket = new Socket(host, port)) {
                    // Port is open, success
                    return true;
                } catch (Exception e) {
                    // Port not available, continue polling
                }
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            throw new RuntimeException("Port " + host + ":" + port + " not available within timeout");
        };
    }

}
