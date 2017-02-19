package com.intuit.karate;

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
        Node node = XmlUtils.getNodeByPath(doc, "/foo");
        assertEquals("foo", node.getNodeName());
        String value = XmlUtils.getValueByPath(doc, "/foo/bar");
        assertEquals("baz", value);
    }

    @Test
    public void testConvertingToMap() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Map<String, Object> map = XmlUtils.toMap(doc);
        logger.debug("map: {}", map);
        Map inner = (Map) map.get("foo");
        assertEquals("baz", inner.get("bar"));
    }

    @Test
    public void testComplexConversionToMap() {
        Document doc = XmlUtils.toXmlDoc(ACTUAL);
        Map<String, Object> map = XmlUtils.toMap(doc);
        logger.debug("map: {}", map);
        Map in1 = (Map) map.get("env:Envelope");
        Map in2 = (Map) in1.get("env:Body");
        Map in3 = (Map) in2.get("QueryUsageBalanceResponse");
        Map in4 = (Map) in3.get("Result");
        Map in5 = (Map) in4.get("Error");
        assertEquals("DAT_USAGE_1003", in5.get("Code"));
    }

    @Test
    public void testRepeatedXmlElementsToMap() {
        String xml = "<foo><bar>baz1</bar><bar>baz2</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Map<String, Object> map = XmlUtils.toMap(doc);
        logger.debug("map: {}", map);
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
        String value = XmlUtils.getValueByPath(doc, "/com.intuit.services.acs.domain.api.ACSDocumentDTO/EntityId");
        logger.debug("value: {}", value);
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
    public void testSetDomNodeByPath() {
        String xml = "<foo><bar>baz</bar></foo>";
        Document doc = XmlUtils.toXmlDoc(xml);
        Node temp = XmlUtils.toXmlDoc("<hello>world</hello>");
        XmlUtils.setByPath(doc, "/foo/bar", temp);
        String result = XmlUtils.toString(doc);
        assertEquals(result, "<foo><hello>world</hello></foo>");
    }       

}
