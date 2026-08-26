package io.karatelabs.core;

import io.karatelabs.common.Xml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StepUtilsTest {

    @Test
    void testIsSingleQuotedLiteral() {
        assertTrue(StepUtils.isSingleQuotedLiteral("'file.feature'"));
        assertTrue(StepUtils.isSingleQuotedLiteral("\"file.feature\""));
        assertTrue(StepUtils.isSingleQuotedLiteral("'c:\\dir\\file.feature'"));
        assertTrue(StepUtils.isSingleQuotedLiteral("'has \"other\" quotes'"));
        // concatenations start and end with a quote but are not literals
        assertFalse(StepUtils.isSingleQuotedLiteral("'a-' + ext + '.feature'"));
        assertFalse(StepUtils.isSingleQuotedLiteral("\"a-\" + ext + \".feature\""));
        assertFalse(StepUtils.isSingleQuotedLiteral("'a' + b"));
        assertFalse(StepUtils.isSingleQuotedLiteral("a + '.feature'"));
        assertFalse(StepUtils.isSingleQuotedLiteral("pathVar"));
        assertFalse(StepUtils.isSingleQuotedLiteral("'"));
        assertFalse(StepUtils.isSingleQuotedLiteral(""));
    }

    @Test
    void testDeepCopyNull() {
        assertNull(StepUtils.deepCopy(null));
    }

    @Test
    void testDeepCopyPrimitivesAndStrings() {
        assertEquals(42, StepUtils.deepCopy(42));
        assertEquals("hello", StepUtils.deepCopy("hello"));
        assertEquals(true, StepUtils.deepCopy(true));
        assertEquals(3.14, StepUtils.deepCopy(3.14));
    }

    @Test
    void testDeepCopyByteArray() {
        byte[] original = new byte[]{1, 2, 3, 4};
        Object copyObj = StepUtils.deepCopy(original);

        assertInstanceOf(byte[].class, copyObj);
        byte[] copy = (byte[]) copyObj;
        assertNotSame(original, copy);
        assertArrayEquals(original, copy);

        // Mutating copy should not affect original
        copy[0] = 99;
        assertEquals(1, original[0]);
    }

    @Test
    void testDeepCopyPrimitiveArrays() {
        int[] originalInts = new int[]{10, 20, 30};
        int[] copyInts = (int[]) StepUtils.deepCopy(originalInts);
        assertNotSame(originalInts, copyInts);
        assertArrayEquals(originalInts, copyInts);

        boolean[] originalBools = new boolean[]{true, false, true};
        boolean[] copyBools = (boolean[]) StepUtils.deepCopy(originalBools);
        assertNotSame(originalBools, copyBools);
        assertArrayEquals(originalBools, copyBools);

        char[] originalChars = new char[]{'a', 'b', 'c'};
        char[] copyChars = (char[]) StepUtils.deepCopy(originalChars);
        assertNotSame(originalChars, copyChars);
        assertArrayEquals(originalChars, copyChars);

        long[] originalLongs = new long[]{100L, 200L};
        long[] copyLongs = (long[]) StepUtils.deepCopy(originalLongs);
        assertNotSame(originalLongs, copyLongs);
        assertArrayEquals(originalLongs, copyLongs);

        double[] originalDoubles = new double[]{1.1, 2.2};
        double[] copyDoubles = (double[]) StepUtils.deepCopy(originalDoubles);
        assertNotSame(originalDoubles, copyDoubles);
        assertArrayEquals(originalDoubles, copyDoubles);
    }

    @Test
    void testDeepCopyObjectArray() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        byte[] bytes = new byte[]{1, 2, 3};

        Object[] original = new Object[]{"test", map, bytes};
        Object[] copy = (Object[]) StepUtils.deepCopy(original);

        assertNotSame(original, copy);
        assertEquals(original.length, copy.length);
        assertEquals("test", copy[0]);

        // Nested map inside array should be deep copied
        assertNotSame(map, copy[1]);
        assertEquals(map, copy[1]);

        // Nested byte array inside array should be deep copied
        assertNotSame(bytes, copy[2]);
        assertArrayEquals(bytes, (byte[]) copy[2]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeepCopyMapWithByteArrayAndNestedStructures() {
        Map<String, Object> map = new LinkedHashMap<>();
        byte[] rawBytes = "binary payload".getBytes();
        map.put("raw", rawBytes);
        map.put("name", "test");

        List<Object> list = new ArrayList<>();
        list.add(new byte[]{4, 5, 6});
        map.put("listWithBytes", list);

        Map<String, Object> copy = (Map<String, Object>) StepUtils.deepCopy(map);

        assertNotSame(map, copy);
        assertEquals("test", copy.get("name"));

        byte[] copiedBytes = (byte[]) copy.get("raw");
        assertNotSame(rawBytes, copiedBytes);
        assertArrayEquals(rawBytes, copiedBytes);

        List<Object> copiedList = (List<Object>) copy.get("listWithBytes");
        assertNotSame(list, copiedList);
        byte[] listBytesOriginal = (byte[]) list.getFirst();
        byte[] listBytesCopied = (byte[]) copiedList.getFirst();
        assertNotSame(listBytesOriginal, listBytesCopied);
        assertArrayEquals(listBytesOriginal, listBytesCopied);
    }

    @Test
    void testDeepCopyStringArrayPreservesType() {
        String[] original = new String[]{"alpha", "beta", "gamma"};
        Object copyObj = StepUtils.deepCopy(original);

        assertInstanceOf(String[].class, copyObj);
        String[] copy = (String[]) copyObj;
        assertNotSame(original, copy);
        assertArrayEquals(original, copy);
    }

    @Test
    void testDeepCopyXmlNode() {
        Document original = Xml.toXmlDoc("<root><a>1</a></root>");
        Node copy = (Node) StepUtils.deepCopy(original);

        assertNotSame(original, copy);
        // XML nodes are mutable via set-by-xpath, so the copy must be a detached tree
        Xml.setByPath(copy, "/root/a", "2");
        assertEquals("1", Xml.getTextValueByPath(original, "/root/a"));
        assertEquals("2", Xml.getTextValueByPath(copy, "/root/a"));
    }

    @Test
    void testDeepCopyXmlElementDetachesFromOwnerDocument() {
        Document doc = Xml.toXmlDoc("<root><a>1</a></root>");
        Element element = doc.getDocumentElement();
        Element copy = (Element) StepUtils.deepCopy(element);

        // set-by-xpath resolves through getOwnerDocument(), so a copy still owned
        // by the original document would route xpath writes to the original tree
        assertNotSame(doc, copy.getOwnerDocument());
        Xml.setByPath(copy.getOwnerDocument(), "/root/a", "2");
        assertEquals("1", Xml.getTextValueByPath(doc, "/root/a"));
        assertEquals("2", Xml.getTextValueByPath(copy, "/root/a"));
    }

    @Test
    void testDeepCopyTypedArrayFallsBackWhenElementTypeChanges() {
        TreeMap<String, Object> treeMap = new TreeMap<>();
        treeMap.put("key", "value");
        TreeMap<?, ?>[] original = new TreeMap<?, ?>[]{treeMap};

        // the copied element becomes a LinkedHashMap, which a TreeMap[] would
        // reject with ArrayStoreException — the copy must degrade to Object[]
        Object[] copy = (Object[]) StepUtils.deepCopy(original);
        assertEquals(1, copy.length);
        assertInstanceOf(Map.class, copy[0]);
        assertNotSame(treeMap, copy[0]);
        assertEquals(treeMap, copy[0]);
    }

    @Test
    void testDeepCopySet() {
        Set<Object> original = new LinkedHashSet<>();
        original.add("keep");
        Map<String, Object> nested = new HashMap<>();
        nested.put("k", "v");
        original.add(nested);

        @SuppressWarnings("unchecked")
        Set<Object> copy = (Set<Object>) StepUtils.deepCopy(original);
        assertNotSame(original, copy);
        assertEquals(2, copy.size());
        assertTrue(copy.contains("keep"));

        copy.add("extra");
        assertEquals(2, original.size());
    }

    @Test
    void testDeepCopyMultidimensionalByteArray() {
        byte[][] original = new byte[][]{{1, 2}, {3, 4, 5}};
        Object copyObj = StepUtils.deepCopy(original);

        assertInstanceOf(byte[][].class, copyObj);
        byte[][] copy = (byte[][]) copyObj;
        assertNotSame(original, copy);
        assertEquals(2, copy.length);
        assertNotSame(original[0], copy[0]);
        assertArrayEquals(original[0], copy[0]);
        assertNotSame(original[1], copy[1]);
        assertArrayEquals(original[1], copy[1]);
    }
}
