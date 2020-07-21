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

import com.intuit.karate.core.MatchType;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.exception.KarateException;
import static com.intuit.karate.ScriptValue.Type.*;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.validator.ArrayValidator;
import com.intuit.karate.validator.BooleanValidator;
import com.intuit.karate.validator.IgnoreValidator;
import com.intuit.karate.validator.NotNullValidator;
import com.intuit.karate.validator.NullValidator;
import com.intuit.karate.validator.NumberValidator;
import com.intuit.karate.validator.ObjectValidator;
import com.intuit.karate.validator.RegexValidator;
import com.intuit.karate.validator.StringValidator;
import com.intuit.karate.validator.UuidValidator;
import com.intuit.karate.validator.ValidationResult;
import com.intuit.karate.validator.Validator;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author pthomas3
 */
public class Script {

    private Script() {
        // only static methods
    }

    public static final String VAR_SELF = "_";
    public static final String VAR_ROOT = "$";
    public static final String VAR_PARENT = "_$";
    public static final String VAR_LOOP = "__loop";
    public static final String VAR_ARG = "__arg";
    public static final String VAR_HEADER = "header";

    public static final Map<String, Validator> VALIDATORS;

    static {
        VALIDATORS = new HashMap(10);
        VALIDATORS.put("ignore", IgnoreValidator.INSTANCE);
        VALIDATORS.put("null", NullValidator.INSTANCE);
        VALIDATORS.put("notnull", NotNullValidator.INSTANCE);
        VALIDATORS.put("present", IgnoreValidator.INSTANCE); // re-use ignore, json key logic is in Script.java
        VALIDATORS.put("uuid", UuidValidator.INSTANCE);
        VALIDATORS.put("string", StringValidator.INSTANCE);
        VALIDATORS.put("number", NumberValidator.INSTANCE);
        VALIDATORS.put("boolean", BooleanValidator.INSTANCE);
        VALIDATORS.put("array", ArrayValidator.INSTANCE);
        VALIDATORS.put("object", ObjectValidator.INSTANCE);
    }

    public static final boolean isCallSyntax(String text) {
        return text.startsWith("call ");
    }

    public static final boolean isCallOnceSyntax(String text) {
        return text.startsWith("callonce ");
    }

    public static final boolean isGetSyntax(String text) {
        return text.startsWith("get ") || text.startsWith("get[");
    }

    public static final boolean isJson(String text) {
        return text.startsWith("{") || text.startsWith("[");
    }

    public static final boolean isJsonPath(String text) {
        return text.indexOf('*') != -1 || text.contains("..") || text.contains("[?");
    }

    public static final boolean isXml(String text) {
        return text.startsWith("<");
    }

    public static final boolean isXmlPath(String text) {
        return text.startsWith("/");
    }

    public static final boolean isXmlPathFunction(String text) {
        return text.matches("^[a-z-]+\\(.+");
    }

    public static final boolean isEmbeddedExpression(String text) {
        return (text.startsWith("#(") || text.startsWith("##(")) && text.endsWith(")");
    }

    public static final boolean isWithinParentheses(String text) {
        return text.startsWith("(") && text.endsWith(")");
    }

    public static final boolean isContainsMacro(String text) {
        return text.startsWith("^");
    }

    public static final boolean isContainsOnlyMacro(String text) {
        return text.startsWith("^^");
    }

    public static final boolean isContainsAnyMacro(String text) {
        return text.startsWith("^*");
    }

    public static final boolean isNotContainsMacro(String text) {
        return text.startsWith("!^");
    }

    public static final boolean isDollarPrefixedJsonPath(String text) {
        return text.startsWith("$.") || text.startsWith("$[") || text.equals("$");
    }

    public static final boolean isDollarPrefixedParen(String text) {
        return text.startsWith("$(");
    }

    public static final boolean isDollarPrefixed(String text) {
        return text.startsWith("$");
    }

    public static final boolean isVariable(String text) {
        return VARIABLE_PATTERN.matcher(text).matches();
    }

    public static final boolean isVariableAndSpaceAndPath(String text) {
        return text.matches("^" + VARIABLE_PATTERN_STRING + "\\s+.+");
    }

    private static final Pattern VAR_AND_PATH_PATTERN = Pattern.compile("\\w+");

    public static StringUtils.Pair parseVariableAndPath(String text) {
        Matcher matcher = VAR_AND_PATH_PATTERN.matcher(text);
        matcher.find();
        String name = text.substring(0, matcher.end());
        String path;
        if (matcher.end() == text.length()) {
            path = "";
        } else {
            path = text.substring(matcher.end());
        }
        if (isXmlPath(path) || isXmlPathFunction(path)) {
            // xml, don't prefix for json
        } else {
            path = "$" + path;
        }
        return StringUtils.pair(name, path);
    }

    public static ScriptValue evalKarateExpressionForMatch(String text, ScenarioContext context) {
        return evalKarateExpression(text, context);
    }

    private static ScriptValue result(ScriptValue called, CallResult result, boolean reuseParentConfig, ScenarioContext context) {
        if (reuseParentConfig) { // if shared scope
            context.configure(new Config(result.config)); // re-apply config from time of snapshot
            if (result.vars != null) {
                context.vars.clear();
                context.vars.putAll(result.vars.copy(false)); // clone for safety 
            }
        }
        return result.value.copy(false); // clone result for safety       
    }

    private static ScriptValue callWithCache(String callKey, ScriptValue called, String arg, ScenarioContext context, boolean reuseParentConfig) {
        // IMPORTANT: the call result is always shallow-cloned before returning
        // so that call result (if java Map) is not mutated by other scenarios
        final FeatureContext featureContext = context.featureContext;
        CallResult result = featureContext.callCache.get(callKey);
        if (result != null) {
            context.logger.trace("callonce cache hit for: {}", callKey);
            return result(called, result, reuseParentConfig, context);
        }
        long startTime = System.currentTimeMillis();
        context.logger.trace("callonce waiting for lock: {}", callKey);
        synchronized (featureContext) {
            result = featureContext.callCache.get(callKey); // retry
            if (result != null) {
                long endTime = System.currentTimeMillis() - startTime;
                context.logger.warn("this thread waited {} milliseconds for callonce lock: {}", endTime, callKey);
                return result(called, result, reuseParentConfig, context);
            }
            // this thread is the 'winner'
            context.logger.info(">> lock acquired, begin callonce: {}", callKey);
            ScriptValue resultValue = call(called, arg, context, reuseParentConfig);
            // we clone result (and config) here, to snapshot state at the point the callonce was invoked
            // this prevents the state from being clobbered by the subsequent steps of this
            // first scenario that is about to use the result
            ScriptValueMap clonedVars = called.isFeature() && reuseParentConfig ? context.vars.copy(false) : null;
            result = new CallResult(resultValue.copy(false), new Config(context.getConfig()), clonedVars);
            featureContext.callCache.put(callKey, result);
            context.logger.info("<< lock released, cached callonce: {}", callKey);
            return resultValue; // another routine will apply globally if needed
        }
    }

