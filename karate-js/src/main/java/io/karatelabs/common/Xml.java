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

import org.w3c.dom.*;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

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
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

public class Xml {

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
            } else {
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
            }
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Document toXmlDoc(String xml) {
        return toXmlDoc(xml, false);
    }

    public static Document toXmlDoc(String xml, boolean namespaceAware) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        factory.setIgnoringElementContentWhitespace(false);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            DtdEntityResolver dtdEntityResolver = new DtdEntityResolver();
            builder.setEntityResolver(dtdEntityResolver);
            InputStream is = FileUtils.toInputStream(xml);
            Document doc = builder.parse(is);
            if (dtdEntityResolver.dtdPresent) { // DOCTYPE present
                // the XML was not parsed, but I think it hangs at the root as a text node
                // so conversion to string and back has the effect of discarding the DOCTYPE !
                return toXmlDoc(toString(doc, false), namespaceAware);
            } else {
                return doc;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class DtdEntityResolver implements EntityResolver {

        protected boolean dtdPresent;

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            dtdPresent = true;
            return new InputSource(new StringReader(""));
        }

    }

    public static Document fromMap(Map<String, Object> map) {
        Map.Entry<String, Object> first = map.entrySet().iterator().next();
        return fromObject(first.getKey(), first.getValue());
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

    public static Document fromObject(String name, Object o) {
        Document doc = newDocument();
        List<Element> list = fromObject(doc, name, o);
        Element root = list.getFirst();
        doc.appendChild(root);
        return doc;
    }

    @SuppressWarnings("unchecked")
    public static List<Element> fromObject(Document doc, String name, Object o) {
        if (o instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) o;
            Map<String, Object> attribs = (Map<String, Object>) map.get("@");
            Object value = map.get("_");
            if (value != null || attribs != null) {
                List<Element> elements = fromObject(doc, name, value);
                addAttributes(elements.getFirst(), attribs);
                return elements;
            } else {
                Element element = createElement(doc, name, null, null);
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String childName = entry.getKey();
                    Object childValue = entry.getValue();
                    List<Element> childNodes = fromObject(doc, childName, childValue);
                    for (Element e : childNodes) {
                        element.appendChild(e);
                    }
                }
                return Collections.singletonList(element);
            }
        } else if (o instanceof List) {
            List<Object> list = (List<Object>) o;
            List<Element> elements = new ArrayList<>(list.size());
            for (Object child : list) {
                List<Element> childNodes = fromObject(doc, name, child);
                elements.addAll(childNodes);
            }
            return elements;
        } else {
            String value = o == null ? null : o.toString();
            Element element = createElement(doc, name, value, null);
            return Collections.singletonList(element);
        }
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

    public static Object toObject(Node node) {
        return toObject(node, false);
    }

    public static Object toObject(Node node, boolean removeNamespace) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            Map<String, Object> map = new LinkedHashMap<>(1);
            node = node.getFirstChild();
            if (node == null) {
                return map;
            }
            while (node.getNodeType() != Node.ELEMENT_NODE) { // ignore comments etc
                node = node.getNextSibling();
            }
            String name = removeNamespace
                    ? node.getNodeName().replaceFirst("(^.*:)", "") : node.getNodeName();
            map.put(name, toObject(node, removeNamespace));
            return map;
        }
        Object value = getElementAsObject(node, removeNamespace);
        if (node.hasAttributes()) {
            Map<String, Object> attribs = getAttributes(node);
            if (removeNamespace) {
                attribs.keySet().removeIf(key -> "xmlns".equals(key) || key.startsWith("xmlns:"));
            }
            if (!attribs.isEmpty()) {
                Map<String, Object> wrapper = new LinkedHashMap<>(2);
                wrapper.put("_", value);
                wrapper.put("@", attribs);
                return wrapper;
            } else {
                //namespaces were the only attributes
                return value;
            }
        } else {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getElementAsObject(Node node, boolean removeNamespace) {
        int childElementCount = getChildElementCount(node);
        if (childElementCount == 0) {
            String textContent = node.getTextContent();
            return StringUtils.isBlank(textContent) ? null:
                    textContent;
        }
        Map<String, Object> map = new LinkedHashMap<>(childElementCount);
        NodeList nodes = node.getChildNodes();
        int childCount = nodes.getLength();
        for (int i = 0; i < childCount; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childName = removeNamespace
                    ? child.getNodeName().replaceFirst("(^.*:)", "") : child.getNodeName();
            Object childValue = toObject(child, removeNamespace);
            // auto detect repeating elements
            if (map.containsKey(childName)) {
                Object temp = map.get(childName);
                if (temp instanceof List) {
                    List<Object> list = (List<Object>) temp;
                    list.add(childValue);
                } else {
                    List<Object> list = new ArrayList<>(childCount);
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

    // ========== XPath utilities ==========

    private static XPathExpression compile(String path) {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        try {
            return xpath.compile(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static NodeList getNodeListByPath(Node node, String path) {
        XPathExpression expr = compile(path);
        try {
            return (NodeList) expr.evaluate(node, XPathConstants.NODESET);
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

    public static Node getNodeByPath(Node node, String path, boolean create) {
        String searchPath = create ? stripNameSpacePrefixes(path) : path;
        XPathExpression expr = compile(searchPath);
        Node result;
        try {
            result = (Node) expr.evaluate(node, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        if (result == null && create) {
            Document doc = node.getNodeType() == Node.DOCUMENT_NODE ? (Document) node : node.getOwnerDocument();
            return createNodeByPath(doc, path);
        } else {
            return result;
        }
    }

    public static String stripNameSpacePrefixes(String path) {
        if (path.indexOf(':') == -1) {
            return path;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : path.split("/")) {
            if (!sb.isEmpty() || path.startsWith("/")) {
                sb.append('/');
            }
            int pos = s.lastIndexOf(':');
            if (pos == -1) {
                sb.append(s);
            } else {
                sb.append(s.substring(pos + 1));
            }
        }
        return sb.toString();
    }

    public static Node createNodeByPath(Document doc, String path) {
        int pos = path.lastIndexOf('/');
        if (pos == 0) { // root
            Node root = doc.getDocumentElement();
            if (root == null) {
                root = createElement(doc, path.substring(1), null, null);
                doc.appendChild(root);
            }
            return root;
        }
        String left = path.substring(0, pos);
        Node parent = getNodeByPath(doc, left, true);
        String right = path.substring(pos + 1);
        if (right.startsWith("@")) { // attribute
            Element parentElement = (Element) parent;
            right = right.substring(1);
            parentElement.setAttribute(right, "");
            return parentElement.getAttributeNode(right);
        } else {
            int bracketPos = right.indexOf('[');
            if (bracketPos != -1) { // index, we assume it is 1 and still append
                right = right.substring(0, bracketPos);
            }
            Element element = createElement(parent, right, null, null);
            parent.appendChild(element);
            return element;
        }
    }

    public static void setByPath(Node doc, String path, String value) {
        Node node = getNodeByPath(doc, path, true);
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            node.setNodeValue(value);
        } else if (node.hasChildNodes() && node.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            node.getFirstChild().setTextContent(value);
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            node.setTextContent(value);
        }
    }

    public static void setByPath(Document doc, String path, Node in) {
        if (in.getNodeType() == Node.DOCUMENT_NODE) {
            in = in.getFirstChild();
        }
        Node node = getNodeByPath(doc, path, true);
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

    public static void removeByPath(Document doc, String path) {
        Node node = getNodeByPath(doc, path, false);
        if (node == null) {
            return;
        }
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            Element parent = ((Attr) node).getOwnerElement();
            parent.removeAttribute(node.getNodeName());
        } else {
            Node parent = node.getParentNode();
            if (parent != null) {
                parent.removeChild(node);
            }
        }
    }

    public static Document toNewDocument(Node in) {
        Document doc = newDocument();
        Node node = doc.importNode(in, true);
        doc.appendChild(node);
        return doc;
    }

    public static boolean isXml(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.charAt(0) == ' ') {
            s = s.trim();
            if (s.isEmpty()) {
                return false;
            }
        }
        return s.charAt(0) == '<';
    }

}
