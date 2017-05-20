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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author pthomas3
 */
public class JsonUtils {

    private JsonUtils() {
        // only static methods
    }

    public static DocumentContext toJsonDoc(String raw) {
        return JsonPath.parse(raw);
    }

    public static String toJsonString(String raw) {
        DocumentContext dc = toJsonDoc(raw);
        return dc.jsonString();
    }
    
    public static String toPrettyJsonString(DocumentContext doc) {
        Object o = doc.read("$");
        StringBuilder sb = new StringBuilder();
        recursePretty(o, sb, 0);
        sb.append('\n');
        return sb.toString();
    }
    
    private static void pad(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append(' ').append(' ');
        }
    }
    
    private static void recursePretty(Object o, StringBuilder sb, int depth) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Map) {            
            sb.append('{').append('\n');
            Map<String, Object> map = (Map<String, Object>) o;
            int size = map.size();
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            for (int i = 0; i < size; i++) {
                Map.Entry<String, Object> entry = iterator.next();
                String key = entry.getKey();
                pad(sb, depth + 1);
                sb.append('"').append(JSONObject.escape(key)).append('"');
                sb.append(':').append(' ');
                recursePretty(entry.getValue(), sb, depth + 1);
                if (i != size - 1) {
                    sb.append(',');
                }                
                sb.append('\n');                
            }
            pad(sb, depth);
            sb.append('}');
        } else if (o instanceof List) {
            List list = (List) o;
            int size = list.size();
            sb.append('[').append('\n');
            for (int i = 0; i < size; i++) {
                Object child = list.get(i);
                pad(sb, depth + 1);
                recursePretty(child, sb, depth + 1);
                if (i != size - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, depth);
            sb.append(']');
        } else if (o instanceof String) {
            String value = (String) o;            
            sb.append('"').append(JSONObject.escape(value)).append('"');
        } else {
            sb.append(o);
        }       
    }

    public static Pair<String, String> getParentAndLeafPath(String path) {
        int pos = path.lastIndexOf('.');
        String left = path.substring(0, pos == -1 ? 0 : pos);
        String right = path.substring(pos + 1);
        return Pair.of(left, right);
    }

    public static void setValueByPath(DocumentContext doc, String path, Object value) {
        if ("$".equals(path)) {
            throw new RuntimeException("cannot replace root path $");
        }
        Pair<String, String> pathLeaf = getParentAndLeafPath(path);
        String left = pathLeaf.getLeft();
        String right = pathLeaf.getRight();
        if (right.endsWith("]")) { // json array
            int indexPos = right.lastIndexOf('[');
            int index = Integer.valueOf(right.substring(indexPos + 1, right.length() - 1));
            right = right.substring(0, indexPos);
            List list;
            String listPath = left + "." + right;
            try {
                list = doc.read(listPath);
                if (index < list.size()) {
                    list.set(index, value);
                } else {
                    list.add(value);
                }
            } catch (Exception e) {
                list = new ArrayList();
                list.add(value);
                doc.put(left, right, list);
            }
        } else {
            doc.put(left, right, value);
        }
    }
    
    public static DocumentContext fromYaml(String raw) {
        Yaml yaml = new Yaml();
        return JsonPath.parse(yaml.load(raw));
    }

}
