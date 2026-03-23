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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpResponseTest {

    @Test
    void testSuccessResponse() {
        Map<String, Object> raw = Map.of(
                "id", 1,
                "result", Map.of("frameId", "ABC123")
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals(1, response.getId());
        assertFalse(response.isError());
        assertNull(response.getError());
        assertEquals("ABC123", response.getResult("frameId"));
    }

    @Test
    void testErrorResponse() {
        Map<String, Object> raw = Map.of(
                "id", 2,
                "error", Map.of(
                        "code", -32000,
                        "message", "Target not found"
                )
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals(2, response.getId());
        assertTrue(response.isError());
        assertEquals(-32000, response.getErrorCode());
        assertEquals("Target not found", response.getErrorMessage());
    }

    @Test
    void testNestedResultPath() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", 3);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", Map.of("type", "string", "value", "Hello"));
        raw.put("result", result);

        CdpResponse response = new CdpResponse(raw);

        assertEquals("string", response.getResult("result.type"));
        assertEquals("Hello", response.getResult("result.value"));
    }

    @Test
    void testGetResultAsString() {
        Map<String, Object> raw = Map.of(
                "id", 4,
                "result", Map.of("count", 42)
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals("42", response.getResultAsString("count"));
    }

    @Test
    void testGetResultAsInt() {
        Map<String, Object> raw = Map.of(
                "id", 5,
                "result", Map.of("count", 42)
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals(42, response.getResultAsInt("count"));
    }

    @Test
    void testGetResultAsBoolean() {
        Map<String, Object> raw = Map.of(
                "id", 6,
                "result", Map.of("enabled", true)
        );
        CdpResponse response = new CdpResponse(raw);

        assertTrue(response.getResultAsBoolean("enabled"));
    }

    @Test
    void testMissingPathReturnsNull() {
        Map<String, Object> raw = Map.of(
                "id", 7,
                "result", Map.of("frameId", "ABC")
        );
        CdpResponse response = new CdpResponse(raw);

        assertNull(response.getResult("nonexistent"));
        assertNull(response.getResult("nested.path.missing"));
    }

    @Test
    void testEmptyResult() {
        Map<String, Object> raw = Map.of("id", 8, "result", Map.of());
        CdpResponse response = new CdpResponse(raw);

        assertFalse(response.isError());
        assertNull(response.getResult("anything"));
    }

    @Test
    void testToStringSuccess() {
        Map<String, Object> raw = Map.of("id", 9, "result", Map.of());
        CdpResponse response = new CdpResponse(raw);

        assertEquals("CdpResponse[9]", response.toString());
    }

    @Test
    void testToStringError() {
        Map<String, Object> raw = Map.of(
                "id", 10,
                "error", Map.of("message", "Failed")
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals("CdpResponse[10 ERROR: Failed]", response.toString());
    }

    @Test
    void testJsonPathWithArray() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", 11);
        raw.put("result", Map.of("items", List.of("a", "b", "c")));

        CdpResponse response = new CdpResponse(raw);

        List<String> items = response.getResult("items");
        assertEquals(3, items.size());
        assertEquals("a", items.get(0));
    }

    @Test
    void testGetFromRaw() {
        Map<String, Object> raw = Map.of(
                "id", 12,
                "result", Map.of("value", "test")
        );
        CdpResponse response = new CdpResponse(raw);

        assertEquals(12, response.<Number>get("id").intValue());
    }

}
