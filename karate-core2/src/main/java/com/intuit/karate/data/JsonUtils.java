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
package com.intuit.karate.data;

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptValue;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import de.siegmar.fastcsv.writer.CsvWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 *
 * @author pthomas3
 */
public class JsonUtils {

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
        return JSONValue.toJSONString(o);
    }

    public static byte[] toJsonBytes(Object o) {
        return toJson(o).getBytes(FileUtils.UTF8);
    }

    public static Object fromJsonString(String json) {
        return JSONValue.parse(json);
    }

    public static Map<String, Object> fromYaml(String raw) {
        Yaml yaml = new Yaml(new SafeConstructor());
        return yaml.load(raw);
    }

    public static List<Map> fromCsv(String raw) {
        CsvReader reader = new CsvReader();
        reader.setContainsHeader(true);
        try {
            CsvContainer csv = reader.read(new StringReader(raw));
            List<Map> rows = new ArrayList(csv.getRowCount());
            for (CsvRow row : csv.getRows()) {
                rows.add(row.getFieldMap());
            }
            return rows;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toCsv(List<Map<String, Object>> list) {
        List<String[]> csv = new ArrayList(list.size() + 1);
        // header row
        boolean first = true;
        for (Map<String, Object> map : list) {
            int colCount = map.size();
            if (first) {
                Set<String> keys = map.keySet();
                csv.add(keys.toArray(new String[colCount]));
                first = false;
            }
            String[] row = new String[colCount];
            List cols = new ArrayList(map.values());
            for (int i = 0; i < colCount; i++) {
                row[i] = new ScriptValue(cols.get(i)).getAsString();
            }
            csv.add(row);
        }
        CsvWriter csvWriter = new CsvWriter();
        StringWriter sw = new StringWriter();
        try {
            csvWriter.write(sw, csv);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

}