    public static ScriptValue getIfVariableReference(String text, ScenarioContext context) {
        if (isVariable(text)) {
            // don't re-evaluate if this is clearly a direct reference to a variable
            // this avoids un-necessary conversion of xml into a map in some cases 
            // e.g. 'Given request foo' - where foo is a ScriptValue of type XML
            ScriptValue value = context.vars.get(text);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static ScriptValue evalKarateExpression(String text, ScenarioContext context) {
        text = StringUtils.trimToNull(text);
        if (text == null) {
            return ScriptValue.NULL;
        }
        ScriptValue varValue = getIfVariableReference(text, context);
        if (varValue != null) {
            return varValue;
        }
        boolean callOnce = isCallOnceSyntax(text);
        if (callOnce || isCallSyntax(text)) { // special case in form "callBegin foo arg"
            if (callOnce) {
                text = text.substring(9);
            } else {
                text = text.substring(5);
            }
            StringUtils.Pair pair = parseCallArgs(text);
            ScriptValue called = evalKarateExpression(pair.left, context);
            if (callOnce) {
                return callWithCache(pair.left, called, pair.right, context, false);
            } else {
                return call(called, pair.right, context, false);
            }
        } else if (isDollarPrefixedJsonPath(text)) {
            return evalJsonPathOnVarByName(ScriptValueMap.VAR_RESPONSE, text, context);
        } else if (isGetSyntax(text) || isDollarPrefixed(text)) { // special case in form
            // get json[*].path
            // $json[*].path
            // get /xml/path
            // get xpath-function(expression)
            int index = -1;
            if (text.startsWith("$")) {
                text = text.substring(1);
            } else if (text.startsWith("get[")) {
                int pos = text.indexOf(']');
                index = Integer.valueOf(text.substring(4, pos));
                text = text.substring(pos + 2);
            } else {
                text = text.substring(4);
            }
            String left;
            String right;
            if (isDollarPrefixedJsonPath(text)) { // edge case get[0] $..foo
                left = ScriptValueMap.VAR_RESPONSE;
                right = text;
            } else if (isVariableAndSpaceAndPath(text)) {
                int pos = text.indexOf(' ');
                right = text.substring(pos + 1);
                left = text.substring(0, pos);
            } else {
                StringUtils.Pair pair = parseVariableAndPath(text);
                left = pair.left;
                right = pair.right;
            }
            ScriptValue sv;
            if (isXmlPath(right) || isXmlPathFunction(right)) {
                sv = evalXmlPathOnVarByName(left, right, context);
            } else {
                sv = evalJsonPathOnVarByName(left, right, context);
            }
            if (index != -1 && sv.isListLike()) {
                List list = sv.getAsList();
                if (!list.isEmpty()) {
                    return new ScriptValue(list.get(index));
                }
            }
            return sv;
        } else if (isJson(text)) {
            DocumentContext doc = JsonUtils.toJsonDoc(text);
            evalJsonEmbeddedExpressions(doc, context);
            return new ScriptValue(doc);
        } else if (isXml(text)) {
            Document doc = XmlUtils.toXmlDoc(text);
            evalXmlEmbeddedExpressions(doc, context);
            return new ScriptValue(doc);
        } else if (isXmlPath(text)) {
            return evalXmlPathOnVarByName(ScriptValueMap.VAR_RESPONSE, text, context);
        } else {
            // js expressions e.g. foo, foo(bar), foo.bar, foo + bar, foo + '', 5, true
            // including function declarations e.g. function() { }
            return evalJsExpression(text, context);
        }
    }

    private static ScriptValue getValuebyName(String name, ScenarioContext context) {
        ScriptValue value = context.vars.get(name);
        if (value == null) {
            throw new RuntimeException("no variable found with name: " + name);
        }
        return value;
    }

    // this is called only from the routine that evaluates karate expressions
    public static ScriptValue evalXmlPathOnVarByName(String name, String path, ScenarioContext context) {
        ScriptValue value = getValuebyName(name, context);
        Node node;
        switch (value.getType()) {
            case XML:
                node = value.getValue(Node.class);
                break;
            default:
                node = XmlUtils.fromMap(value.getAsMap());
        }
        ScriptValue sv = evalXmlPathOnXmlNode(node, path);
        if (sv == null) {
            throw new KarateException("xpath does not exist: " + path + " on " + name);
        }
        if (path.startsWith("count")) { // special case
            sv = new ScriptValue(sv.getAsInt());
        }
        return sv;
    }

    // hack: if this returns null - it means the node does not exist
    // this is relevant for the match routine to process #present and #notpresent macros
    public static ScriptValue evalXmlPathOnXmlNode(Node doc, String path) {
        NodeList nodeList;
        try {
            nodeList = XmlUtils.getNodeListByPath(doc, path);
        } catch (Exception e) {
            // hack, this happens for xpath functions that don't return nodes (e.g. count)
            String strValue = XmlUtils.getTextValueByPath(doc, path);
            ScriptValue sv = new ScriptValue(strValue);
            if (path.startsWith("count")) { // special case
                return new ScriptValue(sv.getAsInt());
            } else {
                return sv;
            }
        }
        int count = nodeList.getLength();
        if (count == 0) { // xpath / node does not exist !
            return null;
        }
        if (count == 1) {
            return nodeToValue(nodeList.item(0));
        }
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            ScriptValue sv = nodeToValue(nodeList.item(i));
            list.add(sv.getValue());
        }
        return new ScriptValue(list);
    }

    private static ScriptValue nodeToValue(Node node) {
        int childElementCount = XmlUtils.getChildElementCount(node);
        if (childElementCount == 0) {
            // hack assuming this is the most common "intent"
            return new ScriptValue(node.getTextContent());
        }
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return new ScriptValue(node);
        } else { // make sure we create a fresh doc else future xpath would run against original root
            return new ScriptValue(XmlUtils.toNewDocument(node));
        }
    }

    public static ScriptValue evalJsonPathOnVarByName(String name, String exp, ScenarioContext context) {
        ScriptValue value = getValuebyName(name, context);
        if (value.isJsonLike()) {
            DocumentContext jsonDoc = value.getAsJsonDocument();
            return new ScriptValue(jsonDoc.read(exp));
        } else if (value.isXml()) {
            Document xml = value.getValue(Document.class);
            DocumentContext xmlDoc = XmlUtils.toJsonDoc(xml);
            return new ScriptValue(xmlDoc.read(exp));
        } else {
            String str = value.getAsString();
            DocumentContext strDoc = JsonPath.parse(str);
            return new ScriptValue(strDoc.read(exp));
        }
    }

    public static ScriptValue evalJsExpression(String exp, ScenarioContext context) {
        return ScriptBindings.evalInNashorn(exp, context, null);
    }

    public static ScriptValue evalJsExpression(String exp, ScenarioContext context, ScriptValue selfValue, Object root, Object parent) {
        return ScriptBindings.evalInNashorn(exp, context, new ScriptEvalContext(selfValue, root, parent));
    }

