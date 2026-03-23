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
package io.karatelabs.core;

import com.jayway.jsonpath.JsonPath;
import io.karatelabs.common.DataUtils;
import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.common.Xml;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.MatchExpression;
import io.karatelabs.http.DefaultHttpClientFactory;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpClientFactory;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.gherkin.GherkinParser;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.markup.Markup;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.match.Expect;
import io.karatelabs.match.Match;
import io.karatelabs.match.Result;
import io.karatelabs.match.Value;
import io.karatelabs.process.ProcessBuilder;
import io.karatelabs.process.ProcessHandle;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main implementation of the karate.* JavaScript API.
 * <p>
 * This class contains methods that require stateful access to:
 * - The JavaScript engine (for variable lookup and evaluation)
 * - Runtime context via {@link KarateJsContext} (scenario, feature, config)
 * - HTTP client and resource resolution
 * - Mock handler context
 * <p>
 * Stateless utility methods are delegated to {@link KarateJsUtils}.
 * Shared state and infrastructure are inherited from {@link KarateJsBase}.
 *
 * @see KarateJsUtils for pure utility functions
 * @see KarateJsBase for state management and initialization
 * @see KarateJsContext for runtime context interface
 */
public class KarateJs extends KarateJsBase implements PerfContext {

    private static final Faker FAKER = new Faker();

    private final JavaCallable read;

    public KarateJs(Resource root) {
        this(root, new DefaultHttpClientFactory());
    }

    public KarateJs(Resource root, HttpClientFactory factory) {
        this(root, factory.create());
    }

    public KarateJs(Resource root, HttpClient client) {
        super(root, client);
        engine.putRootBinding("karate", this);
        engine.putRootBinding("read", read = initRead());
        engine.putRootBinding("match", matchFluent());
    }

    /**
     * Track the previous HTTP request for karate.prevRequest.
     * Called by HttpRequestBuilder after successful HTTP calls.
     */
    public void setPrevRequest(io.karatelabs.http.HttpRequest request) {
        this.prevRequest = request;
    }

    /**
     * Capture a custom performance event (implements PerfContext).
     * <p>
     * When running under Gatling, this event will be reported to the
     * statistics engine. When not in performance mode, this is a no-op.
     * <p>
     * Usage in Java helpers:
     * <pre>
     * public static Object myRpc(Map args, PerfContext karate) {
     *     long start = System.currentTimeMillis();
     *     // ... custom logic ...
     *     long end = System.currentTimeMillis();
     *     karate.capturePerfEvent("myRpc", start, end);
     *     return result;
     * }
     * </pre>
     */
    @Override
    public void capturePerfEvent(String name, long startTime, long endTime) {
        if (context != null) {
            context.getRuntime().captureCustomPerfEvent(name, startTime, endTime);
        }
    }

    /**
     * Lazy-initialize and return the Markup template engine.
     * Used for doc() and render() template processing.
     */
    public Markup markup() {
        if (_markup == null) {
            if (resourceResolver != null) {
                _markup = Markup.init(engine, resourceResolver);
            } else {
                _markup = Markup.init(engine, root.getPrefixedPath());
            }
        }
        return _markup;
    }

    /**
     * Renders an HTML template and returns the result.
     * Also sends to onDoc consumer if set.
     * Called by the 'doc' keyword in StepExecutor.
     */
    public String doc(Map<String, Object> options) {
        String read = (String) options.get("read");
        if (read == null) {
            throw new RuntimeException("doc() requires 'read' key with template path");
        }
        String html = markup().processPath(read, null);
        if (onDoc != null) {
            onDoc.accept(html);
        }
        return html;
    }

