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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

class XmlXpathTest {

    @Test
    void testGetNodeListByPath() {
        Document doc = Xml.toXmlDoc("<root><foo>bar</foo></root>");
        NodeList nodeList = Xml.getNodeListByPath(doc, "/root/foo");
        assertEquals(1, nodeList.getLength());
        assertEquals("bar", nodeList.item(0).getTextContent());
    }

    @Test
    void testGetTextValueByPath() {
        Document doc = Xml.toXmlDoc("<root><foo>bar</foo></root>");
        String value = Xml.getTextValueByPath(doc, "/root/foo");
        assertEquals("bar", value);
    }

    @Test
    void testGetNodeByPath() {
        Document doc = Xml.toXmlDoc("<root><foo>bar</foo></root>");
        org.w3c.dom.Node node = Xml.getNodeByPath(doc, "/root/foo", false);
        assertNotNull(node);
        assertEquals("bar", node.getTextContent());
    }

    @Test
    void testSetByPath() {
        Document doc = Xml.toXmlDoc("<root><foo>bar</foo></root>");
        Xml.setByPath(doc, "/root/foo", "baz");
        String value = Xml.getTextValueByPath(doc, "/root/foo");
        assertEquals("baz", value);
    }

    @Test
    void testRemoveByPath() {
        Document doc = Xml.toXmlDoc("<root><foo>bar</foo></root>");
        Xml.removeByPath(doc, "/root/foo");
        NodeList nodeList = Xml.getNodeListByPath(doc, "/root/foo");
        assertEquals(0, nodeList.getLength());
    }

}