    private static final String VARIABLE_PATTERN_STRING = "[a-zA-Z][\\w]*";

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE_PATTERN_STRING);

    public static boolean isValidVariableName(String name) {
        return VARIABLE_PATTERN.matcher(name).matches();
    }

    public static void evalJsonEmbeddedExpressions(DocumentContext doc, ScenarioContext context) {
        Object o = doc.read("$");
        evalJsonEmbeddedExpressions("$", o, context, doc);
    }

    private static void evalJsonEmbeddedExpressions(String path, Object o, ScenarioContext context, DocumentContext root) {
        if (o == null) {
            return;
        }
        if (o instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) o;
            // collect keys first, since they could be removed by the 'remove if null' ##(macro)
            // else we get a java.util.ConcurrentModificationException
            Collection<String> keys = new ArrayList(map.keySet());
            for (String key : keys) {
                String childPath = JsonUtils.buildPath(path, key);
                evalJsonEmbeddedExpressions(childPath, map.get(key), context, root);
            }
        } else if (o instanceof List) {
            List list = (List) o;
            int size = list.size();
            for (int i = 0; i < size; i++) {
                Object child = list.get(i);
                String childPath = path + "[" + i + "]";
                evalJsonEmbeddedExpressions(childPath, child, context, root);
            }
        } else if (o instanceof String) {
            String value = (String) o;
            value = StringUtils.trimToEmpty(value);
            if (isEmbeddedExpression(value)) {
                boolean optional = isOptionalMacro(value);
                try {
                    ScriptValue sv = evalJsExpression(value.substring(optional ? 2 : 1), context);
                    if (optional) {
                        if (sv.isNull()) {
                            root.delete(path);
                        } else if (!sv.isJsonLike()) {
                            // only substitute primitives ! 
                            // preserve optional JSON chunk schema-like references as-is, they are needed for future match attempts
                            root.set(path, sv.isStream() ? sv.getAsString() : sv.getValue());
                        }
                    } else {
                        root.set(path, sv.isStream() ? sv.getAsString() : sv.getValue());
                    }
                } catch (Exception e) {
                    context.logger.trace("embedded json eval failed, path: {}, reason: {}", path, e.getMessage());
                }
            }
        }
    }

    public static void evalXmlEmbeddedExpressions(Node node, ScenarioContext context) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        NamedNodeMap attribs = node.getAttributes();
        int attribCount = attribs == null ? 0 : attribs.getLength();
        Set<Attr> attributesToRemove = new HashSet(attribCount);
        for (int i = 0; i < attribCount; i++) {
            Attr attrib = (Attr) attribs.item(i);
            String value = attrib.getValue();
            value = StringUtils.trimToEmpty(value);
            if (isEmbeddedExpression(value)) {
                boolean optional = isOptionalMacro(value);
                try {
                    ScriptValue sv = evalJsExpression(value.substring(optional ? 2 : 1), context);
                    if (optional && sv.isNull()) {
                        attributesToRemove.add(attrib);
                    } else {
                        attrib.setValue(sv.getAsString());
                    }
                } catch (Exception e) {
                    context.logger.trace("embedded xml-attribute eval failed, path: {}, reason: {}", attrib.getName(), e.getMessage());
                }
            }
        }
        for (Attr toRemove : attributesToRemove) {
            attribs.removeNamedItem(toRemove.getName());
        }
        NodeList nodeList = node.getChildNodes();
        int childCount = nodeList.getLength();
        List<Node> nodes = new ArrayList(childCount);
        for (int i = 0; i < childCount; i++) {
            nodes.add(nodeList.item(i));
        }
        Set<Node> elementsToRemove = new HashSet(childCount);
        for (Node child : nodes) {
            String value = child.getNodeValue();
            if (value != null) {
                value = StringUtils.trimToEmpty(value);
                if (isEmbeddedExpression(value)) {
                    boolean optional = isOptionalMacro(value);
                    try {
                        ScriptValue sv = evalJsExpression(value.substring(optional ? 2 : 1), context);
                        if (optional && sv.isNull()) {
                            elementsToRemove.add(child);
                        } else {
                            if (sv.isMapLike()) {
                                Node evalNode;
                                if (sv.getType() == XML) {
                                    evalNode = sv.getValue(Node.class);
                                } else {
                                    evalNode = XmlUtils.fromMap(sv.getAsMap());
                                }
                                if (evalNode.getNodeType() == Node.DOCUMENT_NODE) {
                                    evalNode = evalNode.getFirstChild();
                                }
                                if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                                    child.setNodeValue(XmlUtils.toString(evalNode));
                                } else {
                                    evalNode = node.getOwnerDocument().importNode(evalNode, true);
                                    child.getParentNode().replaceChild(evalNode, child);
                                }
                            } else {
                                child.setNodeValue(sv.getAsString());
                            }
                        }
                    } catch (Exception e) {
                        context.logger.trace("embedded xml-text eval failed, path: {}, reason: {}", child.getNodeName(), e.getMessage());
                    }
                }
            } else if (child.hasChildNodes() || child.hasAttributes()) {
                evalXmlEmbeddedExpressions(child, context);
            }
        }
        for (Node toRemove : elementsToRemove) { // because of how the above routine works, these are always of type TEXT_NODE
            Node parent = toRemove.getParentNode(); // element containing the text-node
            Node grandParent = parent.getParentNode(); // parent element
            grandParent.removeChild(parent);
        }
    }

    public static ScriptValue copy(String name, String exp, ScenarioContext context, boolean validateName) {
        return assign(AssignType.COPY, name, exp, context, validateName);
    }

    public static ScriptValue assign(String name, String exp, ScenarioContext context) {
        return assign(AssignType.AUTO, name, exp, context, true);
    }

    private static void validateVariableName(String name) {
        if (!isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        if (ScriptBindings.KARATE.equals(name)) {
            throw new RuntimeException("'karate' is a reserved name");
        }
        if (ScriptValueMap.VAR_REQUEST.equals(name) || "url".equals(name)) {
            throw new RuntimeException("'" + name + "' is a reserved name, also use the form '* " + name + " <expression>' instead");
        }
    }

    public static ScriptValue assign(AssignType assignType, String name, String exp, ScenarioContext context, boolean validateName) {
        name = StringUtils.trimToEmpty(name);
        if (validateName) {
            validateVariableName(name);
            if (context.bindings.adds.containsKey(name)) {
                context.logger.warn("over-writing built-in variable named: {} - with new value: {}", name, exp);
            }
        }
        ScriptValue sv;
        switch (assignType) {
            case TEXT:
                sv = new ScriptValue(exp);
                break;
            case YAML:
                ScriptValue yamlString = evalKarateExpression(exp, context);
                DocumentContext yamlDoc = JsonUtils.fromYaml(yamlString.getAsString());
                evalJsonEmbeddedExpressions(yamlDoc, context);
                sv = new ScriptValue(yamlDoc);
                break;
            case CSV:
                ScriptValue csvString = evalKarateExpression(exp, context);
                DocumentContext csvDoc = JsonUtils.fromCsv(csvString.getAsString());
                sv = new ScriptValue(csvDoc);
                break;
            case STRING:
                ScriptValue str = evalKarateExpression(exp, context);
                sv = new ScriptValue(str.getAsString());
                break;
            case JSON:
                DocumentContext jsonDoc = toJsonDoc(evalKarateExpression(exp, context), context);
                sv = new ScriptValue(jsonDoc);
                break;
            case XML:
                Node xmlDoc = toXmlDoc(evalKarateExpression(exp, context), context);
                sv = new ScriptValue(xmlDoc);
                break;
            case XML_STRING:
                Node xmlStringDoc = toXmlDoc(evalKarateExpression(exp, context), context);
                sv = new ScriptValue(XmlUtils.toString(xmlStringDoc));
                break;
            case BYTE_ARRAY:
                ScriptValue tempBytes = evalKarateExpression(exp, context);
                sv = new ScriptValue(tempBytes.getAsByteArray());
                break;
            case COPY:
                sv = evalKarateExpression(exp, context).copy(true);
                break;
            default: // AUTO
                sv = evalKarateExpression(exp, context);
        }
        context.vars.put(name, sv);
        return sv;
    }

    public static DocumentContext toJsonDoc(ScriptValue sv, ScenarioContext context) {
        if (sv.isJson()) { // optimize
            return (DocumentContext) sv.getValue();
        } else if (sv.isListLike()) {
            return JsonPath.parse(sv.getAsList());
        } else if (sv.isMapLike()) {
            return JsonPath.parse(sv.getAsMap());
        } else if (sv.isUnknown()) { // POJO
            return JsonUtils.toJsonDoc(sv.getValue());
        }
        ScriptValue temp = evalKarateExpression(sv.getAsString(), context);
        if (!temp.isJson()) {
            throw new RuntimeException("cannot convert, not a json string: " + sv);
        }
        return temp.getValue(DocumentContext.class);
    }

    private static Node toXmlDoc(ScriptValue sv, ScenarioContext context) {
        if (sv.isXml()) {
            return sv.getValue(Node.class);
        } else if (sv.isMapLike()) {
            return XmlUtils.fromMap(sv.getAsMap());
        } else if (sv.isUnknown()) { // POJO
            return XmlUtils.toXmlDoc(sv.getValue());
        } else if (sv.isStringOrStream()) {
            ScriptValue temp = evalKarateExpression(sv.getAsString(), context);
            if (!temp.isXml()) {
                throw new RuntimeException("cannot convert, not an xml string: " + sv);
            }
            return temp.getValue(Document.class);
        } else {
            throw new RuntimeException("cannot convert to xml: " + sv);
        }
    }

    public static AssertionResult matchNamed(MatchType matchType, String expression, String path, String expected, ScenarioContext context) {
        String name = StringUtils.trimToEmpty(expression);
        if (isDollarPrefixedJsonPath(name) || isXmlPath(name)) { // short-cut for operating on response
            path = name;
            name = ScriptValueMap.VAR_RESPONSE;
        }
        if (name.startsWith("$")) { // in case someone used the dollar prefix by mistake on the LHS
            name = name.substring(1);
        }
        path = StringUtils.trimToNull(path);
        if (path == null) {
            StringUtils.Pair pair = parseVariableAndPath(name);
            name = pair.left;
            path = pair.right;
        }
        if (VAR_HEADER.equals(name)) { // convenience shortcut for asserting against response header
            return matchNamed(matchType, ScriptValueMap.VAR_RESPONSE_HEADERS, "$['" + path + "'][0]", expected, context);
        }
        ScriptValue actual;
        // karate started out by "defaulting" to JsonPath on the LHS of a match so we have this kludge
        // but we now handle JS expressions of almost any shape on the LHS, if in doubt, wrap in parentheses
        // actually it is not too bad - the XPath function check is the only odd one out
        // rules:
        // if not XPath function, wrapped in parentheses, involves function call
        //      [then] JS eval
        // else if XPath, JsonPath, JsonPath wildcard ".." or "*" or "[?"
        //      [then] eval name, and do a JsonPath or XPath using the parsed path
        if (isXmlPathFunction(path) 
                || (!name.startsWith("(") && !path.endsWith(")") && !path.contains(")."))
                && (isDollarPrefixed(path) || isJsonPath(path) || isXmlPath(path))) {
            actual = evalKarateExpression(name, context);
            // edge case: java property getter, e.g. "driver.cookies"
            if (!actual.isJsonLike() && !isXmlPath(path) && !isXmlPathFunction(path)) {
                // super rare edge case "match $.foo == '#notnull'" for response (which is not json, and will fail match)
                if (expression.startsWith("$")) {
                    expression = name + expression.substring(1);
                }
                actual = evalKarateExpression(expression, context); // fall back to JS eval
                path = VAR_ROOT;
            }
        } else {
            actual = evalKarateExpression(expression, context);
            path = VAR_ROOT; // we have eval-ed the entire LHS, so match RHS to "$"
        }
        return matchScriptValue(matchType, actual, path, expected, context);
    }

    public static AssertionResult matchScriptValue(MatchType matchType, ScriptValue actual, String path, String expected, ScenarioContext context) {
        switch (actual.getType()) {
            case STRING:
            case INPUT_STREAM:
                // this is the entry point so if the "root" is a string, path should be "$"
                if (path.length() > 1) { // so if the path is expecting JSON e.g. $.foo (not a string), fail right here
                    return matchFailed(matchType, path, actual.getValue(), expected, "actual value is not JSON-like");
                }
                return matchString(matchType, actual, expected, path, context);
            case XML:
                if ("$".equals(path)) {
                    path = "/"; // whole document, also edge case where variable name was 'response'
                }
            // break; 
            // fall through to JSON. yes, dot notation can be used on XML !!
            default:
                if (isDollarPrefixed(path)) {
                    return matchJsonOrObject(matchType, actual, path, expected, context);
                } else { // xpath
                    if (actual.getType() != XML) { // force conversion to xml
                        Node node = XmlUtils.fromMap(actual.getAsMap());
                        actual = new ScriptValue(node);
                    }
                    return matchXml(matchType, actual, path, expected, context);
                }
        }
    }

    public static AssertionResult matchString(MatchType matchType, ScriptValue actual, String expected, String path, ScenarioContext context) {
        ScriptValue expectedValue = evalKarateExpression(expected, context);
        if (expectedValue.isPrimitive() && !expectedValue.isString()) {
            if (matchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            } else {
                return matchFailed(matchType, path, actual.getValue(), expected, "actual value is a string");
            }
        }
        expected = expectedValue.getAsString();
        return matchStringOrPattern('*', path, matchType, null, null, actual, expected, context);
    }

    public static boolean isMacro(String text) {
        return text.startsWith("#");
    }

    public static boolean isOptionalMacro(String text) {
        return text.startsWith("##");
    }

    private static String stripParentheses(String s) {
        return StringUtils.trimToEmpty(s.substring(1, s.length() - 1));
    }

    public static AssertionResult matchStringOrPattern(char delimiter, String path, MatchType stringMatchType,
            Object actRoot, Object actParent, ScriptValue actValue, String expected, ScenarioContext context) {
        if (expected == null) {
            if (!actValue.isNull()) {
                if (stringMatchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(stringMatchType, path, actValue.getValue(), expected, "actual value is not null");
                }
            }
        } else if (isMacro(expected)) {
            String macroExpression;
            if (isOptionalMacro(expected)) {
                macroExpression = expected.substring(2); // this is used later to look up validators by name
                if (actValue.isNull()) {
                    boolean isEqual;
                    if (macroExpression.equals("null")) { // edge case
                        isEqual = true;
                    } else if (macroExpression.equals("notnull")) {
                        isEqual = false;
                    } else {
                        isEqual = true; // for any optional, a null is ok
                    }
                    if (isEqual) {
                        if (stringMatchType == MatchType.NOT_EQUALS) {
                            return matchFailed(stringMatchType, path, actValue.getValue(), expected, "actual value is null");
                        } else {
                            return AssertionResult.PASS;
                        }
                    } else {
                        if (stringMatchType == MatchType.NOT_EQUALS) {
                            return AssertionResult.PASS;
                        } else {
                            return matchFailed(stringMatchType, path, actValue.getValue(), expected, "actual value is null");
                        }
                    }
                }
            } else {
                macroExpression = expected.substring(1); // // this is used later to look up validators by name
            }
            if (isWithinParentheses(macroExpression)) { // '#(foo)' | '##(foo)' | '#(^foo)'
                MatchType matchType = stringMatchType;
                macroExpression = stripParentheses(macroExpression);
                boolean isContains = true;
                if (isContainsMacro(macroExpression)) {
                    if (isContainsOnlyMacro(macroExpression)) {
                        matchType = MatchType.CONTAINS_ONLY;
                        macroExpression = macroExpression.substring(2);
                    } else if (isContainsAnyMacro(macroExpression)) {
                        matchType = MatchType.CONTAINS_ANY;
                        macroExpression = macroExpression.substring(2);
                    } else {
                        matchType = MatchType.CONTAINS;
                        macroExpression = macroExpression.substring(1);
                    }
                } else if (isNotContainsMacro(macroExpression)) {
                    matchType = MatchType.NOT_CONTAINS;
                    macroExpression = macroExpression.substring(2);
                } else {
                    isContains = false;
                }
                ScriptValue expValue = evalJsExpression(macroExpression, context, actValue, actRoot, actParent);
                if (isContains && actValue.isListLike() && !expValue.isListLike()) { // if RHS is not list, make it so for contains                    
                    expValue = new ScriptValue(Collections.singletonList(expValue.getValue()));
                }
                AssertionResult ar = matchNestedObject(delimiter, path, matchType, actRoot, actParent, actValue.getValue(), expValue.getValue(), context);
                if (!ar.pass && stringMatchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return ar;
                }
            } else if (macroExpression.startsWith("regex")) {
                String regex = macroExpression.substring(5).trim();
                RegexValidator v = new RegexValidator(regex);
                ValidationResult vr = v.validate(actValue);
                if (!vr.isPass()) {
                    if (stringMatchType == MatchType.NOT_EQUALS) {
                        return AssertionResult.PASS;
                    } else {
                        return matchFailed(stringMatchType, path, actValue.getValue(), expected, vr.getMessage());
                    }
                }
            } else if (macroExpression.startsWith("[") && macroExpression.indexOf(']') > 0) {
                // check if array
                ValidationResult vr = ArrayValidator.INSTANCE.validate(actValue);
                if (!vr.isPass()) {
                    if (stringMatchType == MatchType.NOT_EQUALS) {
                        return AssertionResult.PASS;
                    } else {
                        return matchFailed(stringMatchType, path, actValue.getValue(), expected, vr.getMessage());
                    }
                }
                int endBracketPos = macroExpression.indexOf(']');
                List actValueList = actValue.getAsList();
                if (endBracketPos > 1) {
                    int arrayLength = actValueList.size();
                    String bracketContents = macroExpression.substring(1, endBracketPos);
                    String expression;
                    if (bracketContents.indexOf('_') != -1) { // #[_ < 5]  
                        expression = bracketContents;
                    } else { // #[5] | #[$.foo] 
                        expression = bracketContents + " == " + arrayLength;
                    }
                    ScriptValue result = evalJsExpression(expression, context, new ScriptValue(arrayLength), actRoot, actParent);
                    if (!result.isBooleanTrue()) {
                        if (stringMatchType == MatchType.NOT_EQUALS) {
                            return AssertionResult.PASS;
                        } else {
                            return matchFailed(stringMatchType, path, actValue.getValue(), expected, "actual array length was: " + arrayLength);
                        }
                    }
                }
                if (macroExpression.length() > endBracketPos + 1) { // expression
                    // macro-fy before attempting to re-use match-each routine
                    String expression = macroExpression.substring(endBracketPos + 1);
                    expression = StringUtils.trimToNull(expression);
                    MatchType matchType = stringMatchType == MatchType.NOT_EQUALS ? MatchType.EACH_NOT_EQUALS : MatchType.EACH_EQUALS;
                    if (expression != null) {
                        if (expression.startsWith("?")) {
                            expression = "'#" + expression + "'";
                        } else if (expression.startsWith("#")) {
                            if (expression.startsWith("#regex")) { // hack for horrible edge case
                                expression = expression.replaceAll("\\\\", "\\\\\\\\");
                            }
                            expression = "'" + expression + "'";
                        } else {
                            if (isWithinParentheses(expression)) {
                                expression = stripParentheses(expression);
                            }
                            if (isContainsMacro(expression)) {
                                if (isContainsOnlyMacro(expression)) {
                                    matchType = MatchType.EACH_CONTAINS_ONLY;
                                    expression = expression.substring(2);
                                } else if (isContainsAnyMacro(expression)) {
                                    matchType = MatchType.EACH_CONTAINS_ANY;
                                    expression = expression.substring(2);
                                } else {
                                    matchType = MatchType.EACH_CONTAINS;
                                    expression = expression.substring(1);
                                }
                            } else if (isNotContainsMacro(expression)) {
                                matchType = MatchType.EACH_NOT_CONTAINS;
                                expression = expression.substring(2);
                            }
                        }
                        // actRoot assumed to be json in this case                        
                        return matchJsonOrObject(matchType, new ScriptValue(actRoot), path, expression, context);
                    }
                }
            } else { // '#? _ != 0' | '#string' | '#number? _ > 0'
                int questionPos = macroExpression.indexOf('?');
                String validatorName = null;
                if (questionPos != -1) {
                    validatorName = macroExpression.substring(0, questionPos);
                } else {
                    validatorName = macroExpression;
                }
                validatorName = StringUtils.trimToNull(validatorName);
                if (validatorName != null) {
                    Validator v = VALIDATORS.get(validatorName);
                    if (v == null) {
                        boolean pass = expected.equals(actValue.getAsString());
                        if (!pass) {
                            if (stringMatchType == MatchType.NOT_EQUALS) {
                                return AssertionResult.PASS;
                            } else {
                                return matchFailed(stringMatchType, path, actValue.getValue(), expected, "not equal");
                            }
                        }
                    } else {
                        ValidationResult vr = v.validate(actValue);
                        if (!vr.isPass()) {
                            if (stringMatchType == MatchType.NOT_EQUALS) {
                                return AssertionResult.PASS;
                            } else {
                                return matchFailed(stringMatchType, path, actValue.getValue(), expected, vr.getMessage());
                            }
                        }
                    }
                }
                if (questionPos != -1) {
                    macroExpression = macroExpression.substring(questionPos + 1);
                    ScriptValue result = evalJsExpression(macroExpression, context, actValue, actRoot, actParent);
                    if (!result.isBooleanTrue()) {
                        if (stringMatchType == MatchType.NOT_EQUALS) {
                            return AssertionResult.PASS;
                        } else {
                            return matchFailed(stringMatchType, path, actValue.getValue(), expected, "did not evaluate to 'true'");
                        }
                    }
                }
            }
        } else if (actValue.isStringOrStream()) {
            String actual = actValue.getAsString();
            switch (stringMatchType) {
                case CONTAINS:
                    if (!actual.contains(expected)) {
                        return matchFailed(stringMatchType, path, actual, expected, "not a sub-string");
                    }
                    break;
                case NOT_CONTAINS:
                    if (actual.contains(expected)) {
                        return matchFailed(stringMatchType, path, actual, expected, "does contain expected");
                    }
                    break;
                case NOT_EQUALS:
                    if (expected.equals(actual)) {
                        return matchFailed(stringMatchType, path, actual, expected, "is equal");
                    }
                    // edge case, we have to exit here !
                    // the check for a NOT_EQUALS at the end of this routine will flip to failure otherwise
                    return AssertionResult.PASS;
                // break;
                case EQUALS:
                    if (!expected.equals(actual)) {
                        return matchFailed(stringMatchType, path, actual, expected, "not equal");
                    }
                    break;
                default:
                    throw new RuntimeException("unsupported match type for string: " + stringMatchType);
            }
        } else { // actual value is not a string
            if (stringMatchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            }
            Object actual = actValue.getValue();
            return matchFailed(stringMatchType, path, actual, expected, "actual value is not a string");
        }
        // if we reached here, the macros passed
        if (stringMatchType == MatchType.NOT_EQUALS) {
            return matchFailed(stringMatchType, path, actValue.getValue(), expected, "matched");
        }
        return AssertionResult.PASS;
    }

    public static AssertionResult matchXml(MatchType matchType, ScriptValue actual, String path, String expression, ScenarioContext context) {
        Node node = actual.getValue(Node.class);
        actual = evalXmlPathOnXmlNode(node, path);
        ScriptValue expected = evalKarateExpression(expression, context);
        if (actual == null) { // the xpath did not exist
            if (expected.isString() && "#notpresent".equals(expected.getValue())) {
                return AssertionResult.PASS;
            }
            return matchFailed(matchType, path, null, expected.getValue(), "actual xpath does not exist");
        }
        Object actObject;
        Object expObject;
        switch (expected.getType()) {
            case XML: // convert to map and then compare               
                Node expNode = expected.getValue(Node.class);
                expObject = XmlUtils.toObject(expNode, true);
                actObject = XmlUtils.toObject(actual.getValue(Node.class), true);
                break;
            case MAP: // expected is already in map form, convert the actual also
                expObject = expected.getValue(Map.class);
                actObject = XmlUtils.toObject(actual.getValue(Node.class));
                break;
            case JSON: // special case - xpath expected to result in node-list
                expObject = expected.getValue(DocumentContext.class).read("$");
                actObject = actual.getValue(List.class);
                break;
            case LIST: // similar to above - xpath expected to result in node-list
                expObject = expected.getValue(List.class);
                actObject = actual.getValue(List.class);
                break;
            default: // try string comparison                
                expObject = expected.getAsString();
                if ("#present".equals(expObject)) {
                    return AssertionResult.PASS;
                }
                if ("#notpresent".equals(expObject)) {
                    return matchFailed(matchType, path, actual, expObject, "actual xpath exists");
                }
                actObject = actual.getAsString();
        }
        if ("/".equals(path)) {
            path = ""; // else error x-paths reported would start with "//"
        }
        return matchNestedObject('/', path, matchType, node, node.getParentNode(), actObject, expObject, context);
    }

    private static MatchType getInnerMatchType(MatchType outerMatchType) {
        switch (outerMatchType) {
            case EACH_CONTAINS:
                return MatchType.CONTAINS;
            case EACH_NOT_CONTAINS:
                return MatchType.NOT_CONTAINS;
            case EACH_CONTAINS_ONLY:
                return MatchType.CONTAINS_ONLY;
            case EACH_CONTAINS_ANY:
                return MatchType.CONTAINS_ANY;
            case EACH_EQUALS:
                return MatchType.EQUALS;
            case EACH_NOT_EQUALS:
                return MatchType.EQUALS;
            default:
                throw new RuntimeException("unexpected outer match type: " + outerMatchType);
        }
    }

    public static AssertionResult matchJsonOrObject(MatchType matchType, ScriptValue actual, String path, String expression, ScenarioContext context) {
        DocumentContext actualDoc;
        switch (actual.getType()) {
            case JSON:
            case MAP:
            case LIST:
                actualDoc = actual.getAsJsonDocument();
                break;
            case XML: // auto convert !
                actualDoc = XmlUtils.toJsonDoc(actual.getValue(Node.class));
                break;
            case STRING: // an edge case when the variable is a plain string not JSON, so switch to plain string compare
            case INPUT_STREAM:
                String actualString = actual.getAsString();
                ScriptValue expectedString = evalKarateExpression(expression, context);
                // exit the function early
                if (!expectedString.isStringOrStream()) {
                    return matchFailed(matchType, path, actualString, expectedString.getValue(),
                            "type of actual value is 'string' but that of expected is " + expectedString.getType());
                } else {
                    return matchStringOrPattern('.', path, matchType, null, null, actual, expectedString.getAsString(), context);
                }
            case PRIMITIVE: // an edge case when the variable is non-string, not-json (number / boolean)
                ScriptValue expected = evalKarateExpression(expression, context);
                if (expected.isStringOrStream()) { // fuzzy match macro
                    return matchStringOrPattern('.', path, matchType, null, null, actual, expected.getAsString(), context);
                } else {
                    return matchPrimitive(matchType, path, actual.getValue(), expected.getValue());
                }
            case NULL: // edge case, assume that this is the root variable that is null and the match is for an optional e.g. '##string'
                ScriptValue expectedNull = evalKarateExpression(expression, context);
                if (expectedNull.isNull()) {
                    if (matchType == MatchType.NOT_EQUALS) {
                        return matchFailed(matchType, path, null, null, "actual and expected values are both null");
                    }
                    return AssertionResult.PASS;
                } else if (!expectedNull.isStringOrStream()) { // primitive or anything which is not a string
                    if (matchType == MatchType.NOT_EQUALS) {
                        return AssertionResult.PASS;
                    }
                    return matchFailed(matchType, path, null, expectedNull.getValue(), "actual value is null but expected is " + expectedNull);
                } else {
                    return matchStringOrPattern('.', path, matchType, null, null, actual, expectedNull.getAsString(), context);
                }
            case BYTE_ARRAY:
                ScriptValue expectedBytesValue = evalKarateExpression(expression, context);
                byte[] expectedBytes = expectedBytesValue.getAsByteArray();
                byte[] actualBytes = actual.getAsByteArray();
                if (Arrays.equals(expectedBytes, actualBytes)) {
                    if (matchType == MatchType.NOT_EQUALS) {
                        return matchFailed(matchType, path, actualBytes, expectedBytes, "actual and expected byte-arrays are not equal");
                    }
                    return AssertionResult.PASS;
                } else {
                    if (matchType == MatchType.NOT_EQUALS) {
                        return AssertionResult.PASS;
                    }
                    return matchFailed(matchType, path, actualBytes, expectedBytes, "actual and expected byte-arrays are not equal");
                }
            default:
                throw new RuntimeException("not json, cannot do json path for value: " + actual + ", path: " + path);
        }
        ScriptValue expected = evalKarateExpressionForMatch(expression, context);
        Object actObject;
        try {
            actObject = actualDoc.read(path); // note that the path for actObject is 'reset' to '$' here
        } catch (PathNotFoundException e) {
            if (expected.isString()) {
                String expString = expected.getAsString();
                if (isOptionalMacro(expString) || "#notpresent".equals(expString) || "#ignore".equals(expString)) {
                    return AssertionResult.PASS;
                }
            }
            return matchFailed(matchType, path, null, expected.getValue(), "actual json-path does not exist");
        }
        Object expObject;
        switch (expected.getType()) {
            case JSON: // convert to map or list
                expObject = expected.getValue(DocumentContext.class).read("$");
                break;
            default:
                expObject = expected.getValue();
        }
        switch (matchType) {
            case CONTAINS:
            case NOT_CONTAINS:
            case CONTAINS_ONLY:
            case CONTAINS_ANY:
            case CONTAINS_DEEP:
                if (actObject instanceof List && !(expObject instanceof List)) { // if RHS is not a list, make it so
                    expObject = Collections.singletonList(expObject);
                }
            case NOT_EQUALS:
            case EQUALS:
                return matchNestedObject('.', path, matchType, actualDoc, null, actObject, expObject, context);
            case EACH_CONTAINS:
            case EACH_NOT_CONTAINS:
            case EACH_CONTAINS_ONLY:
            case EACH_CONTAINS_ANY:
            case EACH_NOT_EQUALS:
            case EACH_EQUALS:
                if (actObject instanceof List) {
                    List actList = (List) actObject;
                    MatchType listMatchType = getInnerMatchType(matchType);
                    int actSize = actList.size();
                    for (int i = 0; i < actSize; i++) {
                        Object actListObject = actList.get(i);
                        AssertionResult ar = matchNestedObject('.', "$[" + i + "]", listMatchType, actObject, actListObject, actListObject, expObject, context);
                        if (!ar.pass) {
                            if (matchType == MatchType.EACH_NOT_EQUALS) {
                                return AssertionResult.PASS; // exit early
                            } else {
                                return ar; // fail early
                            }
                        }
                    }
                    // if we reached here all list items (each) matched
                    if (matchType == MatchType.EACH_NOT_EQUALS) {
                        return matchFailed(matchType, path, actual.getValue(), expected.getValue(), "all list items matched");
                    }
                    return AssertionResult.PASS;
                } else {
                    throw new RuntimeException("'match each' failed, not a json array: + " + actual + ", path: " + path);
                }
            default: // dead code
                throw new RuntimeException("unexpected match type: " + matchType);
        }
    }

    private static String getLeafNameFromXmlPath(String path) {
        int pos = path.lastIndexOf('/');
        if (pos == -1) {
            return path;
        } else {
            path = path.substring(pos + 1);
            pos = path.indexOf('[');
            if (pos != -1) {
                return path.substring(0, pos);
            } else {
                return path;
            }
        }
    }

    private static Object toXmlString(String elementName, Object o) {
        if (o instanceof Map) {
            Node node = XmlUtils.fromObject(elementName, o);
            return XmlUtils.toString(node);
        } else if (o instanceof Node) {
            return XmlUtils.toString((Node) o);
        } else {
            return o;
        }
    }

    private static Object quoteIfString(Object o) {
        if (o instanceof String) {
            return "'" + o + "'";
        } else {
            return o;
        }
    }

    private static boolean isNegation(MatchType type) {
        switch (type) {
            case EACH_NOT_CONTAINS:
            case EACH_NOT_EQUALS:
            case NOT_CONTAINS:
            case NOT_EQUALS:
                return true;
            default:
                return false;
        }
    }

    public static AssertionResult matchFailed(MatchType matchType, String path,
            Object actObject, Object expObject, String reason) {
        if (path.startsWith("/")) {
            String leafName = getLeafNameFromXmlPath(path);
            if (!"@".equals(leafName)) {
                actObject = toXmlString(leafName, actObject);
                expObject = toXmlString(leafName, expObject);
            }
            path = path.replace("/@/", "/@");
        }
        String message = String.format("path: %s, actual: %s, %sexpected: %s, reason: %s",
                path, quoteIfString(actObject), isNegation(matchType) ? "NOT " : "", quoteIfString(expObject), reason);
        return AssertionResult.fail(message);
    }

    public static AssertionResult matchNestedObject(char delimiter, String path, MatchType matchType,
            Object actRoot, Object actParent, Object actObject, Object expObject, ScenarioContext context) {
        if (expObject instanceof Node) { // edge case for xml on rhs
            expObject = XmlUtils.toObject((Node) expObject);
        }
        if (expObject == null) {
            if (actObject != null) {
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(matchType, path, actObject, expObject, "actual value is not null");
                }
            } else { // both are null
                if (matchType == MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "equal, both are null");
                } else {
                    return AssertionResult.PASS;
                }
            }
        } else if (expObject instanceof String) {
            ScriptValue actValue = new ScriptValue(actObject);
            return matchStringOrPattern(delimiter, path, matchType, actRoot, actParent, actValue, expObject.toString(), context);
        } else if (actObject == null) {
            if (matchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            } else {
                return matchFailed(matchType, path, actObject, expObject, "actual value is null");
            }
        } else if (expObject instanceof Map) {
            if (!(actObject instanceof Map)) {
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(matchType, path, actObject, expObject, "actual value is not map-like");
                }
            }
            Map<String, Object> expMap = (Map) expObject;
            Map<String, Object> actMap = (Map) actObject;
            if (actMap.size() > expMap.size()) { // > is because of the chance of #ignore
                if (matchType == MatchType.EQUALS || matchType == MatchType.CONTAINS_ONLY) {
                    int sizeDiff = actMap.size() - expMap.size();
                    Map<String, Object> diffMap = new LinkedHashMap(actMap);
                    for (String key : expMap.keySet()) {
                        diffMap.remove(key);
                    }
                    return matchFailed(matchType, path, actObject, expObject, "actual value has " + sizeDiff + " more key(s) than expected: " + diffMap);
                } else if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS; // exit early
                }
            }
            Set<String> unMatchedKeysAct = new LinkedHashSet(actMap.keySet());
            Set<String> unMatchedKeysExp = new LinkedHashSet(expMap.keySet());
            AssertionResult firstMisMatch = null;
            for (Map.Entry<String, Object> expEntry : expMap.entrySet()) {
                String key = expEntry.getKey();
                Object childExp = expEntry.getValue();
                String childPath = delimiter == '.' ? JsonUtils.buildPath(path, key) : path + delimiter + key;
                if (!actMap.containsKey(key)) {
                    boolean equal = false;
                    if (childExp instanceof String) {
                        String childMacro = (String) childExp;
                        if (isOptionalMacro(childMacro)
                                || childMacro.equals("#notpresent")
                                || childMacro.equals("#ignore")) { // logical match
                            if (matchType == MatchType.NOT_CONTAINS) {
                                return matchFailed(matchType, childPath, "(not present)", childExp, "actual value contains expected");
                            }
                            equal = true;
                            unMatchedKeysExp.remove(key);
                        }
                    }
                    if (!equal) {
                        if (matchType == MatchType.NOT_EQUALS) {
                            return AssertionResult.PASS; // exit early
                        }
                        if (matchType == MatchType.NOT_CONTAINS) {
                            return AssertionResult.PASS; // exit early
                        }
                    } else { // we found one
                        if (matchType == MatchType.CONTAINS_ANY) {
                            return AssertionResult.PASS; // at least one matched, exit early
                        }
                    }
                    continue; // end edge case for key not present
                }
                Object childAct = actMap.get(key);
                ScriptValue childActValue = new ScriptValue(childAct);
                MatchType childMatchType = MatchType.EQUALS;
                if (matchType.equals(MatchType.CONTAINS_DEEP)) {
                    if (childActValue.isJsonLike()) {
                        childMatchType = MatchType.CONTAINS_DEEP;
                    }
                }
                AssertionResult ar = matchNestedObject(delimiter, childPath, childMatchType, actRoot, actMap, childAct, childExp, context);
                if (ar.pass) { // values for this key match                    
                    if (matchType == MatchType.CONTAINS_ANY) {
                        return AssertionResult.PASS; // exit early
                    }
                    if (matchType == MatchType.NOT_CONTAINS) {
                        // did we just bubble-up from a map
                        ScriptValue childExpValue = new ScriptValue(childExp);
                        if (childExpValue.isMapLike()) {
                            // a nested map already fulfilled the NOT_CONTAINS
                            return AssertionResult.PASS; // exit early
                        }
                    }
                    unMatchedKeysExp.remove(key);
                    unMatchedKeysAct.remove(key);
                } else { // values for this key don't match
                    if (matchType == MatchType.NOT_EQUALS) {
                        return AssertionResult.PASS; // exit early
                    }
                    if (matchType == MatchType.NOT_CONTAINS) {
                        return AssertionResult.PASS; // exit early
                    }
                    if (firstMisMatch == null) {
                        firstMisMatch = ar;
                    }
                }
            }
            if (matchType == MatchType.CONTAINS_ANY) {
                // if any were found, we would have exited early
                return matchFailed(matchType, path, actObject, expObject, "no key-values matched");
            }
            if (!unMatchedKeysExp.isEmpty()) {
                if (matchType == MatchType.NOT_CONTAINS) {
                    return AssertionResult.PASS;
                }
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                }
                if (firstMisMatch != null) {
                    return firstMisMatch;
                } else {
                    return matchFailed(matchType, path, actObject, expObject, "all key-values did not match, expected has un-matched keys: " + unMatchedKeysExp);
                }
            } else if (!unMatchedKeysAct.isEmpty()) {
                if (matchType == MatchType.CONTAINS || matchType == MatchType.CONTAINS_DEEP || expMap.isEmpty()) {
                    return AssertionResult.PASS;
                }
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                }
                return matchFailed(matchType, path, actObject, expObject, "all key-values did not match, actual has un-matched keys: " + unMatchedKeysAct);
            } else {
                // if we reached here, all map-entries matched
                if (matchType == MatchType.NOT_CONTAINS) {
                    if (expMap.isEmpty()) { // special case, does not make sense to "!contains {}"
                        return AssertionResult.PASS;
                    }
                    return matchFailed(matchType, path, actObject, expObject, "actual contains all expected key-values");
                }
                if (matchType == MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "all key-values matched");
                }
                return AssertionResult.PASS;
            }
        } else if (expObject instanceof List) {
            if (!(actObject instanceof List)) {
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(matchType, path, actObject, expObject, "actual value is not list-like");
                }
            }
            List expList = (List) expObject;
            List actList = (List) actObject;
            int actCount = actList.size();
            int expCount = expList.size();
            if (actCount != expCount) {
                if (matchType == MatchType.EQUALS || matchType == MatchType.CONTAINS_ONLY) {
                    return matchFailed(matchType, path, actObject, expObject, "actual and expected arrays are not the same size - " + actCount + ":" + expCount);
                } else if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS; // exit early
                }
            }
            if (matchType == MatchType.CONTAINS
                    || matchType == MatchType.CONTAINS_ONLY
                    || matchType == MatchType.CONTAINS_ANY
                    || matchType == MatchType.NOT_CONTAINS
                    || matchType == MatchType.CONTAINS_DEEP) { // just checks for existence (or non-existence)
                for (Object expListObject : expList) { // for each expected item in the list
                    boolean found = false;
                    for (int i = 0; i < actCount; i++) {
                        Object actListObject = actList.get(i);
                        String listPath = buildListPath(delimiter, path, i);
                        MatchType childMatchType = matchType.equals(MatchType.CONTAINS_DEEP) ? MatchType.CONTAINS_DEEP : MatchType.EQUALS;
                        AssertionResult ar = matchNestedObject(delimiter, listPath, childMatchType, actRoot, actListObject, actListObject, expListObject, context);
                        if (ar.pass) { // exact match, we found it
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        if (matchType == MatchType.NOT_CONTAINS) {
                            return matchFailed(matchType, path + "[*]", actObject, expListObject, "actual value contains unexpected");
                        }
                        if (matchType == MatchType.CONTAINS_ANY) {
                            return AssertionResult.PASS; // exit early
                        }
                    } else {
                        if (matchType == MatchType.CONTAINS_ANY) {
                            continue; // keep trying
                        }
                        if (matchType != MatchType.NOT_CONTAINS) {
                            return matchFailed(matchType, path + "[*]", actObject, expListObject, "actual value does not contain expected");
                        }
                    }
                }
                if (matchType == MatchType.CONTAINS_ANY) {
                    // if we found any, we would have exited early
                    return matchFailed(matchType, path + "[*]", actObject, expList, "actual value does not contain any expected");
                }
                // reminder: we are here only for the contains / not-contains cases
                return AssertionResult.PASS; // all items were found
            } else { // exact compare of list elements and order
                for (int i = 0; i < expCount; i++) {
                    Object expListObject = expList.get(i);
                    Object actListObject = actList.get(i);
                    String listPath = buildListPath(delimiter, path, i);
                    AssertionResult ar = matchNestedObject(delimiter, listPath, MatchType.EQUALS, actRoot, actListObject, actListObject, expListObject, context);
                    if (!ar.pass) {
                        if (matchType == MatchType.NOT_EQUALS) {
                            return AssertionResult.PASS; // exit early
                        } else {
                            return matchFailed(matchType, listPath, actListObject, expListObject, "[" + ar.message + "]");
                        }
                    }
                }
                // if we reached here, all list entries matched
                if (matchType == MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "all list items matched");
                }
                return AssertionResult.PASS;
            }
        } else if (expObject instanceof BigDecimal) {
            BigDecimal expNumber = (BigDecimal) expObject;
            if (actObject instanceof BigDecimal) {
                BigDecimal actNumber = (BigDecimal) actObject;
                if (actNumber.compareTo(expNumber) != 0 && matchType != MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "not equal (big decimal)");
                }
            } else {
                BigDecimal actNumber = convertToBigDecimal(actObject);
                if ((actNumber == null || actNumber.compareTo(expNumber) != 0) && matchType != MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "not equal (primitive : big decimal)");
                }
            }
            // if we reached here, both are equal
            if (matchType == MatchType.NOT_EQUALS) {
                return matchFailed(matchType, path, actObject, expObject, "equal");
            }
            return AssertionResult.PASS;
        } else if (isPrimitive(expObject.getClass())) {
            return matchPrimitive(matchType, path, actObject, expObject);
        } else { // this should never happen
            throw new RuntimeException("unexpected type: " + expObject.getClass());
        }
    }

    public static boolean isPrimitive(Class clazz) {
        return clazz.isPrimitive()
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.equals(clazz);
    }

    private static String buildListPath(char delimiter, String path, int index) {
        int listIndex = delimiter == '/' ? index + 1 : index;
        return path + "[" + listIndex + "]";
    }

    private static BigDecimal convertToBigDecimal(Object o) {
        DecimalFormat df = new DecimalFormat();
        df.setParseBigDecimal(true);
        try {
            return (BigDecimal) df.parse(o.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static AssertionResult matchPrimitive(MatchType matchType, String path, Object actObject, Object expObject) {
        if (actObject == null) {
            if (matchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            } else {
                return matchFailed(matchType, path, actObject, expObject, "actual value is null");
            }
        } else if (expObject == null) {
            if (matchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            } else {
                return matchFailed(matchType, path, actObject, expObject, "expected value is null");
            }
        }
        // if either was null we would have exited already
        if (!expObject.getClass().equals(actObject.getClass())) {
            if (actObject instanceof BigDecimal) {
                BigDecimal actNumber = (BigDecimal) actObject;
                BigDecimal expNumber = convertToBigDecimal(expObject);
                if ((expNumber == null || expNumber.compareTo(actNumber) != 0) && matchType != MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "not equal (big decimal : primitive)");
                }
            } else if (actObject instanceof Number && expObject instanceof Number) {
                // use the JS engine for a lenient equality check
                String exp = actObject + " == " + expObject;
                ScriptValue sv = evalJsExpression(exp, null);
                if (!sv.isBooleanTrue() && matchType != MatchType.NOT_EQUALS) {
                    return matchFailed(matchType, path, actObject, expObject, "not equal ("
                            + actObject.getClass().getSimpleName() + " : " + expObject.getClass().getSimpleName() + ")");
                }
            } else { // completely different data types, certainly not equal
                if (matchType == MatchType.NOT_EQUALS) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(matchType, path, actObject, expObject, "not equal ("
                            + actObject.getClass().getSimpleName() + " : " + expObject.getClass().getSimpleName() + ")");
                }
            }
        } else if (!expObject.equals(actObject)) { // same data type, but not equal
            if (matchType == MatchType.NOT_EQUALS) {
                return AssertionResult.PASS;
            } else {
                return matchFailed(matchType, path, actObject, expObject, "not equal (" + actObject.getClass().getSimpleName() + ")");
            }
        }
        // if we reached here both were equal
        if (matchType == MatchType.NOT_EQUALS) {
            return matchFailed(matchType, path, actObject, expObject, "equal");
        }
        return AssertionResult.PASS;
    }

    public static void removeValueByPath(String name, String path, ScenarioContext context) {
        setValueByPath(name, path, ScriptValue.NULL, true, context, false);
    }

    public static void setValueByPath(String name, String path, ScriptValue value, ScenarioContext context) {
        setValueByPath(name, path, value, false, context, false);
    }

    public static void setValueByPath(String name, String path, String exp, ScenarioContext context) {
        setValueByPath(name, path, exp, false, context, false);
    }

    public static void setValueByPath(String name, String path, String exp, boolean delete, ScenarioContext context, boolean viaTable) {
        ScriptValue value = delete ? ScriptValue.NULL : evalKarateExpression(exp, context);
        if (viaTable && value.isNull() && !isWithinParentheses(exp)) {
            // by default, skip any expression that evaluates to null unless the user expressed
            // intent to over-ride by enclosing the expression in parentheses
            return;
        }
        setValueByPath(name, path, value, delete, context, viaTable);
    }

    public static void setValueByPath(String name, String path, ScriptValue value, boolean delete, ScenarioContext context, boolean viaTable) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path);
        if (path == null) {
            StringUtils.Pair nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.left;
            path = nameAndPath.right;
        }
        if (isDollarPrefixedJsonPath(path)) {
            ScriptValue target = context.vars.get(name);
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    DocumentContext empty;
                    if (path.startsWith("$[") && !path.startsWith("$['")) {
                        empty = JsonUtils.emptyJsonArray(0);
                    } else {
                        empty = JsonUtils.emptyJsonObject();
                    }
                    target = new ScriptValue(empty);
                    context.vars.put(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            if (target.isJsonLike()) {
                DocumentContext dc = target.getAsJsonDocument();
                JsonUtils.setValueByPath(dc, path, value.getAfterConvertingFromJsonOrXmlIfNeeded(), delete);
            } else {
                throw new RuntimeException("cannot set json path on unexpected type: " + target);
            }
        } else if (isXmlPath(path)) {
            ScriptValue target = context.vars.get(name);
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    Document empty = XmlUtils.newDocument();
                    target = new ScriptValue(empty);
                    context.vars.put(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            Document doc = target.getValue(Document.class);
            if (delete) {
                XmlUtils.removeByPath(doc, path);
            } else if (value.getType() == XML) {
                Node node = value.getValue(Node.class);
                XmlUtils.setByPath(doc, path, node);
            } else if (value.isMapLike()) { // cast to xml
                Node node = XmlUtils.fromMap(value.getAsMap());
                XmlUtils.setByPath(doc, path, node);
            } else {
                XmlUtils.setByPath(doc, path, value.getAsString());
            }
        } else {
            throw new RuntimeException("unexpected path: " + path);
        }
    }

    public static ScriptValue call(ScriptValue called, String argString, ScenarioContext context, boolean reuseParentConfig) {
        ScriptValue argValue = evalKarateExpression(argString, context);
        switch (called.getType()) {
            case JAVA_FUNCTION:
                Function function = called.getValue(Function.class);
                return evalJavaFunctionCall(function, argValue.getValue(), context);
            case JS_FUNCTION:
                ScriptObjectMirror som = called.getValue(ScriptObjectMirror.class);
                return evalJsFunctionCall(som, argValue.getAfterConvertingFromJsonOrXmlIfNeeded(), context);
            case FEATURE:
                Object callArg = null;
                switch (argValue.getType()) {
                    case LIST:
                        callArg = argValue.getValue(List.class);
                        break;
                    case JSON:
                        callArg = argValue.getValue(DocumentContext.class).read("$");
                        break;
                    case MAP:
                        callArg = argValue.getValue(Map.class);
                        break;
                    case NULL:
                        break;
                    default:
                        throw new RuntimeException("only json/map or list/array allowed as feature call argument");
                }
                Feature feature = called.getValue(Feature.class);
                return evalFeatureCall(feature, callArg, context, reuseParentConfig);
            default:
                context.logger.warn("not a js function or feature file: {}", called);
                return ScriptValue.NULL;
        }
    }

    public static ScriptValue evalJavaFunctionCall(Function function, Object callArg, ScenarioContext context) {
        try {
            Object result = function.apply(callArg);
            return new ScriptValue(result);
        } catch (Exception e) {
            String message = "java function call failed: " + e.getMessage();
            context.logger.error(message);
            throw new KarateException(message);
        }
    }

    public static ScriptValue evalJsFunctionCall(ScriptObjectMirror som, Object callArg, ScenarioContext context) {
        Object result;
        try {
            if (callArg != null) {
                result = som.call(som, callArg);
            } else {
                result = som.call(som);
            }
            return new ScriptValue(result);
        } catch (Exception e) {
            String message = "javascript function call failed: " + e.toString();
            context.logger.error(message);
            context.logger.error("failed function body: " + som);
            throw new KarateException(message);
        }
    }

    public static ScriptValue evalFeatureCall(Feature feature, Object callArg, ScenarioContext context, boolean reuseParentConfig) {
        if (callArg instanceof Collection) { // JSON array
            Collection items = (Collection) callArg;
            Object[] array = items.toArray();
            List result = new ArrayList(array.length);
            List<String> errors = new ArrayList(array.length);
            for (int i = 0; i < array.length; i++) {
                Object rowArg = array[i];
                if (rowArg instanceof Map) {
                    Map rowArgMap = (Map) rowArg;
                    try {
                        CallContext callContext = CallContext.forCall(feature, context, rowArgMap, i, reuseParentConfig);
                        ScriptValue rowResult = evalFeatureCall(callContext);
                        result.add(rowResult.getValue());
                    } catch (KarateException ke) {
                        String argString = new ScriptValue(rowArg).getAsString(); // convert if JS object from [object: Object]
                        String message = "feature call (loop) failed at index: " + i + "\ncaller: "
                                + feature.getRelativePath() + "\narg: " + argString + "\n" + ke.getMessage();
                        errors.add(message);
                        // log but don't stop (yet)
                        context.logger.error("{}", message);
                    }
                } else {
                    throw new RuntimeException("argument not json or map for feature call loop array position: " + i + ", " + rowArg);
                }
            }
            if (!errors.isEmpty()) {
                String caller = context.featureContext.feature.getRelativePath();
                String message = "feature call (loop) failed: " + feature.getRelativePath()
                        + "\ncaller: " + caller + "\nitems: " + items + "\nerrors:";
                for (String s : errors) {
                    message = message + "\n-------\n" + s;
                }
                throw new KarateException(message);
            }
            return new ScriptValue(result);
        } else if (callArg == null || callArg instanceof Map) {
            Map<String, Object> argAsMap = (Map) callArg;
            try {
                CallContext callContext = CallContext.forCall(feature, context, argAsMap, -1, reuseParentConfig);
                return evalFeatureCall(callContext);
            } catch (KarateException ke) {
                String argString = new ScriptValue(callArg).getAsString(); // convert if JS object from [object: Object] 
                String message = "feature call failed: " + feature.getRelativePath()
                        + "\narg: " + argString + "\n" + ke.getMessage();
                context.logger.error("{}", message);
                throw new KarateException(message, ke);
            }
        } else {
            throw new RuntimeException("unexpected feature call arg type: " + callArg.getClass());
        }
    }

    private static ScriptValue evalFeatureCall(CallContext callContext) {
        // the call is always going to execute synchronously !
        FeatureResult result = Engine.executeFeatureSync(null, callContext.feature, null, callContext);
        // hack to pass call result back to caller step
        callContext.context.addCallResult(result);
        result.setCallArg(callContext.callArg);
        result.setLoopIndex(callContext.loopIndex);
        if (result.isFailed()) {
            throw result.getErrorsCombined();
        }
        return new ScriptValue(result.getResultAsPrimitiveMap());
    }

    public static StringUtils.Pair parseCallArgs(String line) {
        int pos = line.indexOf("read(");
        if (pos != -1) {
            pos = line.indexOf(')');
            if (pos == -1) {
                throw new RuntimeException("failed to parse call arguments: " + line);
            }
            return new StringUtils.Pair(line.substring(0, pos + 1), line.substring(pos + 1));
        }
        pos = line.indexOf(' ');
        if (pos == -1) {
            return new StringUtils.Pair(line, null);
        }
        return new StringUtils.Pair(line.substring(0, pos), line.substring(pos));
    }

    public static void callAndUpdateConfigAndAlsoVarsIfMapReturned(boolean callOnce, String name, String arg, ScenarioContext context) {
        ScriptValue called = evalKarateExpression(name, context);
        ScriptValue sv;
        if (callOnce) {
            sv = callWithCache(name, called, arg, context, true);
        } else {
            sv = call(called, arg, context, true);
        }
        // feature calls will update context automatically
        if (!called.isFeature() && sv.isMapLike()) {
            context.logger.trace("auto-adding vars returned from function call result: {}", sv);
            sv.getAsMap().forEach((k, v) -> context.vars.put(k, v));
        }
    }

    public static AssertionResult assertBoolean(String expression, ScenarioContext context) {
        ScriptValue result = evalJsExpression(expression, context);
        if (!result.isBooleanTrue()) {
            return AssertionResult.fail("assert evaluated to false: " + expression);
        }
        return AssertionResult.PASS;
    }

    public static String replacePlaceholderText(String text, String token, String replaceWith, ScenarioContext context) {
        if (text == null) {
            return null;
        }
        replaceWith = StringUtils.trimToNull(replaceWith);
        if (replaceWith == null) {
            return text;
        }
        try {
            ScriptValue sv = evalKarateExpression(replaceWith, context);
            replaceWith = sv.getAsString();
        } catch (Exception e) {
            throw new RuntimeException("expression error (replace string values need to be within quotes): " + e.getMessage());
        }
        if (replaceWith == null) { // ignore if eval result is null
            return text;
        }
        token = StringUtils.trimToNull(token);
        if (token == null) {
            return text;
        }
        char firstChar = token.charAt(0);
        if (Character.isLetterOrDigit(firstChar)) {
            token = '<' + token + '>';
        }
        return text.replace(token, replaceWith);
    }

    private static final String TOKEN = "token";

    public static String replacePlaceholders(String text, List<Map<String, String>> list, ScenarioContext context) {
        if (text == null) {
            return null;
        }
        if (list == null) {
            return text;
        }
        for (Map<String, String> map : list) {
            String token = map.get(TOKEN);
            if (token == null) {
                continue;
            }
            // the verbosity below is to be lenient with table second column name
            List<String> keys = new ArrayList(map.keySet());
            keys.remove(TOKEN);
            Iterator<String> iterator = keys.iterator();
            if (iterator.hasNext()) {
                String key = keys.iterator().next();
                String value = map.get(key);
                text = replacePlaceholderText(text, token, value, context);
            }
        }
        return text;
    }

    public static List<Map<String, Object>> evalTable(List<Map<String, String>> list, ScenarioContext context) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Map<String, String> map : list) {
            Map<String, Object> row = new LinkedHashMap<>(map);
            List<String> toRemove = new ArrayList(map.size());
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String exp = (String) entry.getValue();
                ScriptValue sv = evalKarateExpression(exp, context);
                if (sv.isNull() && !isWithinParentheses(exp)) { // by default empty / null will be stripped, force null like this: '(null)'
                    toRemove.add(entry.getKey());
                } else {
                    if (sv.isJsonLike()) {
                        entry.setValue(sv.getAsJsonDocument().read("$")); // will be Map or List
                    } else if (sv.isString()) {
                        entry.setValue(sv.getAsString());
                    } else {
                        entry.setValue(sv.getValue());
                    }
                }
            }
            for (String keyToRemove : toRemove) {
                row.remove(keyToRemove);
            }
            result.add(row);
        }
        return result;
    }

    private static final String PATH = "path";

    public static void setByPathTable(String name, String path, List<Map<String, String>> list, ScenarioContext context) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path); // TODO re-factor these few lines cut and paste
        if (path == null) {
            StringUtils.Pair nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.left;
            path = nameAndPath.right;
        }
        for (Map<String, String> map : list) {
            String append = (String) map.get(PATH);
            if (append == null) {
                continue;
            }
            List<String> keys = new ArrayList(map.keySet());
            keys.remove(PATH);
            int columnCount = keys.size();
            for (int i = 0; i < columnCount; i++) {
                String key = keys.get(i);
                String expression = StringUtils.trimToNull(map.get(key));
                if (expression == null) { // cucumber cell was left blank
                    continue; // skip
                    // default behavior is to skip nulls when the expression evaluates 
                    // this is driven by the routine in setValueByPath
                    // and users can over-ride this by simple enclosing the expression in parentheses
                }
                String suffix;
                try {
                    int arrayIndex = Integer.valueOf(key);
                    suffix = "[" + arrayIndex + "]";
                } catch (NumberFormatException e) { // default to the column position as the index
                    suffix = columnCount > 1 ? "[" + i + "]" : "";
                }
                String finalPath;
                if (append.startsWith("/") || (path != null && path.startsWith("/"))) { // XML
                    if (path == null) {
                        finalPath = append + suffix;
                    } else {
                        finalPath = path + suffix + '/' + append;
                    }
                } else {
                    if (path == null) {
                        path = "$";
                    }
                    finalPath = path + suffix + '.' + append;
                }
                setValueByPath(name, finalPath, expression, false, context, true);
            }
        }
    }

}