    @SuppressWarnings("unchecked")
    private JavaInvokable doc() {
        return args -> {
            if (onDoc == null) {
                logger.warn("doc() called, but no destination set");
                return null;
            }
            if (args.length == 0) {
                throw new RuntimeException("doc() needs at least one argument");
            }
            String read;
            if (args[0] instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) args[0];
                read = (String) map.get("read");
            } else if (args[0] == null) {
                read = null;
            } else {
                read = args[0] + "";
            }
            if (read == null) {
                throw new RuntimeException("doc() read arg should not be null");
            }
            Map<String, Object> vars;
            if (args.length > 1) {
                vars = (Map<String, Object>) args[1];
            } else {
                vars = null;
            }
            String html = markup().processPath(read, vars);
            onDoc.accept(html);
            return null;
        };
    }

    // ========== Engine-Dependent Methods ==========
    // These methods require access to the JavaScript engine for evaluation.

    private JavaCallable initRead() {
        return (context, args) -> {
            if (args.length == 0) {
                throw new RuntimeException("read() needs at least one argument");
            }
            String rawPath = args[0] + "";

            // Parse tag selector for feature files
            // Supports: file.feature@tag or @tag (same-file)
            String path;
            String tagSelector = null;
            if (rawPath.startsWith("@")) {
                // Same-file tag - return a FeatureCall wrapper
                return new FeatureCall(null, rawPath);
            } else {
                int tagPos = rawPath.indexOf(".feature@");
                if (tagPos != -1) {
                    path = rawPath.substring(0, tagPos + 8);  // "file.feature"
                    tagSelector = "@" + rawPath.substring(tagPos + 9);  // "@tag"
                } else {
                    path = rawPath;
                }
            }

            // V1 compatibility: handle 'this:' prefix for relative paths
            Resource resource;
            if (path.startsWith("this:")) {
                path = path.substring(5);
                resource = getCurrentResource().resolve(path);
            } else {
                resource = root.resolve(path);
            }
            return switch (resource.getExtension()) {
                case "json" -> Json.of(resource.getText()).value();
                case "js" -> engine.eval(resource);
                case "feature" -> {
                    Feature feature = Feature.read(resource);
                    yield tagSelector != null ? new FeatureCall(feature, tagSelector) : feature;
                }
                case "xml" -> {
                    Document doc = Xml.toXmlDoc(resource.getText());
                    processXmlEmbeddedExpressions(doc);
                    yield doc;
                }
                case "csv" -> DataUtils.fromCsv(resource.getText());
                case "yml", "yaml" -> DataUtils.fromYaml(resource.getText());
                // Binary file types - return raw bytes (V1 compatibility)
                case "pdf", "png", "jpg", "jpeg", "gif", "ico", "mp4", "bin", "zip", "gz", "tar" -> FileUtils.toBytes(resource.getStream());
                default -> resource.getText();
            };
        };
    }

    /**
     * Fluent match API for global match() function.
     */
    private JavaInvokable matchFluent() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("match() needs at least one argument");
            }
            return Match.evaluate(args[0], null, (ctx, result) -> {
                if (onMatch != null) {
                    onMatch.accept(ctx, result);
                }
            });
        };
    }

    /**
     * Process embedded expressions in XML nodes.
     * Handles #(expr) and ##(optional) patterns in text content and attributes.
     */
    private void processXmlEmbeddedExpressions(org.w3c.dom.Node node) {
        if (node == null) return;
        if (node.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        if (node == null) return;

        // Process attributes
        org.w3c.dom.NamedNodeMap attribs = node.getAttributes();
        if (attribs != null) {
            for (int i = 0; i < attribs.getLength(); i++) {
                org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attribs.item(i);
                String value = attr.getValue();
                if (value != null && value.contains("#(")) {
                    attr.setValue(processEmbeddedString(value));
                }
            }
        }

        // Process child nodes
        java.util.List<org.w3c.dom.Node> elementsToRemove = new java.util.ArrayList<>();
        java.util.List<org.w3c.dom.Node[]> nodesToReplace = new java.util.ArrayList<>();
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (text != null && text.contains("#(")) {
                    String trimmed = text.trim();
                    if (trimmed.startsWith("##(") && trimmed.endsWith(")")) {
                        String expr = trimmed.substring(3, trimmed.length() - 1);
                        Object result = engine.eval(expr);
                        if (result == null) {
                            elementsToRemove.add(child.getParentNode());
                        } else if (result instanceof org.w3c.dom.Node) {
                            nodesToReplace.add(new org.w3c.dom.Node[]{child, (org.w3c.dom.Node) result});
                        } else {
                            child.setTextContent(result.toString());
                        }
                    } else if (trimmed.startsWith("#(") && trimmed.endsWith(")") && !trimmed.substring(2).contains("#(")) {
                        String expr = trimmed.substring(2, trimmed.length() - 1);
                        try {
                            Object result = engine.eval(expr);
                            if (result instanceof org.w3c.dom.Node) {
                                nodesToReplace.add(new org.w3c.dom.Node[]{child, (org.w3c.dom.Node) result});
                            } else {
                                child.setTextContent(KarateJsUtils.valueToString(result));
                            }
                        } catch (Exception e) {
                            // Keep original if evaluation fails
                        }
                    } else {
                        child.setTextContent(processEmbeddedString(text));
                    }
                }
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                processXmlEmbeddedExpressions(child);
            } else if (child.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                String text = child.getTextContent();
                if (text != null && text.contains("#(")) {
                    child.setTextContent(processEmbeddedString(text));
                }
            }
        }

        // Replace text nodes with imported XML nodes
        for (org.w3c.dom.Node[] pair : nodesToReplace) {
            org.w3c.dom.Node textNode = pair[0];
            org.w3c.dom.Node xmlNode = pair[1];
            org.w3c.dom.Node parent = textNode.getParentNode();
            if (parent != null) {
                Document ownerDoc = parent.getOwnerDocument();
                org.w3c.dom.Node toImport = xmlNode;
                if (toImport.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) {
                    toImport = ((Document) toImport).getDocumentElement();
                }
                org.w3c.dom.Node imported = ownerDoc.importNode(toImport, true);
                parent.replaceChild(imported, textNode);
            }
        }

        // Remove elements marked for removal
        for (org.w3c.dom.Node toRemove : elementsToRemove) {
            Node parent = toRemove.getParentNode();
            if (parent != null) {
                parent.removeChild(toRemove);
            }
        }
    }

    /**
     * Process a string with embedded expressions like "Hello #(name)!"
     */
    private String processEmbeddedString(String str) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            int hashPos = str.indexOf('#', i);
            if (hashPos == -1 || hashPos >= str.length() - 1) {
                result.append(str.substring(i));
                break;
            }
            result.append(str, i, hashPos);
            char next = str.charAt(hashPos + 1);
            if (next == '(') {
                int closePos = KarateJsUtils.findMatchingParen(str, hashPos + 1);
                if (closePos > 0) {
                    String expr = str.substring(hashPos + 2, closePos);
                    try {
                        Object value = engine.eval(expr);
                        result.append(KarateJsUtils.valueToString(value));
                    } catch (Exception e) {
                        result.append(str, hashPos, closePos + 1);
                    }
                    i = closePos + 1;
                } else {
                    result.append('#');
                    i = hashPos + 1;
                }
            } else if (next == '#' && hashPos + 2 < str.length() && str.charAt(hashPos + 2) == '(') {
                int closePos = KarateJsUtils.findMatchingParen(str, hashPos + 2);
                if (closePos > 0) {
                    String expr = str.substring(hashPos + 3, closePos);
                    try {
                        Object value = engine.eval(expr);
                        result.append(KarateJsUtils.valueToString(value));
                    } catch (Exception e) {
                        result.append(str, hashPos, closePos + 1);
                    }
                    i = closePos + 1;
                } else {
                    result.append("##");
                    i = hashPos + 2;
                }
            } else {
                result.append('#');
                i = hashPos + 1;
            }
        }
        return result.toString();
    }

    private JavaInvokable http() {
        return args -> {
            if (args.length > 0) {
                http.url(args[0] + "");
            }
            return http;
        };
    }

    private JavaInvokable readAsString() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("read() needs at least one argument");
            }
            Resource resource = root.resolve(args[0] + "");
            return resource.getText();
        };
    }

    /**
     * Read a file as raw bytes. Useful for binary content handling.
     * Usage: karate.readAsBytes('path/to/file')
     */
    private JavaInvokable readAsBytes() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("readAsBytes() needs at least one argument");
            }
            String path = args[0] + "";
            Resource resource = getCurrentResource().resolve(path);
            try (java.io.InputStream is = resource.getStream()) {
                return is.readAllBytes();
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read bytes from: " + path, e);
            }
        };
    }

    private JavaInvokable get() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("get() needs at least one argument");
            }
            String expr = args[0] + "";

            // Check if it's a jsonpath expression like $varname.path or $varname[*].path
            if (expr.startsWith("$") && expr.length() > 1) {
                String withoutDollar = expr.substring(1);
                // Find where the path starts (at . or [)
                int pathStart = -1;
                for (int i = 0; i < withoutDollar.length(); i++) {
                    char c = withoutDollar.charAt(i);
                    if (c == '.' || c == '[') {
                        pathStart = i;
                        break;
                    }
                }
                if (pathStart > 0) {
                    String varName = withoutDollar.substring(0, pathStart);
                    String jsonPath = "$" + withoutDollar.substring(pathStart);
                    Object target = engine.get(varName);
                    if (target != null) {
                        return JsonPath.read(target, jsonPath);
                    }
                    return null;
                } else if (pathStart == 0) {
                    // $. or $[ means use 'response'
                    Object target = engine.get("response");
                    if (target != null) {
                        return JsonPath.read(target, "$" + withoutDollar);
                    }
                    return null;
                }
                // Just $varname - return the variable
                return engine.get(withoutDollar);
            }

            // Simple variable lookup
            Object result = engine.get(expr);
            if (result == null && args.length > 1) {
                return args[1];
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private JavaInvokable set() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("set() needs at least two arguments: name and value");
            }
            String name = args[0] + "";
            if (args.length == 2) {
                // Simple set: karate.set('name', value)
                engine.put(name, args[1]);
            } else {
                // Path set: karate.set('name', 'path', value)
                String path = args[1] + "";
                Object value = args[2];
                Object target = engine.get(name);

                // Check if this is XPath (path starts with /) or target is XML
                if (path.startsWith("/") || target instanceof Node) {
                    // XPath set on XML
                    Document doc;
                    if (target instanceof Document) {
                        doc = (Document) target;
                    } else if (target instanceof Node) {
                        doc = ((Node) target).getOwnerDocument();
                    } else if (target == null) {
                        // Create new XML document
                        doc = Xml.newDocument();
                        engine.put(name, doc);
                    } else if (target instanceof String && Xml.isXml((String) target)) {
                        // Convert XML string to Document
                        doc = Xml.toXmlDoc((String) target);
                        engine.put(name, doc);
                    } else {
                        throw new RuntimeException("cannot set xpath on non-XML variable: " + name);
                    }
                    if (value instanceof Node) {
                        Xml.setByPath(doc, path, (Node) value);
                    } else {
                        Xml.setByPath(doc, path, value == null ? "" : value.toString());
                    }
                } else if (target == null) {
                    target = new java.util.LinkedHashMap<>();
                    engine.put(name, target);
                    // Direct path set for JSON
                    String navPath = path.startsWith("$.") ? path.substring(2) : path;
                    KarateJsUtils.setAtPath(target, navPath, value);
                } else {
                    // Handle special jsonpath cases
                    if (path.endsWith("[]")) {
                        // Append to array: $.foo[] means add to foo array
                        String arrayPath = path.substring(0, path.length() - 2);
                        if (arrayPath.equals("$")) {
                            // Root is array
                            if (target instanceof List) {
                                ((List<Object>) target).add(value);
                            }
                        } else {
                            // Navigate to array and append
                            String navPath = arrayPath.substring(2); // remove "$."
                            Object arr = KarateJsUtils.navigateToPath(target, navPath);
                            if (arr instanceof List) {
                                ((List<Object>) arr).add(value);
                            }
                        }
                    } else {
                        // Direct path set
                        String navPath = path.startsWith("$.") ? path.substring(2) : path;
                        KarateJsUtils.setAtPath(target, navPath, value);
                    }
                }
            }
            return null;
        };
    }

    /**
     * V1-compatible karate.match() function.
     * Usage: karate.match(actual, expected) or karate.match("foo == expected")
     * Returns { pass: boolean, message: String|null }
     */
    private JavaInvokable karateMatch() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("karate.match() needs at least one argument");
            }
            if (args.length >= 2) {
                // Two-argument form: karate.match(actual, expected)
                // Do an equals comparison and return { pass, message }
                Object actual = args[0];
                Object expected = args[1];
                Value value = Match.evaluate(actual, null, null);
                Result result = value._equals(expected);
                return result.toMap();
            } else {
                // One-argument string form: karate.match("foo == expected")
                // Use GherkinParser for proper lexer-based parsing
                String expression = args[0].toString();
                MatchExpression parsed = GherkinParser.parseMatchExpression(expression);

                Object actual = engine.get(parsed.getActualExpr());
                Object expected = engine.eval(parsed.getExpectedExpr());
                Value value = Match.evaluate(actual, null, null);
                Match.Type matchType = Match.Type.valueOf(parsed.getMatchTypeName());
                Result result = value.is(matchType, expected);
                return result.toMap();
            }
        };
    }

    private JavaInvokable call() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt == null) {
                throw new RuntimeException("karate.call() is not available in this context");
            }
            if (args.length == 0) {
                throw new RuntimeException("karate.call() requires at least one argument (feature path)");
            }
            // V1 compatible signatures:
            // call(path) - isolated scope
            // call(path, arg) - isolated scope with arg
            // call(sharedScope, path) - explicit scope
            // call(sharedScope, path, arg) - explicit scope with arg
            boolean sharedScope = false;
            String path;
            Object arg;
            if (args[0] instanceof Boolean) {
                sharedScope = (Boolean) args[0];
                if (args.length < 2) {
                    throw new RuntimeException("karate.call() with sharedScope requires a feature path");
                }
                path = args[1].toString();
                arg = args.length > 2 ? args[2] : null;
            } else {
                path = args[0].toString();
                arg = args.length > 1 ? args[1] : null;
            }
            Map<String, Object> result = rt.executeJsCall(path, arg);
            if (sharedScope && result != null) {
                // Merge result variables into current scope
                for (var entry : result.entrySet()) {
                    engine.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        };
    }

    private JavaInvokable eval() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("eval() needs one argument");
            }
            return engine.eval(args[0].toString());
        };
    }

    /**
     * karate.expect() - Chai-style BDD assertion API.
     * <p>
     * Usage:
     * <pre>
     * karate.expect(actual).to.equal(expected)
     * karate.expect(actual).to.be.a('string')
     * karate.expect(actual).to.have.property('name')
     * karate.expect(actual).to.not.equal(unexpected)
     * </pre>
     */
    private JavaCallable expect() {
        return (context, args) -> {
            if (args.length == 0) {
                throw new RuntimeException("expect() needs at least one argument");
            }
            return new Expect(args[0], onMatch);
        };
    }

    private JavaInvokable remove() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("remove() needs two arguments: variable name and path");
            }
            String varName = args[0].toString();
            String path = args[1].toString();
            Object var = engine.get(varName);
            if (var instanceof Node && path != null && path.startsWith("/")) {
                // XPath remove on XML
                Document doc = var instanceof Document ? (Document) var : ((Node) var).getOwnerDocument();
                Xml.removeByPath(doc, path);
            } else if (var instanceof Map && path != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) var;
                map.remove(path);
            }
            return null;
        };
    }

    private JavaInvokable setXml() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("setXml() needs at least two arguments: name and xml");
            }
            String name = args[0].toString();
            if (args.length == 2) {
                // Simple form: setXml('name', '<xml/>')
                String xml = args[1].toString();
                engine.put(name, Xml.toXmlDoc(xml));
            } else {
                // Path form: setXml('name', '/path', '<xml/>')
                String path = args[1].toString();
                String xml = args[2].toString();
                Object target = engine.get(name);
                if (target instanceof Node) {
                    Node doc = (Node) target;
                    if (doc.getNodeType() != Node.DOCUMENT_NODE) {
                        doc = doc.getOwnerDocument();
                    }
                    Xml.setByPath((org.w3c.dom.Document) doc, path, Xml.toXmlDoc(xml));
                }
            }
            return null;
        };
    }

    // ========== Process Execution ==========

    /**
     * karate.exec() - Synchronous process execution.
     * Returns stdout as string.
     * Usage:
     * karate.exec('ls -la')
     * karate.exec(['ls', '-la'])
     * karate.exec({ line: 'ls -la', workingDir: '/tmp' })
     */
    @SuppressWarnings("unchecked")
    private JavaInvokable exec() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("exec() needs at least one argument");
            }
            ProcessBuilder builder = ProcessBuilder.create();
            Object arg = args[0];
            if (arg instanceof String) {
                builder.line((String) arg);
            } else if (arg instanceof List) {
                builder.args((List<String>) arg);
            } else if (arg instanceof Map) {
                builder = ProcessBuilder.fromMap((Map<String, Object>) arg);
            } else {
                throw new RuntimeException("exec() argument must be string, array, or object");
            }
            ProcessHandle handle = ProcessHandle.start(builder.build());
            handle.waitSync();
            return handle.getStdOut();
        };
    }

    /**
     * karate.fork() - Asynchronous process execution.
     * Returns ProcessHandle for async control.
     * Usage:
     * var proc = karate.fork('ping google.com')
     * var proc = karate.fork({ args: ['node', 'server.js'], listener: fn })
     * var proc = karate.fork({ args: [...], start: false })  // deferred start
     * proc.onStdOut(fn).start()
     * proc.waitSync()
     * proc.stdOut
     * proc.exitCode
     * proc.close()
     */
    @SuppressWarnings("unchecked")
    private JavaInvokable fork() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("fork() needs at least one argument");
            }
            ProcessBuilder builder = ProcessBuilder.create();
            Consumer<String> listener = null;
            Consumer<String> errorListener = null;
            boolean autoStart = true;

            Object arg = args[0];
            if (arg instanceof String) {
                builder.line((String) arg);
            } else if (arg instanceof List) {
                builder.args((List<String>) arg);
            } else if (arg instanceof Map) {
                Map<String, Object> options = (Map<String, Object>) arg;
                builder = ProcessBuilder.fromMap(options);

                // Extract listener function (receives line string directly)
                Object listenerObj = options.get("listener");
                if (listenerObj instanceof JavaCallable jsListener) {
                    listener = line -> {
                        try {
                            jsListener.call(null, line);
                        } catch (Exception e) {
                            logger.warn("process listener error: {}", e.getMessage());
                        }
                    };
                }

                // Extract errorListener function (receives line string directly)
                Object errorListenerObj = options.get("errorListener");
                if (errorListenerObj instanceof JavaCallable jsErrorListener) {
                    errorListener = line -> {
                        try {
                            jsErrorListener.call(null, line);
                        } catch (Exception e) {
                            logger.warn("process errorListener error: {}", e.getMessage());
                        }
                    };
                }

                // Check start option (default true)
                Object startObj = options.get("start");
                if (startObj instanceof Boolean) {
                    autoStart = (Boolean) startObj;
                }
            } else {
                throw new RuntimeException("fork() argument must be string, array, or object");
            }

            if (listener != null) {
                builder.listener(listener);
            }
            if (errorListener != null) {
                builder.errorListener(errorListener);
            }

            ProcessHandle handle = ProcessHandle.create(builder.build());

            // Wire signal consumer for listen/listenResult integration
            ScenarioRuntime rt = getRuntime();
            if (rt != null) {
                handle.setSignalConsumer(rt::setListenResult);
            }

            if (autoStart) {
                handle.start();
            }
            return handle;
        };
    }

    /**
     * karate.start() - Start a mock server from a feature file.
     * Usage:
     * <pre>
     * var server = karate.start('api.feature');
     * var server = karate.start({ mock: 'api.feature', port: 8080 });
     * var server = karate.start({ mock: 'api.feature', port: 8443, ssl: true });
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private JavaInvokable start() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("start() needs at least one argument: feature path or config map");
            }
            Object arg = args[0];
            MockServer.Builder builder;

            if (arg instanceof String path) {
                // Simple path: karate.start('api.feature')
                builder = MockServer.feature(root.resolve(path));
            } else if (arg instanceof Map) {
                // Config map: karate.start({ mock: 'api.feature', port: 8080 })
                Map<String, Object> config = (Map<String, Object>) arg;
                String mockPath = (String) config.get("mock");
                if (mockPath == null) {
                    throw new RuntimeException("start() config requires 'mock' key with feature path");
                }
                builder = MockServer.feature(root.resolve(mockPath));

                if (config.containsKey("port")) {
                    builder.port(((Number) config.get("port")).intValue());
                }
                if (config.containsKey("ssl")) {
                    builder.ssl(Boolean.TRUE.equals(config.get("ssl")));
                }
                if (config.containsKey("cert")) {
                    builder.certPath((String) config.get("cert"));
                }
                if (config.containsKey("key")) {
                    builder.keyPath((String) config.get("key"));
                }
                if (config.containsKey("arg")) {
                    builder.arg((Map<String, Object>) config.get("arg"));
                }
                if (config.containsKey("pathPrefix")) {
                    builder.pathPrefix((String) config.get("pathPrefix"));
                }
            } else {
                throw new RuntimeException("start() argument must be a string path or config map");
            }

            return builder.start();
        };
    }

    /**
     * karate.proceed() - Forward the current request to a target URL (proxy mode).
     * Can only be used within a mock scenario.
     * Usage:
     * <pre>
     * // Forward to specific target
     * var response = karate.proceed('http://backend:8080');
     *
     * // Forward using Host header from request
     * var response = karate.proceed();
     * </pre>
     */
    private JavaInvokable proceed() {
        return args -> {
            if (mockHandler == null) {
                throw new RuntimeException("proceed() can only be called within a mock scenario");
            }
            HttpRequest currentRequest = mockHandler.getCurrentRequest();

            String targetUrl;
            if (args.length > 0 && args[0] != null) {
                targetUrl = args[0].toString();
            } else {
                // Use Host header from request
                String host = currentRequest.getHeader("Host");
                if (host == null) {
                    throw new RuntimeException("proceed() needs a target URL or Host header in request");
                }
                targetUrl = "http://" + host;
            }

            // Build request manually to avoid header conflicts
            HttpRequestBuilder builder = new HttpRequestBuilder(client);
            builder.url(targetUrl);
            builder.path(currentRequest.getPath());
            builder.method(currentRequest.getMethod());

            // Copy headers except those that will be auto-set
            if (currentRequest.getHeaders() != null) {
                currentRequest.getHeaders().forEach((name, values) -> {
                    String lowerName = name.toLowerCase();
                    // Skip headers that are auto-managed
                    if (!lowerName.equals("content-length") && !lowerName.equals("host")
                            && !lowerName.equals("transfer-encoding")) {
                        builder.header(name, values);
                    }
                });
            }

            // Set body (this will set Content-Length appropriately)
            Object body = currentRequest.getBodyConverted();
            if (body != null) {
                builder.body(body);
            }

            // Execute and return the response
            HttpResponse response = builder.invoke();
            return response;
        };
    }

    // ========== Pending Methods Implementation ==========

    /**
     * karate.request - Returns the current mock request (only in mock context).
     * Returns the body content of the current request.
     */
    private Object getRequest() {
        if (mockHandler == null) {
            logger.warn("karate.request is only available in mock context");
            return null;
        }
        io.karatelabs.http.HttpRequest request = mockHandler.getCurrentRequest();
        return request != null ? request.getBodyConverted() : null;
    }

    /**
     * karate.response - Returns the current response variable (only in mock context).
     * Used by mocks to get/set the response to return.
     */
    private Object getResponse() {
        if (mockHandler == null) {
            logger.warn("karate.response is only available in mock context");
            return null;
        }
        // In mock context, 'response' is a variable in the engine
        return engine.get("response");
    }

    /**
     * karate.readAsStream(path) - Read file as InputStream.
     * Useful for streaming large files without loading into memory.
     */
    private JavaInvokable readAsStream() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("readAsStream() needs at least one argument");
            }
            String path = args[0] + "";
            Resource resource = getCurrentResource().resolve(path);
            try {
                return resource.getStream();
            } catch (Exception e) {
                throw new RuntimeException("Failed to open stream for: " + path, e);
            }
        };
    }

    /**
     * karate.render(template) - Render HTML template (similar to doc).
     * Returns the rendered HTML string without sending to doc consumer.
     */
    @SuppressWarnings("unchecked")
    private JavaInvokable render() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("render() needs at least one argument");
            }
            String readPath;
            Map<String, Object> vars = null;
            if (args[0] instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) args[0];
                readPath = (String) map.get("read");
            } else if (args[0] == null) {
                throw new RuntimeException("render() read arg should not be null");
            } else {
                readPath = args[0] + "";
            }
            if (args.length > 1 && args[1] instanceof Map) {
                vars = (Map<String, Object>) args[1];
            }
            return markup().processPath(readPath, vars);
        };
    }

    /**
     * karate.toAbsolutePath(path) - Convert a relative path to absolute.
     * Resolves relative to the current feature file's directory.
     */
    private JavaInvokable toAbsolutePath() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("toAbsolutePath() needs a path argument");
            }
            String path = args[0] + "";
            Resource resource = getCurrentResource().resolve(path);
            if (resource.isFile() && resource.getPath() != null) {
                return resource.getPath().toAbsolutePath().toString();
            }
            // For classpath resources, return the prefixed path
            return resource.getPrefixedPath();
        };
    }

    /**
     * karate.write(value, path) - Write content to a file.
     * Path is relative to the output directory (e.g., target/karate-reports).
     */
    private JavaInvokable write() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("write() needs value and path arguments");
            }
            Object value = args[0];
            String path = args[1] + "";

            // Get output directory
            String outputDir = getOutputDir();

            // Create the full path
            File file = new File(outputDir, path);

            // Ensure parent directories exist
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Convert value to bytes
            byte[] bytes = KarateJsUtils.convertToBytes(value);

            // Write to file
            try {
                Files.write(file.toPath(), bytes);
                logger.debug("wrote {} bytes to: {}", bytes.length, file.getAbsolutePath());
                return file;
            } catch (Exception e) {
                throw new RuntimeException("Failed to write file: " + file.getAbsolutePath(), e);
            }
        };
    }

    @Override
    public Object jsGet(String key) {
        return switch (key) {
            // Stateless utility methods (KarateJsApi)
            case "append" -> KarateJsUtils.append();
            case "appendTo" -> KarateJsUtils.appendTo();
            case "distinct" -> KarateJsUtils.distinct();
            case "extract" -> KarateJsUtils.extract();
            case "extractAll" -> KarateJsUtils.extractAll();
            case "filter" -> KarateJsUtils.filter();
            case "filterKeys" -> KarateJsUtils.filterKeys();
            case "forEach" -> KarateJsUtils.forEach();
            case "fromJson" -> KarateJsUtils.fromJson();
            case "fromString" -> KarateJsUtils.fromString();
            case "jsonPath" -> KarateJsUtils.jsonPath();
            case "keysOf" -> KarateJsUtils.keysOf();
            case "lowerCase" -> KarateJsUtils.lowerCase();
            case "map" -> KarateJsUtils.map();
            case "mapWithKey" -> KarateJsUtils.mapWithKey();
            case "merge" -> KarateJsUtils.merge();
            case "os" -> KarateJsUtils.getOsInfo();
            case "pause" -> KarateJsUtils.pause();
            case "pretty" -> KarateJsUtils.pretty();
            case "prettyXml" -> KarateJsUtils.prettyXml();
            case "range" -> KarateJsUtils.range();
            case "repeat" -> KarateJsUtils.repeat();
            case "sizeOf" -> KarateJsUtils.sizeOf();
            case "sort" -> KarateJsUtils.sort();
            case "toBean" -> KarateJsUtils.toBean();
            case "toBytes" -> KarateJsUtils.toBytes();
            case "toCsv" -> KarateJsUtils.toCsv();
            case "toJson" -> KarateJsUtils.toJson();
            case "toString" -> KarateJsUtils.toStringValue();
            case "typeOf" -> KarateJsUtils.typeOf();
            case "urlDecode" -> KarateJsUtils.urlDecode();
            case "urlEncode" -> KarateJsUtils.urlEncode();
            case "uuid" -> KarateJsUtils.uuid();
            case "valuesOf" -> KarateJsUtils.valuesOf();
            // Stateful methods that need engine/providers
            case "abort" -> abort();
            case "call" -> call();
            case "callonce" -> callonce();
            case "callSingle" -> callSingle();
            case "config" -> getConfig();
            case "configure" -> configure();
            case "doc" -> doc();
            case "embed" -> embed();
            case "env" -> env;
            case "eval" -> eval();
            case "exec" -> exec();
            case "expect" -> expect();
            case "fail" -> KarateJsUtils.fail();
            case "faker" -> FAKER;
            case "feature" -> getFeatureData();
            case "fork" -> fork();
            case "get" -> get();
            case "http" -> http();
            case "info" -> getInfo();
            case "log" -> log();
            case "logger" -> getLogger();
            case "match" -> karateMatch();
            case "prevRequest" -> getPrevRequest();
            case "proceed" -> proceed();
            case "properties" -> getProperties();
            case "read" -> read;
            case "readAsBytes" -> readAsBytes();
            case "readAsStream" -> readAsStream();
            case "readAsString" -> readAsString();
            case "remove" -> remove();
            case "render" -> render();
            case "request" -> getRequest();
            case "response" -> getResponse();
            case "scenario" -> getScenarioData();
            case "scenarioOutline" -> getScenarioOutlineData();
            case "set" -> set();
            case "setup" -> setup();
            case "setupOnce" -> setupOnce();
            case "setXml" -> setXml();
            case "signal" -> signal();
            case "start" -> start();
            case "stop" -> KarateJsUtils.stop();
            case "tags" -> getTags();
            case "tagValues" -> getTagValues();
            case "toAbsolutePath" -> toAbsolutePath();
            case "toJava" -> KarateJsUtils.toJava();
            case "waitForHttp" -> KarateJsUtils.waitForHttp();
            case "waitForPort" -> KarateJsUtils.waitForPort();
            case "write" -> write();
            case "xmlPath" -> KarateJsUtils.xmlPath();
            default -> null;
        };
    }

}
