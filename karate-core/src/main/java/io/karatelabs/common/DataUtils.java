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
package io.karatelabs.common;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataUtils {

    private DataUtils() {
        // only static methods
    }

    public static List<Map<String, Object>> fromCsv(String raw) {
        CsvReader<CsvRecord> reader = CsvReader.builder().ofCsvRecord(raw);
        List<String> header = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            boolean first = true;
            for (CsvRecord row : reader) {
                if (first) {
                    for (String field : row.getFields()) {
                        header.add(field.replace("\ufeff", "")); // remove byte order mark
                    }
                    first = false;
                } else {
                    int count = header.size();
                    Map<String, Object> map = new LinkedHashMap<>(count);
                    for (int i = 0; i < count; i++) {
                        map.put(header.get(i), row.getField(i));
                    }
                    rows.add(map);
                }
            }
            return rows;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toCsv(List<Map<String, Object>> list) {
        StringWriter sw = new StringWriter();
        try (CsvWriter writer = CsvWriter.builder().build(sw)) {
            // header row
            if (!list.isEmpty()) {
                writer.writeRecord(list.get(0).keySet());
            }
            for (Map<String, Object> map : list) {
                List<String> row = new ArrayList<>(map.size());
                for (Object value : map.values()) {
                    row.add(value == null ? null : value.toString());
                }
                writer.writeRecord(row);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

    public static Object fromYaml(String raw) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(8 * 1024 * 1024); // 8MB
        Yaml yaml = new Yaml(new SafeConstructor(options));
        return yaml.load(raw);
    }

}
