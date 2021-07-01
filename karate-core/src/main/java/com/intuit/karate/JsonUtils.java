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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import de.siegmar.fastcsv.writer.CsvWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static net.minidev.json.JSONValue.defaultReader;

/**
 *
 * @author pthomas3
 */
public class JsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private JsonUtils() {
        // only static methods
    }

    static {
        // ensure that even if jackson (databind?) is on the classpath, don't switch provider
        Configuration.setDefaults(new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new JsonSmartJsonProvider();
            private final MappingProvider mappingProvider = new JsonSmartMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }

    public static boolean isJson(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.charAt(0) == ' ') {
            s = s.trim();
            if (s.isEmpty()) {
                return false;
            }
        }
        return s.charAt(0) == '{' || s.charAt(0) == '[';
    }

    public static String toStrictJson(String raw) {
        JSONParser jp = new JSONParser(JSONParser.MODE_PERMISSIVE);
        try {
            Object o = jp.parse(raw);
            return JSONValue.toJSONString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(Object o) {
        return toJson(o, false);
    }

    public static String toJson(Object o, boolean pretty) {
        if (!pretty) { // TODO use JSONStyleIdent in json-smart 2.4
            try {
                return JSONValue.toJSONString(o);
            } catch (Throwable t) {
                logger.warn("object to json serialization failure, trying alternate approach: {}", t.getMessage());
            }
        }
        return JsonUtils.toJsonSafe(o, pretty);
    }

    public static byte[] toJsonBytes(Object o) {
        return toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    public static Object fromJson(String json) {
        return JSONValue.parseKeepingOrder(json);
    }

    public static Object fromJsonStrict(String json) {
        JSONParser parser = new JSONParser(JSONParser.MODE_RFC4627);
        try {
            return parser.parse(json.trim(), defaultReader.DEFAULT_ORDERED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object fromJson(String s, String className) {
        try {
            Class clazz = Class.forName(className);
            return JSONValue.parse(s, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String s, Class<T> clazz) {
        return (T) fromJson(s, clazz.getName());
    }

    public static Object fromYaml(String raw) {
        Yaml yaml = new Yaml(new SafeConstructor());
        return yaml.load(raw);
    }

    public static List<Map> fromCsv(String raw) {
        CsvReader reader = CsvReader.builder().build(raw);
        List<String> header = Collections.emptyList();
        List<Map> rows = new ArrayList();
        try {
            boolean first = true;
            for (CsvRow row : reader) {
                if (first) {
                    header = row.getFields();
                    first = false;
                } else {
                    int count = header.size();
                    Map<String, Object> map = new LinkedHashMap(count);
                    for (int i = 0; i < count; i++) {
                        map.put(header.get(i), row.getField(i));
                    }
                    rows.add(map);
                }
            }
            return rows;
        } catch (Exception e) {
            logger.warn("failed to parse csv: {}", e.getMessage());
            return rows;
        }
    }

    public static String toCsv(List<Map<String, Object>> list) {
        StringWriter sw = new StringWriter();
        CsvWriter writer = CsvWriter.builder().build(sw);
        // header row
        if (!list.isEmpty()) {
            writer.writeRow(list.get(0).keySet());
        }
        for (Map<String, Object> map : list) {
            List<String> row = new ArrayList(map.size());
            for (Object value : map.values()) {
                row.add(value == null ? null : value.toString());
            }
            writer.writeRow(row);
        }
        return sw.toString();
    }

    public static Object deepCopy(Object o) {
        // anti recursion / back-references
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
        return recurseDeepCopy(o, seen);
    }

    private static Object recurseDeepCopy(Object o, Set<Object> seen) {
        if (o instanceof List) {
            List list = (List) o;
            if (seen.add(o)) {
                int count = list.size();
                List listCopy = new ArrayList(count);
                for (int i = 0; i < count; i++) {
                    listCopy.add(recurseDeepCopy(list.get(i), seen));
                }
                return listCopy;
            } else {
                return o;
            }
        } else if (o instanceof Map) {
            if (seen.add(o)) {
                Map<String, Object> map = (Map<String, Object>) o;
                Map<String, Object> mapCopy = new LinkedHashMap(map.size());
                map.forEach((k, v) -> {
                    mapCopy.put(k, recurseDeepCopy(v, seen));
                });
                return mapCopy;
            } else {
                return o;
            }
        } else {
            return o;
        }
    }

    public static String toJsonSafe(Object o, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        // anti recursion / back-references
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap());
        recurseJsonString(o, pretty, sb, 0, seen);
        if (pretty) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void pad(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append(' ').append(' ');
        }
    }

    private static void ref(StringBuilder sb, Object o) {
        sb.append("\"#ref:").append(o.getClass().getName()).append('"');
    }

    public static String escapeValue(String raw) {
        return JSONValue.escape(raw, JSONStyle.LT_COMPRESS);
    }

    private static void recurseJsonString(Object o, boolean pretty, StringBuilder sb, int depth, Set<Object> seen) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof List) {
            List list = (List) o;
            Iterator iterator = list.iterator();
            if (seen.add(o)) {
                sb.append('[');
                if (pretty) {
                    sb.append('\n');
                }
                while (iterator.hasNext()) {
                    Object child = iterator.next();
                    if (pretty) {
                        pad(sb, depth + 1);
                    }
                    recurseJsonString(child, pretty, sb, depth + 1, seen);
                    if (iterator.hasNext()) {
                        sb.append(',');
                    }
                    if (pretty) {
                        sb.append('\n');
                    }
                }
                if (pretty) {
                    pad(sb, depth);
                }
                sb.append(']');
            } else {
                ref(sb, o);
            }
        } else if (o instanceof Map) {
            if (seen.add(o)) {
                sb.append('{');
                if (pretty) {
                    sb.append('\n');
                }
                Map<String, Object> map = (Map<String, Object>) o;
                Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> entry = iterator.next();
                    Object key = entry.getKey(); // found a rare case where this was a boolean
                    if (pretty) {
                        pad(sb, depth + 1);
                    }
                    sb.append('"').append(escapeValue(key == null ? null : key.toString())).append('"').append(':');
                    if (pretty) {
                        sb.append(' ');
                    }
                    recurseJsonString(entry.getValue(), pretty, sb, depth + 1, seen);
                    if (iterator.hasNext()) {
                        sb.append(',');
                    }
                    if (pretty) {
                        sb.append('\n');
                    }
                }
                if (pretty) {
                    pad(sb, depth);
                }
                sb.append('}');
            } else {
                ref(sb, o);
            }
        } else if (o instanceof String) {
            String value = (String) o;
            sb.append('"').append(escapeValue(value)).append('"');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else { // TODO custom writers ?
            String value = o.toString();
            sb.append('"').append(escapeValue(value)).append('"');
        }
    }

    public static void removeKeysWithNullValues(Object o) {
        if (o instanceof List) {
            List list = (List) o;
            for (Object v : list) {
                removeKeysWithNullValues(v);
            }
        } else if (o instanceof Map) {
            Map<String, Object> map = (Map) o;
            List<String> toRemove = new ArrayList();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object v = entry.getValue();
                if (v == null) {
                    toRemove.add(entry.getKey());
                } else {
                    removeKeysWithNullValues(v);
                }
            }
            toRemove.forEach(key -> map.remove(key));
        }
    }

}
