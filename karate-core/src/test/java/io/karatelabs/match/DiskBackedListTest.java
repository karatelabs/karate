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
package io.karatelabs.match;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiskBackedListTest {

    @Test
    void testCreateAndGet() throws IOException {
        List<Object> source = List.of("a", "b", "c", 1, 2, 3);
        DiskBackedList dbl = DiskBackedList.create(source);

        assertEquals(6, dbl.size());
        assertEquals("a", dbl.get(0));
        assertEquals("b", dbl.get(1));
        assertEquals("c", dbl.get(2));
        assertEquals(1, dbl.get(3));
        assertEquals(2, dbl.get(4));
        assertEquals(3, dbl.get(5));

        dbl.close();
    }

    @Test
    void testIterator() throws IOException {
        List<Object> source = List.of("x", "y", "z");
        DiskBackedList dbl = DiskBackedList.create(source);

        Iterator<Object> iter = dbl.iterator();
        assertTrue(iter.hasNext());
        assertEquals("x", iter.next());
        assertTrue(iter.hasNext());
        assertEquals("y", iter.next());
        assertTrue(iter.hasNext());
        assertEquals("z", iter.next());
        assertFalse(iter.hasNext());

        dbl.close();
    }

    @Test
    void testNestedObjects() throws IOException {
        Map<String, Object> map1 = Map.of("name", "alice", "age", 30);
        Map<String, Object> map2 = Map.of("name", "bob", "age", 25);
        List<Object> nested = List.of(1, 2, 3);
        List<Object> source = List.of(map1, map2, nested);

        DiskBackedList dbl = DiskBackedList.create(source);

        assertEquals(3, dbl.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> retrieved1 = (Map<String, Object>) dbl.get(0);
        assertEquals("alice", retrieved1.get("name"));
        assertEquals(30, ((Number) retrieved1.get("age")).intValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> retrieved2 = (Map<String, Object>) dbl.get(1);
        assertEquals("bob", retrieved2.get("name"));

        @SuppressWarnings("unchecked")
        List<Object> retrievedList = (List<Object>) dbl.get(2);
        assertEquals(3, retrievedList.size());

        dbl.close();
    }

    @Test
    void testNullValues() throws IOException {
        List<Object> source = Arrays.asList("a", null, "b", null);
        DiskBackedList dbl = DiskBackedList.create(source);

        assertEquals(4, dbl.size());
        assertEquals("a", dbl.get(0));
        assertNull(dbl.get(1));
        assertEquals("b", dbl.get(2));
        assertNull(dbl.get(3));

        dbl.close();
    }

    @Test
    void testEstimateSize() {
        // Small values
        assertEquals(8, DiskBackedList.estimateSize(null));
        assertEquals(24, DiskBackedList.estimateSize(42));
        assertEquals(16, DiskBackedList.estimateSize(true));

        // String: 40 + length * 2
        long stringSize = DiskBackedList.estimateSize("hello");
        assertEquals(40 + 5 * 2, stringSize);

        // Empty list
        long emptyListSize = DiskBackedList.estimateSize(List.of());
        assertEquals(40, emptyListSize);

        // List with items
        long listSize = DiskBackedList.estimateSize(List.of(1, 2, 3));
        assertTrue(listSize > 40);
    }

    @Test
    void testEstimateCollectionSize() {
        // Empty list
        assertEquals(40, DiskBackedList.estimateCollectionSize(List.of()));

        // Small list - all items checked
        List<String> small = List.of("a", "b", "c");
        long smallSize = DiskBackedList.estimateCollectionSize(small);
        assertTrue(smallSize > 40);

        // Large list - sampling kicks in (>5 items)
        List<Integer> large = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            large.add(i);
        }
        long largeSize = DiskBackedList.estimateCollectionSize(large);
        assertTrue(largeSize > 1000 * 20); // rough minimum
    }

    @Test
    void testMatchWithLowThreshold() {
        // Create a list - with threshold of 1, it will use disk storage
        List<Object> actual = List.of("a", "b", "c", "d", "e");
        List<Object> expected = List.of("a", "b", "c", "d", "e");

        // Use low threshold (1 byte) to force disk-backed storage
        Result result = Match.execute(null, Match.Type.EQUALS, actual, expected, 1L);
        assertTrue(result.pass, result.message);

        // Test CONTAINS with low threshold
        result = Match.execute(null, Match.Type.CONTAINS, actual, List.of("c", "d"), 1L);
        assertTrue(result.pass, result.message);

        // Test EACH_EQUALS with low threshold
        result = Match.execute(null, Match.Type.EACH_EQUALS, actual, "#string", 1L);
        assertTrue(result.pass, result.message);

        // Test failure case with low threshold
        result = Match.execute(null, Match.Type.EQUALS, actual, List.of("x", "y", "z"), 1L);
        assertFalse(result.pass);
    }

    @Test
    void testMatchWithLargeCollection() {
        // Create a list that would be "large" if threshold was lower
        // This tests that the matching logic works with the iterator pattern
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(Map.of("id", i, "name", "item" + i));
        }

        // Test CONTAINS match
        Result result = Match.that(items).contains(List.of(Map.of("id", 50, "name", "item50")));
        assertTrue(result.pass, result.message);

        // Test EACH_EQUALS match
        result = Match.that(items).eachEquals("#object");
        assertTrue(result.pass, result.message);

        // Test WITHIN match
        List<Map<String, Object>> subset = List.of(
            Map.of("id", 0, "name", "item0"),
            Map.of("id", 50, "name", "item50")
        );
        result = Match.that(subset).within(items);
        assertTrue(result.pass, result.message);
    }
}
