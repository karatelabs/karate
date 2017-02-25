package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author pthomas3
 */
public class XmlUtils {

    private static final Logger logger = LoggerFactory.getLogger(XmlUtils.class);

    private XmlUtils() {
        // only static methods
    }

    public static String toString(Node node) {
        DOMSource domSource = new DOMSource(node);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Document toXmlDoc(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            return builder.parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static XPathExpression compile(String path) {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        try {
            return xpath.compile(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Node getNodeByPath(Node node, String path) {
        XPathExpression expr = compile(path);
        try {
            return (Node) expr.evaluate(node, XPathConstants.NODE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getValueByPath(Node node, String path) {
        XPathExpression expr = compile(path);
        try {
            return expr.evaluate(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void setByPath(Node doc, String path, String value) {
        Node node = getNodeByPath(doc, path);
        if (node.hasChildNodes() && node.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            node.getFirstChild().setTextContent(value);
        } else {
            node.setNodeValue(value);
        }
    }

    public static void setByPath(Document doc, String path, Node in) {
        Node node = getNodeByPath(doc, path);
        if (in.getNodeType() == Node.DOCUMENT_NODE) {
            in = in.getFirstChild();
        }
        Node newNode = doc.importNode(in, true);
        node.getParentNode().replaceChild(newNode, node);
    }

    public static DocumentContext toJsonDoc(Node node) {
        return JsonPath.parse(toMap(node));
    }

    public static Object toMap(Node node) {
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE:
                Map<String, Object> root = new LinkedHashMap<>();
                node = node.getFirstChild();
                root.put(node.getNodeName(), toMap(node));
                return root;
            case Node.ELEMENT_NODE:
                NodeList nodes = node.getChildNodes();
                int childCount = nodes.getLength();
                Map<String, Object> map = new LinkedHashMap<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    Node child = nodes.item(i);
                    String childName = child.getNodeName();
                    String value = child.getNodeValue();
                    if (value != null) { // text node !
                        return value;
                    } else if (child.hasChildNodes()) {
                        Object childValue = toMap(child);
                        if (map.containsKey(childName)) {
                            Object temp = map.get(childName);
                            if (temp instanceof List) {
                                List list = (List) temp;
                                list.add(childValue);
                            } else {
                                List list = new ArrayList(childCount);
                                map.put(childName, list);
                                list.add(temp);
                                list.add(childValue);
                            }
                        } else {
                            map.put(childName, childValue);
                        }
                    }
                }
                return map;
            default:
                logger.warn("unexpected node: " + node);
                return new LinkedHashMap<>();
        }
    }

}
