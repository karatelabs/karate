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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPathFactory;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Xml uses the JDK's built-in JAXP implementations whatever else is on the classpath. A system
 * property naming an implementation class is looked up ahead of META-INF/services, which is how a
 * library such as Saxon or Xalan swaps itself in, so pointing the properties at a class that does
 * not exist makes any lookup that honours them fail.
 */
class XmlFactoryTest {

    static final String[] PROPERTIES = {
            TransformerFactory.class.getName(),
            DocumentBuilderFactory.class.getName(),
            XPathFactory.class.getName() + ":" + XPathFactory.DEFAULT_OBJECT_MODEL_URI
    };

    @Test
    void testClasspathImplementationsAreNeverPicked() {
        Map<String, String> saved = new LinkedHashMap<>();
        for (String name : PROPERTIES) {
            saved.put(name, System.getProperty(name));
            System.setProperty(name, "com.example.NotOnTheClasspath");
        }
        try {
            Document doc = Xml.toXmlDoc("<s:a xmlns:s=\"urn:s\"><s:b>1</s:b></s:a>", true);
            assertEquals("1", Xml.getTextValueByPath(doc, "/s:a/s:b"));
            assertEquals("<s:a xmlns:s=\"urn:s\"><s:b>1</s:b></s:a>", Xml.toString(doc));
            assertNotNull(Xml.newDocument());
        } finally {
            saved.forEach((name, value) -> {
                if (value == null) {
                    System.clearProperty(name);
                } else {
                    System.setProperty(name, value);
                }
            });
        }
    }

    @Test
    void testFactoriesAreTheJdkDefaults() {
        assertTrue(TransformerFactory.newDefaultInstance().getClass().getName().startsWith("com.sun.org.apache."));
        assertEquals(TransformerFactory.newDefaultInstance().getClass(), Xml.transformerFactory().getClass());
        assertEquals(DocumentBuilderFactory.newDefaultInstance().getClass(), Xml.documentBuilderFactory().getClass());
        assertEquals(XPathFactory.newDefaultInstance().getClass(), Xml.xpathFactory().getClass());
    }

}
