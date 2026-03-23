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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataUtilsTest {

    @Test
    void testFromCsv() {
        String csv = "name,age\nJohn,30\nJane,25";
        List<Map<String, Object>> result = DataUtils.fromCsv(csv);
        assertEquals(2, result.size());
        assertEquals("John", result.get(0).get("name"));
        assertEquals("30", result.get(0).get("age"));
        assertEquals("Jane", result.get(1).get("name"));
        assertEquals("25", result.get(1).get("age"));
    }

    @Test
    void testFromCsvEmpty() {
        String csv = "name,age";
        List<Map<String, Object>> result = DataUtils.fromCsv(csv);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToCsv() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "John");
        row1.put("age", 30);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "Jane");
        row2.put("age", 25);
        List<Map<String, Object>> list = List.of(row1, row2);

        String csv = DataUtils.toCsv(list);
        assertTrue(csv.contains("name,age"));
        assertTrue(csv.contains("John,30"));
        assertTrue(csv.contains("Jane,25"));
    }

    @Test
    void testToCsvEmpty() {
        List<Map<String, Object>> list = List.of();
        String csv = DataUtils.toCsv(list);
        assertEquals("", csv);
    }

    @Test
    void testToCsvWithNullValue() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "John");
        row.put("age", null);
        List<Map<String, Object>> list = List.of(row);

        String csv = DataUtils.toCsv(list);
        assertTrue(csv.contains("name,age"));
        assertTrue(csv.contains("John,"));
    }

    @Test
    void testFromYaml() {
        String yaml = "name: John\nage: 30";
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) DataUtils.fromYaml(yaml);
        assertEquals("John", result.get("name"));
        assertEquals(30, result.get("age"));
    }

    @Test
    void testFromYamlList() {
        String yaml = "- a\n- b\n- c";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) DataUtils.fromYaml(yaml);
        assertEquals(List.of("a", "b", "c"), result);
    }

}
