/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author pthomas3
 */
public class XmlUtils {

    private XmlUtils() {
        // only static methods
    }

    public static String toString(Node node) {
        return toString(node, false);
    }    
    
    public static String toString(Node node, boolean pretty) {
        DOMSource domSource = new DOMSource(node);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            if (pretty) {
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            }
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

    public static String getTextValueByPath(Node node, String path) {
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
        if (in.getNodeType() == Node.DOCUMENT_NODE) {
            in = in.getFirstChild();
        }        
        Node node = getNodeByPath(doc, path);
        if (node == null) {
            throw new RuntimeException("no results for xpath: " + path);
        }
        Node newNode = doc.importNode(in, true);
        if (node.hasChildNodes() && node.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            node.replaceChild(newNode, node.getFirstChild());
        } else {
            node.appendChild(newNode);
        }
    }

    public static DocumentContext toJsonDoc(Node node) {
        return JsonPath.parse(toObject(node));
    }

    private static Map<String, Object> getAttributes(Node node) {
        NamedNodeMap attribs = node.getAttributes();
        int attribCount = attribs.getLength();
        Map<String, Object> map = new LinkedHashMap<>(attribCount);
        for (int j = 0; j < attribCount; j++) {
            Node attrib = attribs.item(j);
            map.put(attrib.getNodeName(), attrib.getNodeValue());
        }
        return map;
    }
    
    public static int getChildElementCount(Node node) {
        NodeList nodes = node.getChildNodes();
        int childCount = nodes.getLength();
        int childElementCount = 0;
        for (int i = 0; i < childCount; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childElementCount++;
            }
        }
        return childElementCount;
    }
    
    private static Object getElementAsObject(Node node) {
        int childElementCount = getChildElementCount(node);
        if (childElementCount == 0) {
            return node.getTextContent();
        }
        Map<String, Object> map = new LinkedHashMap<>(childElementCount);
        NodeList nodes = node.getChildNodes();
        int childCount = nodes.getLength();        
        for (int i = 0; i < childCount; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childName = child.getNodeName();
            Object childValue = child.hasChildNodes() ? toObject(child) : null;
            // auto detect repeating elements
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
        return map;       
    }

    public static Object toObject(Node node) {        
        if (node.getNodeType() == Node.DOCUMENT_NODE) {            
            node = node.getFirstChild();
            Map<String, Object> map = new LinkedHashMap<>(1);
            map.put(node.getNodeName(), toObject(node));
            return map;
        }
        Object value = getElementAsObject(node);
        if (node.hasAttributes()) {
            Map<String, Object> wrapper = new LinkedHashMap<>(2);
            wrapper.put("_", value);
            wrapper.put("@", getAttributes(node));
            return wrapper;
        } else {
            return value;
        }
    }
    
    public static Element fromObject(String name, Object o) {
        return fromObject(newDocument(), name, o);
    }

    public static Element fromObject(Document doc, String name, Object o) {
        if (o instanceof Map) {
            Map<String, Object> map = (Map) o;
            Object value = map.get("_");
            if (value != null) {                
                Element element = fromObject(doc, name, value);
                Map<String, Object> attribs = (Map) map.get("@");
                addAttributes(element, attribs);           
                return element;
            } else {
                Element element = createElement(doc, name, null, null);
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String childName = entry.getKey();
                    Object childValue = entry.getValue();
                    Element childNode = fromObject(doc, childName, childValue);
                    element.appendChild(childNode);
                }
                return element;
            }
        } else if (o instanceof List) {
            Element element = createElement(doc, name, null, null);
            List list = (List) o;
            for (Object child : list) {
                Element childNode = fromObject(doc, name, child);
                element.appendChild(childNode);
            }
            return element;
        } else {
            String value = o == null ? null : o.toString();
            return createElement(doc, name, value, null);
        }
    }
    
    public static Document newDocument() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.newDocument();          
    }    
    
    public static void addAttributes(Element element, Map<String, Object> map) {
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object attrValue = entry.getValue();
                element.setAttribute(entry.getKey(), attrValue == null ? null : attrValue.toString());
            }
        }        
    }
    
    public static Element createElement(Node node, String name, String value, Map<String, Object> attributes) {
        Document doc = node.getNodeType() == Node.DOCUMENT_NODE ? (Document) node : node.getOwnerDocument();
		Element element = doc.createElement(name);
        element.setTextContent(value);
        addAttributes(element, attributes);
        return element;
    }
    
    public static Document toNewDocument(Node in) {
        Document doc = newDocument();
        Node node = doc.importNode(in, true);
        doc.appendChild(node);
        return doc;
    }

}
