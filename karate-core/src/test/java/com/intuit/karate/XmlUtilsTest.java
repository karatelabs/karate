package com.intuit.karate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class XmlUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(XmlUtilsTest.class);

    private final String ACTUAL = "<env:Envelope xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><env:Header/><env:Body xmlns=\"http://www.intuit.com/iep/ServiceUsage/IntuitServiceUsageABO/V1\"><QueryUsageBalanceResponse xmlns=\"http://www.intuit.com/iep/ServiceUsage/IntuitServiceUsageABO/V1\"><Balance/><Result><Success/><Error><Category>DAT</Category><Code>DAT_USAGE_1003</Code><Description>Invalid Request: Invalid Input criteria: No asset found for license/eoc (630289335971198/855939).</Description><Source>SIEBEL</Source></Error></Result></QueryUsageBalanceResponse></env:Body></env:Envelope>";

    @Test
    public void testParsing() {
        String xml = "<foo></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        String rootName = doc.getDocumentElement().getNodeName();
        assertEquals("foo", rootName);
    }

    @Test
    public void testXpath() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node node = XmlUtils.getNodeByPath(doc, "/foo", false);
        assertEquals("foo", node.getNodeName());
        String value = XmlUtils.getTextValueByPath(doc, "/foo/bar");
        assertEquals("baz", value);
    }

    @Test
    public void testConvertingToMap() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Map<String, Object> map = (Map) XmlUtils.toObject(doc);
        logger.trace("map: {}", map);
        Map inner = (Map) map.get("foo");
        assertEquals("baz", inner.get("bar"));
    }

    @Test
    public void testConvertingToMapWithoutNamespace() {
        String xml = "<foo xmlns=\"foobar\"><a:bar xmlns:a=\"test\">baz</a:bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Map<String, Object> map = (Map) XmlUtils.toObject(doc, true);
        logger.trace("map: {}", map);
        Map inner = (Map) map.get("foo");
        assertEquals("baz", inner.get("bar"));
    }

    @Test
    public void testComplexConversionToMap() {
        Document doc = XmlUtils.toXmlDoc(ACTUAL);
        Map<String, Object> map = (Map) XmlUtils.toObject(doc);
        logger.debug("map: {}", map);
        Map in1 = (Map) map.get("env:Envelope");
        Map in11 = (Map) in1.get("_");
        Map in2 = (Map) in11.get("env:Body");
        Map in22 = (Map) in2.get("_");
        Map in3 = (Map) in22.get("QueryUsageBalanceResponse");
        Map in33 = (Map) in3.get("_");
        Map in4 = (Map) in33.get("Result");
        Map in5 = (Map) in4.get("Error");
        assertEquals("DAT_USAGE_1003", in5.get("Code"));
    }

    @Test
    public void testRepeatedXmlElementsToMap() {
        String xml = "<foo><bar>baz1</bar><bar>baz2</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Map<String, Object> map = (Map) XmlUtils.toObject(doc);
        logger.trace("map: {}", map);
        Map in1 = (Map) map.get("foo");
        List list = (List) in1.get("bar");
        assertEquals(2, list.size());
        assertEquals("baz1", list.get(0));
        assertEquals("baz2", list.get(1));
    }

    @Test
    public void testAnotherXpath() {
        String xml = "<com.intuit.services.acs.domain.api.ACSDocumentDTO>\n"
                + "  <EntityId>b14712d1-df91-4111-a77f-ce48f066b4ab</EntityId>\n"
                + "  <Name>test.pdf</Name>\n"
                + "  <Size>100250</Size>\n"
                + "  <Created>2016-12-23 22:08:36.90 PST</Created>\n"
                + "  <Properties/>\n"
                + "</com.intuit.services.acs.domain.api.ACSDocumentDTO>";
        Document doc = XmlUtils.toXmlDoc(xml);
        String value = XmlUtils.getTextValueByPath(doc, "/com.intuit.services.acs.domain.api.ACSDocumentDTO/EntityId");
        logger.trace("value: {}", value);
        assertEquals("b14712d1-df91-4111-a77f-ce48f066b4ab", value);
    }

    @Test
    public void testSetStringValueByPath() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        XmlUtils.setByPath(doc, "/foo/bar", "hello");
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar>hello</bar></foo>");
    }

    @Test
    public void testReplaceDomNodeByPath() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node temp = XmlUtils.toXmlDoc("<hello>world</hello>");
        XmlUtils.setByPath(doc, "/foo/bar", temp);
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar><hello>world</hello></bar></foo>");
    }

    @Test
    public void testAppendDomNodeByPath() {
        String xml = "<foo><bar/></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node temp = XmlUtils.toXmlDoc("<hello>world</hello>");
        XmlUtils.setByPath(doc, "/foo/bar", temp);
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar><hello>world</hello></bar></foo>");
    }

    @Test
    public void testSetDomNodeWithAttributeByPath() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node temp = XmlUtils.toXmlDoc("<baz hello=\"world\">ban</baz>");
        XmlUtils.setByPath(doc, "/foo/bar", temp);
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar><baz hello=\"world\">ban</baz></bar></foo>");
    }
    
    @Test
    public void testCreateElementByPath() {
        Document doc = XmlUtils.newDocument();
        XmlUtils.createNodeByPath(doc, "/foo/bar");
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar/></foo>");
    }
    
   @Test
    public void testSetElementCreatingNonExistentParents() {
        String xml = "<foo></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node temp = XmlUtils.toXmlDoc("<hello>world</hello>");
        XmlUtils.setByPath(doc, "/foo/bar", temp);
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar><hello>world</hello></bar></foo>");
    }

   @Test
    public void testSetAttributeCreatingNonExistentParents() {
        String xml = "<foo></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        XmlUtils.setByPath(doc, "/foo/bar/@baz", "ban");
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><bar baz=\"ban\"/></foo>");
    }      
    
    private Document getDocument() {
        return XmlUtils.newDocument();
    }

    @Test
    public void testCreateElement() {
        Node node = XmlUtils.createElement(getDocument(), "foo", "bar", null);
        String result = XmlUtils.toString(node);
        assertEquals(result, "<foo>bar</foo>");
    }

    @Test
    public void testCreateElementWithAttributes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hello", "world");
        Node node = XmlUtils.createElement(getDocument(), "foo", "bar", map);
        String result = XmlUtils.toString(node);
        assertEquals(result, "<foo hello=\"world\">bar</foo>");
    }

    @Test
    public void testXmlFromMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hello", "world");
        Node node = XmlUtils.fromObject("foo", map);
        String result = XmlUtils.toString(node);
        assertEquals(result, "<foo><hello>world</hello></foo>");
    }

    @Test
    public void testXmlWithAttributesFromMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_", "world");
        Map<String, Object> attribs = new LinkedHashMap<>();
        attribs.put("foo", "bar");
        map.put("@", attribs);
        Node node = XmlUtils.fromObject("hello", map);
        String result = XmlUtils.toString(node);
        assertEquals(result, "<hello foo=\"bar\">world</hello>");
    }

    @Test
    public void testPrettyPrint() {
        String xml = "<foo><bar>baz</bar><ban><goo>moo</goo></ban></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        String temp = XmlUtils.toString(doc, true);
        String expected
                = "<foo>\n"
                + "  <bar>baz</bar>\n"
                + "  <ban>\n"
                + "    <goo>moo</goo>\n"
                + "  </ban>\n"
                + "</foo>\n";
        
        expected = expected.replace("\n", System.lineSeparator());
        assertEquals(temp, expected);
    }
    
    @Test
    public void testCreatingNewDocumentFromSomeChildNode() {
        String xml = "<root><foo><bar>baz</bar></foo></root>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node node = XmlUtils.getNodeByPath(doc, "/root/foo", false);
        Document tempDoc = XmlUtils.toNewDocument(node);
        String tempString = XmlUtils.getTextValueByPath(tempDoc, "/foo/bar");
        assertEquals(tempString, "baz");
        Node tempNode = XmlUtils.getNodeByPath(tempDoc, "/", false);
        assertEquals(XmlUtils.toString(tempNode), "<foo><bar>baz</bar></foo>");
    }
    
    public final static String TEACHERS_XML = "<teachers>\n"
            + "	<teacher department=\"science\" id=\"309\">\n"
            + "		<subject>math</subject>\n"
            + "		<subject>physics</subject>\n"
            + "	</teacher>\n"
            + "	<teacher department=\"arts\" id=\"310\">\n"
            + "		<subject>political education</subject>\n"
            + "		<subject>english</subject>\n"
            + "	</teacher>\n"
            + "</teachers>";    
    
    @Test
    public void testConversionToMapRoundTrip() {
        String xpath = "//teacher[@department='science']/subject";
        Node node = XmlUtils.toXmlDoc(TEACHERS_XML.replaceAll("\n\\s*", ""));
        ScriptValue sv1 = Script.evalXmlPathOnXmlNode(node, xpath);
        assertTrue(sv1.getType() == ScriptValue.Type.LIST);
        String before = XmlUtils.toString(node);
        Map map = (Map) XmlUtils.toObject(node);
        Node temp = XmlUtils.fromMap(map);
        ScriptValue sv2 = Script.evalXmlPathOnXmlNode(temp, xpath);
        assertTrue(sv2.getType() == ScriptValue.Type.LIST);
        String after = XmlUtils.toString(temp);
        assertEquals(after, before);
    }
    
    @Test
    public void testStripNameSpacePrefixes() {
        assertEquals("/", XmlUtils.stripNameSpacePrefixes("/"));
        assertEquals("/foo", XmlUtils.stripNameSpacePrefixes("/foo"));
        assertEquals("/bar", XmlUtils.stripNameSpacePrefixes("/foo:bar"));
        assertEquals("/bar/baz", XmlUtils.stripNameSpacePrefixes("/foo:bar/foo:baz"));
        assertEquals("/bar/baz/@ban", XmlUtils.stripNameSpacePrefixes("/foo:bar/foo:baz/@ban"));
    }

}
