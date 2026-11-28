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

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
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

    // the JDK's own implementations: a JAXP provider on the classpath (Saxon, Xalan, Xerces)
    // would otherwise be picked up and change how documents parse, serialize and evaluate

    static TransformerFactory transformerFactory() {
        return TransformerFactory.newDefaultInstance();
    }

    static DocumentBuilderFactory documentBuilderFactory() {
        return DocumentBuilderFactory.newDefaultInstance();
    }

    static XPathFactory xpathFactory() {
        return XPathFactory.newDefaultInstance();
    }

    public static String toString(Node node) {
        return toString(node, false);
    }

    public static String toString(Node node, boolean pretty) {
        // when serializing a subtree that's still attached to a
        // bigger document, any prefix declared on an ancestor (e.g. xsi on
        // soap:Envelope, used deep in the tree) is out of scope from the
        // serializer's view and it throws "Namespace for prefix X has not
        // been declared". Clone the subtree and copy the ancestor xmlns:*
        // decls onto the clone's root before handing it off.
        Node source = resolvePrefixes(node);
        DOMSource domSource = new DOMSource(source);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = transformerFactory();
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

    private static Node resolvePrefixes(Node node) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return node;
        }
        Element target = (Element) node;
        Node parent = target.getParentNode();
        if (parent == null || parent.getNodeType() == Node.DOCUMENT_NODE) {
            return node;
        }
        Map<String, String> inherited = collectAncestorNamespaces(target);
        inherited.keySet().removeIf(k -> target.getAttributeNode(k) != null);
        if (inherited.isEmpty()) {
            return node;
        }
        Document newDoc = newDocument();
        Element imported = (Element) newDoc.importNode(target, true);
        newDoc.appendChild(imported);
        for (Map.Entry<String, String> e : inherited.entrySet()) {
            declareNamespace(imported, e.getKey(), e.getValue());
        }
        return imported;
    }

    public static Document toXmlDoc(String xml) {
        return toXmlDoc(xml, false);
    }

    public static Document toXmlDoc(String xml, boolean namespaceAware) {
        DocumentBuilderFactory factory = documentBuilderFactory();
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
        DocumentBuilderFactory factory = documentBuilderFactory();
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

    /**
     * The {@link #toObject(Node)} shape keyed by expanded name: {@code {uri}local} for a name in a
     * namespace, the bare local name otherwise, so prefixes and the placement of xmlns declarations
     * (which are dropped) never matter. Attributes sit under {@code "@"} beside the value under
     * {@code "_"}, repeated elements become a list, and {@code xsi:nil} stays an ordinary attribute
     * ({@code {http://www.w3.org/2001/XMLSchema-instance}nil}). Meant for namespace-aware nodes.
     */
    public static Object toExpandedObject(Node node) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            Map<String, Object> map = new LinkedHashMap<>(1);
            node = node.getFirstChild();
            if (node == null) {
                return map;
            }
            while (node.getNodeType() != Node.ELEMENT_NODE) {
                node = node.getNextSibling();
            }
            map.put(expandedName(node), toExpandedObject(node));
            return map;
        }
        Object value;
        if (getChildElementCount(node) == 0) {
            String textContent = node.getTextContent();
            value = StringUtils.isBlank(textContent) ? null : textContent;
        } else {
            value = childrenAsObject(node, Xml::expandedName, Xml::toExpandedObject);
        }
        Map<String, Object> attribs = new LinkedHashMap<>();
        NamedNodeMap nodeAttribs = node.getAttributes();
        for (int i = 0; nodeAttribs != null && i < nodeAttribs.getLength(); i++) {
            Node attr = nodeAttribs.item(i);
            String name = attr.getNodeName();
            if (!"xmlns".equals(name) && !name.startsWith("xmlns:")) {
                attribs.put(expandedName(attr), attr.getNodeValue());
            }
        }
        if (attribs.isEmpty()) {
            return value;
        }
        Map<String, Object> wrapper = new LinkedHashMap<>(2);
        wrapper.put("_", value);
        wrapper.put("@", attribs);
        return wrapper;
    }

    private static String expandedName(Node node) {
        String local = node.getLocalName();
        if (local == null) {
            return node.getNodeName();
        }
        String uri = node.getNamespaceURI();
        return uri == null ? local : "{" + uri + "}" + local;
    }

    /**
     * The namespace URI of an element whether or not its document is namespace-aware: on a
     * prefix-blind DOM the prefix is resolved against the xmlns declarations in scope.
     */
    public static String getNamespaceUri(Element element) {
        if (element.getLocalName() != null) {
            return element.getNamespaceURI();
        }
        String name = element.getNodeName();
        int pos = name.indexOf(':');
        String declaration = pos == -1 ? "xmlns" : "xmlns:" + name.substring(0, pos);
        for (Node node = element; node != null && node.getNodeType() == Node.ELEMENT_NODE; node = node.getParentNode()) {
            Attr attr = ((Element) node).getAttributeNode(declaration);
            if (attr != null) {
                return attr.getValue().isEmpty() ? null : attr.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childrenAsObject(Node node, java.util.function.Function<Node, String> naming,
                                                        java.util.function.Function<Node, Object> converter) {
        NodeList nodes = node.getChildNodes();
        int childCount = nodes.getLength();
        Map<String, Object> map = new LinkedHashMap<>(childCount);
        for (int i = 0; i < childCount; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childName = naming.apply(child);
            Object childValue = converter.apply(child);
            if (map.containsKey(childName)) {
                Object temp = map.get(childName);
                if (temp instanceof List) {
                    ((List<Object>) temp).add(childValue);
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

    /**
     * True when the node's document was parsed (or built) namespace-aware, so element and
     * attribute names carry a namespace URI and XPath steps must use prefixes the document binds.
     */
    public static boolean isNamespaceAware(Node node) {
        Document doc = documentOf(node);
        Element root = doc == null ? null : doc.getDocumentElement();
        return root != null && root.getLocalName() != null;
    }

    // every prefix bound anywhere in the document, the first binding in document order winning
    private static Map<String, String> collectPrefixes(Document doc) {
        Map<String, String> prefixes = new HashMap<>();
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (element.getPrefix() != null && element.getNamespaceURI() != null) {
                prefixes.putIfAbsent(element.getPrefix(), element.getNamespaceURI());
            }
            NamedNodeMap attribs = element.getAttributes();
            for (int j = 0; j < attribs.getLength(); j++) {
                Node attr = attribs.item(j);
                String name = attr.getNodeName();
                if (name.startsWith("xmlns:")) {
                    prefixes.putIfAbsent(name.substring(6), attr.getNodeValue());
                } else if (attr.getPrefix() != null && attr.getNamespaceURI() != null) {
                    prefixes.putIfAbsent(attr.getPrefix(), attr.getNamespaceURI());
                }
            }
        }
        return prefixes;
    }

    private static Document documentOf(Node node) {
        return node.getNodeType() == Node.DOCUMENT_NODE ? (Document) node : node.getOwnerDocument();
    }

    private static String namespaceOf(Document doc, String qualifiedName, Map<String, String> namespaces) {
        int pos = qualifiedName.indexOf(':');
        if (pos == -1) {
            return null;
        }
        String prefix = qualifiedName.substring(0, pos);
        if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        }
        String uri = namespaces == null ? null : namespaces.get(prefix);
        if (uri == null) {
            uri = collectPrefixes(doc).get(prefix);
        }
        if (uri == null) {
            throw new RuntimeException("namespace prefix '" + prefix + "' is neither configured nor declared in the document: " + qualifiedName);
        }
        return uri;
    }

    private static void declareNamespace(Element element, String name, String uri) {
        if (isNamespaceAware(element)) {
            element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, name, uri);
        } else {
            element.setAttribute(name, uri);
        }
    }

    private static NamespaceContext namespaceContext(Map<String, String> prefixes) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
                    return XMLConstants.XML_NS_URI;
                }
                return prefixes.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                Iterator<String> it = getPrefixes(uri);
                return it.hasNext() ? it.next() : null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return prefixes.entrySet().stream()
                        .filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).iterator();
            }
        };
    }

    // on a namespace-aware document a step's prefix resolves through the given bindings
    // first, then through the prefixes the document itself declares
    private static XPathExpression compile(Node node, String path, Map<String, String> namespaces) {
        XPathFactory xPathfactory = xpathFactory();
        XPath xpath = xPathfactory.newXPath();
        if (isNamespaceAware(node)) {
            Map<String, String> prefixes = collectPrefixes(documentOf(node));
            if (namespaces != null) {
                prefixes.putAll(namespaces);
            }
            xpath.setNamespaceContext(namespaceContext(prefixes));
        }
        try {
            return xpath.compile(path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static NodeList getNodeListByPath(Node node, String path) {
        return getNodeListByPath(node, path, null);
    }

    public static NodeList getNodeListByPath(Node node, String path, Map<String, String> namespaces) {
        XPathExpression expr = compile(node, path, namespaces);
        try {
            return (NodeList) expr.evaluate(node, XPathConstants.NODESET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getTextValueByPath(Node node, String path) {
        return getTextValueByPath(node, path, null);
    }

    public static String getTextValueByPath(Node node, String path, Map<String, String> namespaces) {
        XPathExpression expr = compile(node, path, namespaces);
        try {
            return expr.evaluate(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Node getNodeByPath(Node node, String path, boolean create) {
        return getNodeByPath(node, path, create, null);
    }

    public static Node getNodeByPath(Node node, String path, boolean create, Map<String, String> namespaces) {
        String searchPath = create && !isNamespaceAware(node) ? stripNameSpacePrefixes(path) : path;
        XPathExpression expr = compile(node, searchPath, namespaces);
        Node result;
        try {
            result = (Node) expr.evaluate(node, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        if (result == null && create) {
            Document doc = node.getNodeType() == Node.DOCUMENT_NODE ? (Document) node : node.getOwnerDocument();
            return createNodeByPath(doc, path, namespaces);
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
        return createNodeByPath(doc, path, null);
    }

    public static Node createNodeByPath(Document doc, String path, Map<String, String> namespaces) {
        int pos = path.lastIndexOf('/');
        if (pos == 0) { // root
            Node root = doc.getDocumentElement();
            if (root == null) {
                root = createElement(doc, path.substring(1), null, null);
                doc.appendChild(root);
            } else if (isNamespaceAware(doc)) {
                String step = path.substring(1);
                int bracketPos = step.indexOf('[');
                if (bracketPos != -1) {
                    step = step.substring(0, bracketPos);
                }
                String uri = namespaceOf(doc, step, namespaces);
                String local = step.substring(step.indexOf(':') + 1);
                if (!local.equals(root.getLocalName()) || !Objects.equals(uri, root.getNamespaceURI())) {
                    throw new RuntimeException("xpath root step '" + step + "' does not match the document root '"
                            + expandedName(root) + "'");
                }
            }
            return root;
        }
        String left = path.substring(0, pos);
        Node parent = getNodeByPath(doc, left, true, namespaces);
        String right = path.substring(pos + 1);
        if (right.startsWith("@")) { // attribute
            Element parentElement = (Element) parent;
            right = right.substring(1);
            if (isNamespaceAware(doc)) {
                String uri = namespaceOf(doc, right, namespaces);
                if (uri == null) {
                    parentElement.setAttributeNS(null, right, "");
                    return parentElement.getAttributeNodeNS(null, right);
                }
                String local = right.substring(right.indexOf(':') + 1);
                String prefix = bindPrefix(parentElement, parentElement, right.substring(0, right.indexOf(':')), uri, namespaces);
                parentElement.setAttributeNS(uri, prefix + ":" + local, "");
                return parentElement.getAttributeNodeNS(uri, local);
            } else {
                parentElement.setAttribute(right, "");
            }
            return parentElement.getAttributeNode(right);
        } else {
            int bracketPos = right.indexOf('[');
            if (bracketPos != -1) { // index, we assume it is 1 and still append
                right = right.substring(0, bracketPos);
            }
            Element element = isNamespaceAware(doc)
                    ? createElementNS(parent, right, namespaces) : createElement(parent, right, null, null);
            parent.appendChild(element);
            return element;
        }
    }

    private static Element createElementNS(Node parent, String name, Map<String, String> namespaces) {
        Document doc = documentOf(parent);
        String uri = namespaceOf(doc, name, namespaces);
        if (uri == null) {
            return doc.createElementNS(null, name);
        }
        String requested = name.substring(0, name.indexOf(':'));
        String local = name.substring(requested.length() + 1);
        Element element = doc.createElementNS(uri, requested + ":" + local);
        String prefix = bindPrefix(parent, element, requested, uri, namespaces);
        element.setPrefix(prefix);
        return element;
    }

    /**
     * The prefix a new name in {@code uri} takes under {@code scope}: one already bound there to
     * {@code uri}, else {@code requested} declared on {@code target} when that rebinds nothing in
     * scope, else a generated one used nowhere in the document nor in {@code namespaces}, since
     * XPath resolves a document prefix by its first declaration anywhere. A prefix is never
     * rebound, so names already using it keep their namespace.
     */
    private static String bindPrefix(Node scope, Element target, String requested, String uri, Map<String, String> namespaces) {
        if (XMLConstants.XML_NS_URI.equals(uri)) {
            return XMLConstants.XML_NS_PREFIX;
        }
        String inScope = scope.lookupPrefix(uri);
        if (inScope != null && uri.equals(scope.lookupNamespaceURI(inScope))) {
            return inScope;
        }
        String prefix = requested;
        if (isPrefixTaken(scope, prefix)) {
            Map<String, String> used = collectPrefixes(documentOf(scope));
            int i = 0;
            do {
                prefix = "ns" + i++;
            } while (used.containsKey(prefix) || (namespaces != null && namespaces.containsKey(prefix)) || isPrefixTaken(scope, prefix));
        }
        declareNamespace(target, "xmlns:" + prefix, uri);
        return prefix;
    }

    private static boolean isPrefixTaken(Node scope, String prefix) {
        if (scope.lookupNamespaceURI(prefix) != null) {
            return true;
        }
        NamedNodeMap attribs = scope.getAttributes();
        for (int i = 0; attribs != null && i < attribs.getLength(); i++) {
            if (prefix.equals(attribs.item(i).getPrefix())) {
                return true;
            }
        }
        return false;
    }

    public static void setByPath(Node doc, String path, String value) {
        setByPath(doc, path, value, null);
    }

    public static void setByPath(Node doc, String path, String value, Map<String, String> namespaces) {
        Node node = getNodeByPath(doc, path, true, namespaces);
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            node.setNodeValue(value);
        } else if (node.hasChildNodes() && node.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            node.getFirstChild().setTextContent(value);
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            node.setTextContent(value);
        }
    }

    public static void setByPath(Document doc, String path, Node in) {
        setByPath(doc, path, in, null);
    }

    public static void setByPath(Document doc, String path, Node in, Map<String, String> namespaces) {
        if (in.getNodeType() == Node.DOCUMENT_NODE) {
            in = in.getFirstChild();
        }
        Node node = getNodeByPath(doc, path, true, namespaces);
        if (node == null) {
            throw new RuntimeException("no results for xpath: " + path);
        }
        if (isNamespaceAware(doc) && !isNamespaceAware(in) && in.getNodeType() == Node.ELEMENT_NODE) {
            try {
                in = toXmlDoc(toString(in), true).getDocumentElement();
            } catch (RuntimeException undeclaredPrefix) {
                // imported as it is, by prefixed name
            }
        }
        Node newNode = doc.importNode(in, true);
        if (node.hasChildNodes() && node.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            node.replaceChild(newNode, node.getFirstChild());
        } else {
            node.appendChild(newNode);
        }
    }

    public static void removeByPath(Document doc, String path) {
        removeByPath(doc, path, null);
    }

    public static void removeByPath(Document doc, String path, Map<String, String> namespaces) {
        Node node = getNodeByPath(doc, path, false, namespaces);
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
        // inherit in-scope xmlns:* declarations from the source node's
        // ancestors so a subtree lifted out of a bigger document stays
        // self-contained — otherwise prefixed names referencing an ancestor
        // namespace (e.g. xsi:nil) fail to serialize.
        if (in.getNodeType() == Node.ELEMENT_NODE && node.getNodeType() == Node.ELEMENT_NODE) {
            Element target = (Element) node;
            Map<String, String> inherited = collectAncestorNamespaces(in);
            for (Map.Entry<String, String> e : inherited.entrySet()) {
                if (target.getAttributeNode(e.getKey()) == null) {
                    declareNamespace(target, e.getKey(), e.getValue());
                }
            }
        }
        doc.appendChild(node);
        return doc;
    }

    private static Map<String, String> collectAncestorNamespaces(Node node) {
        Map<String, String> inherited = new LinkedHashMap<>();
        Node parent = node.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            NamedNodeMap attribs = parent.getAttributes();
            if (attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    Node attr = attribs.item(i);
                    String name = attr.getNodeName();
                    if (("xmlns".equals(name) || name.startsWith("xmlns:"))
                            && !inherited.containsKey(name)) {
                        inherited.put(name, attr.getNodeValue());
                    }
                }
            }
            parent = parent.getParentNode();
        }
        return inherited;
    }

}
