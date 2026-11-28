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

    @Test
    void testSerializeDescendantWithAncestorNamespaces() {
        // xsi declared on soap:Envelope, attribute used on a
        // nested element. Serializing the nested subtree must pull down
        // ancestor xmlns:* declarations or the Transformer throws
        // "Namespace for prefix 'xsi' has not been declared".
        String soap = """
                <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Body>
                    <outer xmlns="http://example">
                      <inner xsi:nil="true"/>
                    </outer>
                  </soap:Body>
                </soap:Envelope>
                """;
        Document doc = Xml.toXmlDoc(soap);
        org.w3c.dom.Node outer = Xml.getNodeByPath(doc, "//*[local-name()='outer']", false);
        assertNotNull(outer);
        String s = Xml.toString(outer);
        assertTrue(s.contains("xsi:nil=\"true\""));
        assertTrue(s.contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""));
    }

    static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    static final String SOAP = "<soap:Envelope xmlns:soap=\"" + SOAP_NS + "\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<soap:Body><ns2:AddResponse xmlns:ns2=\"urn:calc\">"
            + "<ns2:AddResult>5</ns2:AddResult><ns2:note xsi:nil=\"true\"/>"
            + "</ns2:AddResponse></soap:Body></soap:Envelope>";

    @Test
    void testNamespaceAwareParse() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        assertEquals(SOAP_NS, doc.getDocumentElement().getNamespaceURI());
        assertEquals("Envelope", doc.getDocumentElement().getLocalName());
        assertTrue(Xml.isNamespaceAware(doc));
        assertFalse(Xml.isNamespaceAware(Xml.toXmlDoc(SOAP)));
        assertFalse(Xml.isNamespaceAware(Xml.fromObject("root", "x")));
    }

    @Test
    void testNamespaceAwarePrefixedXpath() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        assertEquals("5", Xml.getTextValueByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult"));
        assertEquals(1, Xml.getNodeListByPath(doc, "//ns2:AddResult").getLength());
        org.w3c.dom.Node node = Xml.getNodeByPath(doc, "//ns2:note/@xsi:nil", false);
        assertEquals("true", node.getNodeValue());
        org.w3c.dom.Node body = Xml.getNodeListByPath(doc, "/soap:Envelope/soap:Body").item(0);
        assertEquals("5", Xml.getTextValueByPath(body, "ns2:AddResponse/ns2:AddResult"));
    }

    @Test
    void testNamespaceAwareSetExistingAndNew() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        Xml.setByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult", "6");
        assertEquals("6", Xml.getTextValueByPath(doc, "//ns2:AddResult"));
        assertEquals(1, Xml.getNodeListByPath(doc, "/soap:Envelope/soap:Body").getLength());
        Xml.setByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/ns2:extra", "x");
        org.w3c.dom.Node extra = Xml.getNodeByPath(doc, "//ns2:extra", false);
        assertNotNull(extra);
        assertEquals("urn:calc", extra.getNamespaceURI());
        assertEquals("extra", extra.getLocalName());
        Xml.setByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/@xsi:type", "t");
        assertEquals("t", Xml.getTextValueByPath(doc, "//ns2:AddResponse/@xsi:type"));
        assertEquals("<soap:Envelope xmlns:soap=\"" + SOAP_NS + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<soap:Body><ns2:AddResponse xmlns:ns2=\"urn:calc\" xsi:type=\"t\">"
                + "<ns2:AddResult>6</ns2:AddResult><ns2:note xsi:nil=\"true\"/><ns2:extra>x</ns2:extra>"
                + "</ns2:AddResponse></soap:Body></soap:Envelope>", Xml.toString(doc));
    }

    @Test
    void testNamespaceAwareSetNode() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        Xml.setByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult", Xml.toXmlDoc("<b>7</b>", true));
        assertEquals("7", Xml.getTextValueByPath(doc, "//ns2:AddResult/b"));
    }

    @Test
    void testNamespaceAwareRemove() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        Xml.removeByPath(doc, "/soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult");
        Xml.removeByPath(doc, "//ns2:note/@xsi:nil");
        assertEquals("<soap:Envelope xmlns:soap=\"" + SOAP_NS + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<soap:Body><ns2:AddResponse xmlns:ns2=\"urn:calc\"><ns2:note/></ns2:AddResponse></soap:Body></soap:Envelope>",
                Xml.toString(doc));
    }

    @Test
    void testNamespaceAwareSerializeDescendant() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        org.w3c.dom.Node note = Xml.getNodeByPath(doc, "//ns2:note", false);
        assertEquals("<ns2:note xmlns:ns2=\"urn:calc\" xmlns:soap=\"" + SOAP_NS + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>",
                Xml.toString(note));
        Document lifted = Xml.toNewDocument(note);
        assertEquals("urn:calc", lifted.getDocumentElement().getNamespaceURI());
        assertEquals("true", Xml.getTextValueByPath(lifted, "/ns2:note/@xsi:nil"));
    }

    @Test
    void testNamespaceAwarePrettyAndToObject() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        assertEquals(Xml.toString(Xml.toXmlDoc(SOAP), true), Xml.toString(doc, true));
        assertEquals(Xml.toObject(Xml.toXmlDoc(SOAP)), Xml.toObject(doc));
        assertEquals(Xml.toObject(Xml.toXmlDoc(SOAP), true), Xml.toObject(doc, true));
    }

    static final java.util.Map<String, String> BINDINGS = java.util.Map.of("env", SOAP_NS, "c", "urn:calc");

    static final String SOAP_OTHER_PREFIXES = "<s:Envelope xmlns:s=\"" + SOAP_NS + "\">"
            + "<s:Body><AddResponse xmlns=\"urn:calc\"><AddResult>5</AddResult></AddResponse></s:Body></s:Envelope>";

    @Test
    void testConfiguredNamespacesResolveWhateverPrefixTheDocumentUses() {
        for (String xml : new String[]{SOAP, SOAP_OTHER_PREFIXES}) {
            Document doc = Xml.toXmlDoc(xml, true);
            assertEquals("5", Xml.getTextValueByPath(doc, "/env:Envelope/env:Body/c:AddResponse/c:AddResult", BINDINGS));
            assertEquals(1, Xml.getNodeListByPath(doc, "//c:AddResult", BINDINGS).getLength());
            assertNotNull(Xml.getNodeByPath(doc, "//c:AddResponse", false, BINDINGS));
        }
        Document doc = Xml.toXmlDoc(SOAP, true);
        // a document prefix the bindings do not override still resolves
        assertEquals("5", Xml.getTextValueByPath(doc, "//ns2:AddResult", BINDINGS));
        // a configured binding wins over the document's own use of the same prefix
        assertEquals("", Xml.getTextValueByPath(doc, "//ns2:AddResult", java.util.Map.of("ns2", "urn:other")));
    }

    @Test
    void testConfiguredNamespacesSetAndRemove() {
        Document doc = Xml.toXmlDoc(SOAP_OTHER_PREFIXES, true);
        Xml.setByPath(doc, "/env:Envelope/env:Body/c:AddResponse/c:AddResult", "6", BINDINGS);
        Xml.setByPath(doc, "/env:Envelope/env:Body/c:AddResponse/c:Note", "n", BINDINGS);
        Xml.setByPath(doc, "/env:Envelope/env:Header/c:Trace", "t", BINDINGS);
        assertEquals("<s:Envelope xmlns:s=\"" + SOAP_NS + "\"><s:Body><AddResponse xmlns=\"urn:calc\">"
                + "<AddResult>6</AddResult><c:Note xmlns:c=\"urn:calc\">n</c:Note></AddResponse></s:Body>"
                + "<s:Header><c:Trace xmlns:c=\"urn:calc\">t</c:Trace></s:Header></s:Envelope>", Xml.toString(doc));
        Document reparsed = Xml.toXmlDoc(Xml.toString(doc), true);
        assertEquals("n", Xml.getTextValueByPath(reparsed, "//c:Note", BINDINGS));
        Xml.removeByPath(doc, "/env:Envelope/env:Body/c:AddResponse/c:Note", BINDINGS);
        assertEquals(0, Xml.getNodeListByPath(doc, "//c:Note", BINDINGS).getLength());
        Document soap = Xml.toXmlDoc(SOAP, true);
        Xml.setByPath(soap, "/env:Envelope/env:Body/c:AddResponse/c:Note", "n", BINDINGS);
        assertTrue(Xml.toString(soap).contains("<ns2:Note>n</ns2:Note>"));
    }

    @Test
    void testUnknownPrefixOnCreateFails() {
        Document doc = Xml.toXmlDoc(SOAP, true);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> Xml.setByPath(doc, "/soap:Envelope/soap:Body/zz:New", "x"));
        assertTrue(e.getMessage().contains("zz"), e.getMessage());
    }

    @Test
    void testToExpandedObject() {
        Object a = Xml.toExpandedObject(Xml.toXmlDoc(SOAP, true));
        String other = "<e:Envelope xmlns:e=\"" + SOAP_NS + "\" xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<e:Body><AddResponse xmlns=\"urn:calc\"><AddResult>5</AddResult><note i:nil=\"true\"/></AddResponse></e:Body></e:Envelope>";
        assertEquals(a, Xml.toExpandedObject(Xml.toXmlDoc(other, true)));
        String env = "{" + SOAP_NS + "}";
        java.util.Map<String, Object> note = new java.util.LinkedHashMap<>();
        note.put("_", null);
        note.put("@", java.util.Map.of("{http://www.w3.org/2001/XMLSchema-instance}nil", "true"));
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("{urn:calc}AddResult", "5");
        response.put("{urn:calc}note", note);
        assertEquals(java.util.Map.of(env + "Envelope", java.util.Map.of(env + "Body", java.util.Map.of("{urn:calc}AddResponse", response))), a);
        Object repeated = Xml.toExpandedObject(Xml.toXmlDoc("<a xmlns=\"urn:a\" x=\"1\"><b>1</b><b>2</b></a>", true));
        assertEquals(java.util.Map.of("{urn:a}a", java.util.Map.of("_", java.util.Map.of("{urn:a}b", java.util.List.of("1", "2")), "@", java.util.Map.of("x", "1"))), repeated);
        assertNotEquals(a, Xml.toExpandedObject(Xml.toXmlDoc(SOAP.replace("urn:calc", "urn:other"), true)));
    }

    @Test
    void testGetNamespaceUriWithOrWithoutNamespaceAwareness() {
        for (boolean aware : new boolean[]{false, true}) {
            assertEquals(SOAP_NS, Xml.getNamespaceUri(Xml.toXmlDoc(SOAP, aware).getDocumentElement()));
            assertEquals(SOAP_NS, Xml.getNamespaceUri(Xml.toXmlDoc(SOAP_OTHER_PREFIXES, aware).getDocumentElement()));
            Document doc = Xml.toXmlDoc(SOAP_OTHER_PREFIXES, aware);
            org.w3c.dom.Element result = (org.w3c.dom.Element) doc.getElementsByTagName("AddResult").item(0);
            assertEquals("urn:calc", Xml.getNamespaceUri(result));
            assertNull(Xml.getNamespaceUri(Xml.toXmlDoc("<a><b/></a>", aware).getDocumentElement()));
        }
    }

    @Test
    void testNamespaceAwareSetWithMismatchedRootFailsAndLeavesTheDocument() {
        Document doc = Xml.toXmlDoc("<a:r xmlns:a=\"urn:a\"/>", true);
        java.util.Map<String, String> z = java.util.Map.of("z", "urn:z");
        RuntimeException e = assertThrows(RuntimeException.class, () -> Xml.setByPath(doc, "/z:r/z:b", "x", z));
        assertTrue(e.getMessage().contains("{urn:a}r"), e.getMessage());
        assertThrows(RuntimeException.class, () -> Xml.setByPath(doc, "/r/b", "x"));
        assertEquals("<a:r xmlns:a=\"urn:a\"/>", Xml.toString(doc));
        Xml.setByPath(doc, "/a:r/a:b", "x");
        assertEquals("<a:r xmlns:a=\"urn:a\"><a:b>x</a:b></a:r>", Xml.toString(doc));
        Document plain = Xml.toXmlDoc("<a:r xmlns:a=\"urn:a\"/>");
        Xml.setByPath(plain, "/z:r/z:b", "x");
        assertEquals("<a:r xmlns:a=\"urn:a\"><z:b>x</z:b></a:r>", Xml.toString(plain));
    }

    @Test
    void testNamespaceAwareSetNeverRebindsAPrefixInScope() {
        Document doc = Xml.toXmlDoc("<r xmlns:p=\"urn:old\" p:id=\"1\"/>", true);
        java.util.Map<String, String> bindings = java.util.Map.of("p", "urn:new");
        Xml.setByPath(doc, "/r/@p:id", "2", bindings);
        Xml.setByPath(doc, "/r/p:child", "c", bindings);
        Document reparsed = Xml.toXmlDoc(Xml.toString(doc), true);
        org.w3c.dom.Element root = reparsed.getDocumentElement();
        assertEquals("1", root.getAttributeNS("urn:old", "id"));
        assertEquals("2", root.getAttributeNS("urn:new", "id"));
        assertEquals("c", Xml.getTextValueByPath(reparsed, "/r/p:child", bindings));
        assertEquals("1", Xml.getTextValueByPath(reparsed, "/r/@p:id"));
        assertEquals("2", Xml.getTextValueByPath(reparsed, "/r/@p:id", bindings));
        Document sameUri = Xml.toXmlDoc("<r xmlns:q=\"urn:new\"/>", true);
        Xml.setByPath(sameUri, "/r/@p:id", "3", bindings);
        assertEquals("<r xmlns:q=\"urn:new\" q:id=\"3\"/>", Xml.toString(sameUri));
    }

    @Test
    void testNamespaceAwareSetImportsAPrefixBlindNodeByNamespace() {
        org.w3c.dom.Node saved = Xml.toXmlDoc("<p:item xmlns:p=\"urn:x\"><p:v>1</p:v></p:item>").getDocumentElement();
        Document doc = Xml.toXmlDoc("<r><slot/></r>", true);
        Xml.setByPath(doc, "/r/slot", saved);
        assertEquals(Xml.toExpandedObject(Xml.toXmlDoc("<r><slot><q:item xmlns:q=\"urn:x\"><q:v>1</q:v></q:item></slot></r>", true)),
                Xml.toExpandedObject(doc));
    }

    @Test
    void testNamespaceAwareSetXmlPrefixedAttribute() {
        Document doc = Xml.toXmlDoc("<r/>", true);
        Xml.setByPath(doc, "/r/@xml:lang", "en");
        assertEquals("<r xml:lang=\"en\"/>", Xml.toString(doc));
        assertEquals("en", Xml.getTextValueByPath(doc, "/r/@xml:lang"));
    }

    @Test
    void testGeneratedPrefixIsUnusedAnywhereInTheDocument() {
        String xml = "<r xmlns:p=\"urn:old\"><ns0:item xmlns:ns0=\"urn:item\">ok</ns0:item></r>";
        Document doc = Xml.toXmlDoc(xml, true);
        java.util.Map<String, String> bindings = java.util.Map.of("p", "urn:new", "ns1", "urn:configured");
        Xml.setByPath(doc, "/r/@p:id", "2", bindings);
        assertEquals("ok", Xml.getTextValueByPath(doc, "//ns0:item"));
        Document reparsed = Xml.toXmlDoc(Xml.toString(doc), true);
        assertEquals("ok", Xml.getTextValueByPath(reparsed, "//ns0:item"));
        assertEquals("2", reparsed.getDocumentElement().getAttributeNS("urn:new", "id"));
        assertEquals("2", Xml.getTextValueByPath(reparsed, "/r/@p:id", bindings));
        assertTrue(Xml.toString(doc).contains("xmlns:ns2=\"urn:new\""), Xml.toString(doc));
    }

}
