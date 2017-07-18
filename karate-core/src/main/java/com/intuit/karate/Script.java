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

import com.intuit.karate.exception.KarateException;
import static com.intuit.karate.ScriptValue.Type.*;
import com.intuit.karate.cucumber.CucumberUtils;
import com.intuit.karate.cucumber.FeatureWrapper;
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
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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

    public static final String VAR_SELF = "_";
    public static final String VAR_DOLLAR = "$";

    private Script() {
        // only static methods
    }

    public static final boolean isCallSyntax(String text) {
        return text.startsWith("call ");
    }

    public static final boolean isCallOnceSyntax(String text) {
        return text.startsWith("callonce ");
    }

    public static final boolean isGetSyntax(String text) {
        return text.startsWith("get ");
    }

    public static final boolean isJson(String text) {
        return text.startsWith("{") || text.startsWith("[");
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
        return text.startsWith("#(") && text.endsWith(")");
    }

    public static final boolean isWithinParantheses(String text) {
        return text.startsWith("(") && text.endsWith(")");
    }

    public static final boolean isContainsMacro(String text) {
        return text.startsWith("^");
    }
    
    public static final boolean isNotContainsMacro(String text) {
        return text.startsWith("!^");
    }    

    public static final boolean isJsonPath(String text) {
        return text.startsWith("$.") || text.startsWith("$[") || text.equals("$");
    }

    public static final boolean isVariable(String text) {
        return VARIABLE_PATTERN.matcher(text).matches();
    }

    public static final boolean isVariableAndSpaceAndPath(String text) {
        return text.matches("^" + VARIABLE_PATTERN_STRING + "\\s+.+");
    }

    public static final boolean isVariableAndJsonPath(String text) {
        return !text.endsWith(")") // hack, just to ignore JS method calls
                && text.matches("^" + VARIABLE_PATTERN_STRING + "\\..+");
    }

    public static final boolean isVariableAndXmlPath(String text) {
        return text.matches("^" + VARIABLE_PATTERN_STRING + "/.*");
    }

    public static final boolean isStringExpression(String text) {
        // somewhat of a cop out but should be sufficient
        // users can enclose complicated expressions in parantheses as a work around
        return text.startsWith("'") || text.startsWith("\"")
                || text.endsWith("'") || text.endsWith("\"");
    }

    public static boolean isJavaScriptFunction(String text) {
        return text.matches("^function\\s*[(].+");
    }

    private static final Pattern VAR_AND_PATH_PATTERN = Pattern.compile("\\w+");

    public static Pair<String, String> parseVariableAndPath(String text) {
        Matcher matcher = VAR_AND_PATH_PATTERN.matcher(text);
        matcher.find();
        String name = text.substring(0, matcher.end());
        String path;
        if (matcher.end() == text.length()) {
            path = "";
        } else {
            path = text.substring(matcher.end());
        }
        if (Script.isXmlPath(path) || Script.isXmlPathFunction(path)) {
            // xml, don't prefix for json
        } else {
            path = "$" + path;
        }
        return Pair.of(name, path);
    }

    public static ScriptValue eval(String text, ScriptContext context) {
        return eval(text, context, false);
    }

    private static ScriptValue callWithCache(String text, String arg, ScriptContext context, boolean reuseParentConfig) {
        CallResult result = context.env.callCache.get(text);
        if (result != null) {
            context.logger.debug("callonce cache hit for: {}", text);
            if (reuseParentConfig) { // re-apply config that may have been lost when we switched scenarios within a feature
                context.configure(result.config);
            }
            return result.value;
        }
        ScriptValue resultValue = call(text, arg, context, reuseParentConfig);
        context.env.callCache.put(text, resultValue, context.config);
        context.logger.debug("cached callonce: {}", text);
        return resultValue;
    }

    private static ScriptValue eval(String text, ScriptContext context, boolean reuseParentConfig) {
        text = StringUtils.trimToEmpty(text);
        if (text.isEmpty()) {
            return ScriptValue.NULL;
        }
        boolean callOnce = isCallOnceSyntax(text);
        if (callOnce || isCallSyntax(text)) { // special case in form "call foo arg"
            if (callOnce) {
                text = text.substring(9);
            } else {
                text = text.substring(5);
            }
            int pos = text.indexOf(' '); // TODO handle read('file with spaces in the name')
            String arg;
            if (pos != -1) {
                arg = text.substring(pos);
                text = text.substring(0, pos);
            } else {
                arg = null;
            }
            if (callOnce) {
                return callWithCache(text, arg, context, reuseParentConfig);
            } else {
                return call(text, arg, context, reuseParentConfig);
            }
        } else if (isGetSyntax(text)) { // special case in form
            // get json[*].path
            // get /xml/path
            // get xpath-function(expression)
            text = text.substring(4);
            String left;
            String right;
            if (isVariableAndSpaceAndPath(text)) {
                int pos = text.indexOf(' ');
                right = text.substring(pos + 1);
                left = text.substring(0, pos);
            } else {
                Pair<String, String> pair = parseVariableAndPath(text);
                left = pair.getLeft();
                right = pair.getRight();
            }
            if (isXmlPath(right) || isXmlPathFunction(right)) {
                return evalXmlPathOnVarByName(left, right, context);
            } else {
                return evalJsonPathOnVarByName(left, right, context);
            }
        } else if (isJsonPath(text)) {
            return evalJsonPathOnVarByName(ScriptValueMap.VAR_RESPONSE, text, context);
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
        } else if (isStringExpression(text)) { // has to be above variableAndXml/JsonPath because of / in URL-s etc
            return evalInNashorn(text, context);
        } else if (isVariableAndJsonPath(text)) {
            Pair<String, String> pair = parseVariableAndPath(text);
            return evalJsonPathOnVarByName(pair.getLeft(), pair.getRight(), context);
        } else if (isVariableAndXmlPath(text)) {
            Pair<String, String> pair = parseVariableAndPath(text);
            return evalXmlPathOnVarByName(pair.getLeft(), pair.getRight(), context);
        } else {
            // js expressions e.g. foo, foo(bar), foo.bar, foo + bar, 5, true
            // including function declarations e.g. function() { }
            return evalInNashorn(text, context);
        }
    }

    public static ScriptValue evalXmlPathOnVarByName(String name, String path, ScriptContext context) {
        ScriptValue value = context.vars.get(name);
        if (value == null) {
            context.logger.warn("no var found with name: {}", name);
            return ScriptValue.NULL;
        }
        switch (value.getType()) {
            case XML:
                Node doc = value.getValue(Node.class);
                return evalXmlPathOnXmlNode(doc, path);
            default:
                Node node = XmlUtils.fromMap(value.getAsMap());
                return evalXmlPathOnXmlNode(node, path);
        }
    }

    public static ScriptValue evalXmlPathOnXmlNode(Node doc, String path) {
        NodeList nodeList;
        try {
            nodeList = XmlUtils.getNodeListByPath(doc, path);
        } catch (Exception e) {
            // hack, this happens for xpath functions that don't return nodes (e.g. count)
            String strValue = XmlUtils.getTextValueByPath(doc, path);
            return new ScriptValue(strValue);
        }
        int count = nodeList.getLength();
        if (count == 0) {
            return ScriptValue.NULL;
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

    public static ScriptValue evalJsonPathOnVarByName(String name, String exp, ScriptContext context) {
        ScriptValue value = context.vars.get(name);
        if (value == null) {
            context.logger.warn("no var found with name: {}", name);
            return ScriptValue.NULL;
        }
        switch (value.getType()) {
            case JSON:
                DocumentContext jsonDoc = value.getValue(DocumentContext.class);
                return new ScriptValue(jsonDoc.read(exp));
            case MAP: // this happens because some jsonpath expressions evaluate to Map
                Map<String, Object> map = value.getValue(Map.class);
                DocumentContext mapDoc = JsonPath.parse(map);
                return new ScriptValue(mapDoc.read(exp));
            case LIST: // this happens because some jsonpath expressions evaluate to List
                List list = value.getValue(List.class);
                DocumentContext listDoc = JsonPath.parse(list);
                return new ScriptValue(listDoc.read(exp));
            case XML: // time to auto-convert again
                Document xml = value.getValue(Document.class);
                DocumentContext xmlDoc = XmlUtils.toJsonDoc(xml);
                return new ScriptValue(xmlDoc.read(exp));
            case STRING:
                String str = value.getValue(String.class);
                DocumentContext strDoc = JsonPath.parse(str);
                return new ScriptValue(strDoc.read(exp));
            default:
                throw new RuntimeException("cannot run jsonpath on type: " + value);
        }
    }

    public static ScriptValue evalInNashorn(String exp, ScriptContext context) {
        return evalInNashorn(exp, context, null, null);
    }

    public static ScriptValue evalInNashorn(String exp, ScriptContext context, ScriptValue selfValue, ScriptValue parentValue) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine nashorn = manager.getEngineByName("nashorn");
        Bindings bindings = nashorn.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
        if (context != null) {
            Map<String, Object> map = context.getVariableBindings();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue());
            }
            bindings.put(ScriptContext.KARATE_NAME, new ScriptBridge(context));
        }
        if (selfValue != null) {
            bindings.put(VAR_SELF, selfValue.getValue());
        }
        if (parentValue != null) {
            bindings.put(VAR_DOLLAR, parentValue.getAfterConvertingFromJsonOrXmlIfNeeded());
        }
        try {
            Object o = nashorn.eval(exp);
            ScriptValue result = new ScriptValue(o);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("javascript evaluation failed: " + exp, e);
        }
    }

    public static ScriptValueMap clone(ScriptValueMap vars) {
        ScriptValueMap temp = new ScriptValueMap();
        for (Map.Entry<String, ScriptValue> entry : vars.entrySet()) {
            String key = entry.getKey();
            ScriptValue value = entry.getValue(); // TODO immutable / copy
            temp.put(key, value);
        }
        return temp;
    }

    public static Map<String, Object> simplify(ScriptValueMap vars) {
        Map<String, Object> map = new HashMap<>(vars.size() + 1); // 1 extra for the read function
        for (Map.Entry<String, ScriptValue> entry : vars.entrySet()) {
            String key = entry.getKey();
            ScriptValue sv = entry.getValue();
            if (sv == null) {
                continue;
            }
            map.put(key, sv.getAfterConvertingFromJsonOrXmlIfNeeded());
        }
        return map;
    }

    private static final String VARIABLE_PATTERN_STRING = "[a-zA-Z][\\w]*";

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE_PATTERN_STRING);

    public static boolean isValidVariableName(String name) {
        return VARIABLE_PATTERN.matcher(name).matches();
    }

    public static void evalJsonEmbeddedExpressions(DocumentContext doc, ScriptContext context) {
        Object o = doc.read("$");
        evalJsonEmbeddedExpressions("$", o, context, doc);
    }

    private static void evalJsonEmbeddedExpressions(String path, Object o, ScriptContext context, DocumentContext root) {
        if (o == null) {
            return;
        }
        if (o instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) o;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String childPath = JsonUtils.buildPath(path, entry.getKey());
                evalJsonEmbeddedExpressions(childPath, entry.getValue(), context, root);
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
            value = StringUtils.trim(value);
            if (isEmbeddedExpression(value)) {
                try {
                    ScriptValue sv = evalInNashorn(value.substring(1), context);
                    root.set(path, sv.getValue());
                } catch (Exception e) {
                    context.logger.warn("embedded json script eval failed at path {}: {}", path, e.getMessage());
                }
            }
        }
    }

    public static void evalXmlEmbeddedExpressions(Node node, ScriptContext context) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        NamedNodeMap attribs = node.getAttributes();
        int attribCount = attribs.getLength();
        for (int i = 0; i < attribCount; i++) {
            Attr attrib = (Attr) attribs.item(i);
            String value = attrib.getValue();
            value = StringUtils.trimToEmpty(value);
            if (isEmbeddedExpression(value)) {
                try {
                    ScriptValue sv = evalInNashorn(value.substring(1), context);
                    attrib.setValue(sv.getAsString());
                } catch (Exception e) {
                    context.logger.warn("embedded xml-attribute script eval failed: {}", e.getMessage());
                }
            }
        }
        NodeList nodes = node.getChildNodes();
        int childCount = nodes.getLength();
        for (int i = 0; i < childCount; i++) {
            Node child = nodes.item(i);
            String value = child.getNodeValue();
            if (value != null) {
                value = StringUtils.trimToEmpty(value);
                if (isEmbeddedExpression(value)) {
                    try {
                        ScriptValue sv = evalInNashorn(value.substring(1), context);
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
                            evalNode = node.getOwnerDocument().importNode(evalNode, true);
                            child.getParentNode().replaceChild(evalNode, child);
                        } else {
                            child.setNodeValue(sv.getAsString());
                        }
                    } catch (Exception e) {
                        context.logger.warn("embedded xml-text script eval failed: {}", e.getMessage());
                    }
                }
            } else if (child.hasChildNodes()) {
                evalXmlEmbeddedExpressions(child, context);
            }
        }
    }

    public static void assign(String name, String exp, ScriptContext context) {
        assign(AssignType.AUTO, name, exp, context);
    }

    public static void assignText(String name, String exp, ScriptContext context) {
        assign(AssignType.TEXT, name, exp, context);
    }

    public static void assignYaml(String name, String exp, ScriptContext context) {
        assign(AssignType.YAML, name, exp, context);
    }

    public static void assignString(String name, String exp, ScriptContext context) {
        assign(AssignType.STRING, name, exp, context);
    }

    public static void assignJson(String name, String exp, ScriptContext context) {
        assign(AssignType.JSON, name, exp, context);
    }

    public static void assignXml(String name, String exp, ScriptContext context) {
        assign(AssignType.XML, name, exp, context);
    }

    public static void assignXmlString(String name, String exp, ScriptContext context) {
        assign(AssignType.XML_STRING, name, exp, context);
    }

    private static void assign(AssignType assignType, String name, String exp, ScriptContext context) {
        name = StringUtils.trim(name);
        if (!isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        if ("request".equals(name) || "url".equals(name)) {
            throw new RuntimeException("'" + name + "' is not a variable, use the form '* " + name + " " + exp + "' instead");
        }
        ScriptValue sv;
        switch (assignType) {
            case TEXT:
                exp = exp.replace("\n", "\\n");
                if (!isQuoted(exp)) {
                    exp = "'" + exp + "'";
                }
                sv = evalInNashorn(exp, context);
                break;
            case YAML:
                DocumentContext doc = JsonUtils.fromYaml(exp);
                evalJsonEmbeddedExpressions(doc, context);
                sv = new ScriptValue(doc);
                break;
            case STRING:
                ScriptValue tempString = eval(exp, context);
                sv = new ScriptValue(tempString.getAsString());
                break;
            case JSON:
                ScriptValue tempJson = eval(exp, context);
                if (tempJson.getType() == STRING) {
                    sv = eval(tempJson.getValue(String.class), context);
                } else if (tempJson.isListLike()) {
                    DocumentContext listDoc = JsonPath.parse(tempJson.getAsList());
                    sv = new ScriptValue(listDoc);
                } else if (tempJson.isMapLike()) {
                    DocumentContext mapDoc = JsonPath.parse(tempJson.getAsMap());
                    sv = new ScriptValue(mapDoc);
                } else {
                    throw new RuntimeException("cannot convert to json: " + tempJson);
                }
                break;
            case XML:
                sv = toXml(exp, context);
                break;
            case XML_STRING:
                ScriptValue tempXml = toXml(exp, context);
                sv = new ScriptValue(tempXml.getAsString());
                break;
            default: // AUTO
                sv = eval(exp, context);
        }
        context.vars.put(name, sv);
    }

    private static ScriptValue toXml(String exp, ScriptContext context) {
        ScriptValue tempXml = eval(exp, context);
        if (tempXml.getType() == STRING) {
            return eval(tempXml.getValue(String.class), context);
        } else if (tempXml.isMapLike()) {
            return new ScriptValue(XmlUtils.fromMap(tempXml.getAsMap()));
        } else {
            throw new RuntimeException("cannot convert to xml: " + tempXml);
        }
    }

    public static boolean isQuoted(String exp) {
        return exp.startsWith("'") || exp.startsWith("\"");
    }

    public static AssertionResult matchNamed(String name, String path, String expected, ScriptContext context) {
        return matchNamed(MatchType.EQUALS, name, path, expected, context);
    }

    public static AssertionResult matchNamed(MatchType matchType, String name, String path, String expected, ScriptContext context) {
        name = StringUtils.trim(name);
        if (isJsonPath(name) || isXmlPath(name)) { // short-cut for operating on response
            path = name;
            name = ScriptValueMap.VAR_RESPONSE;
        }
        path = StringUtils.trimToNull(path);
        if (path == null) {
            Pair<String, String> pair = parseVariableAndPath(name);
            name = pair.getLeft();
            path = pair.getRight();
        }
        expected = StringUtils.trim(expected);
        if ("header".equals(name)) { // convenience shortcut for asserting against response header
            return matchNamed(matchType, ScriptValueMap.VAR_RESPONSE_HEADERS, "$['" + path + "'][0]", expected, context);
        } else {
            ScriptValue actual = context.vars.get(name);
            if (actual == null) {
                throw new RuntimeException("variable not initialized: " + name);
            }
            switch (actual.getType()) {
                case STRING:
                case INPUT_STREAM:
                    return matchString(matchType, actual, expected, path, context);
                case XML:
                    if ("$".equals(path)) {
                        path = "/"; // whole document, also edge case where variable name was 'response'
                    }
                // break; 
                // fall through to JSON. yes, dot notation can be used on XML !!
                default:
                    if (isJsonPath(path)) {
                        return matchJsonPath(matchType, actual, path, expected, context);
                    } else { // xpath
                        if (actual.getType() != XML) { // force conversion to xml
                            Node node = XmlUtils.fromMap(actual.getAsMap());
                            actual = new ScriptValue(node);
                        }
                        return matchXmlPath(matchType, actual, path, expected, context);
                    }
            }
        }
    }

    public static AssertionResult matchString(MatchType matchType, ScriptValue actual, String expected, String path, ScriptContext context) {
        ScriptValue expectedValue = eval(expected, context);
        expected = expectedValue.getAsString();
        return matchStringOrPattern('*', path, matchType, null, actual, expected, context);
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

    public static AssertionResult matchStringOrPattern(char delimiter, String path, MatchType stringMatchType, Object actRoot,
            ScriptValue actValue, String expected, ScriptContext context) {
        if (expected == null) {
            if (!actValue.isNull()) {
                return matchFailed(path, actValue.getValue(), expected, "actual value is not null");
            }
        } else if (isMacro(expected)) {
            String macroExpression;
            if (isOptionalMacro(expected)) {
                if (actValue.isNull()) {
                    return AssertionResult.PASS;
                }
                macroExpression = expected.substring(2);
            } else {
                macroExpression = expected.substring(1);
            }
            if (isWithinParantheses(macroExpression)) { // '#(foo)' | '##(foo)' | '#(^foo)'
                MatchType matchType = MatchType.EQUALS;
                macroExpression = stripParentheses(macroExpression);
                if (isContainsMacro(macroExpression)) {
                    matchType = MatchType.CONTAINS;
                    macroExpression = macroExpression.substring(1);
                } else if (isNotContainsMacro(macroExpression)) {
                    matchType = MatchType.NOT_CONTAINS;
                    macroExpression = macroExpression.substring(2);
                }
                ScriptValue parentValue = getValueOfParentNode(actRoot, path);
                ScriptValue expValue = evalInNashorn(macroExpression, context, actValue, parentValue);
                return matchNestedObject(delimiter, path, matchType, actRoot, actValue.getValue(), expValue.getValue(), context);
            }
            if (macroExpression.startsWith("regex")) {
                String regex = macroExpression.substring(5).trim();
                RegexValidator v = new RegexValidator(regex);
                ValidationResult vr = v.validate(actValue);
                if (!vr.isPass()) { // TODO wrap string values in quotes
                    return matchFailed(path, actValue.getValue(), expected, vr.getMessage());
                }
            } else if (macroExpression.startsWith("[") && macroExpression.indexOf(']') > 0) {
                // check if array
                ValidationResult vr = ArrayValidator.INSTANCE.validate(actValue);
                if (!vr.isPass()) {
                    return matchFailed(path, actValue.getValue(), expected, vr.getMessage());
                }
                int endBracketPos = macroExpression.indexOf(']');
                List actValueList = actValue.getAsList();
                if (endBracketPos > 1) {
                    int arrayLength = actValueList.size();
                    String bracketContents = macroExpression.substring(1, endBracketPos);
                    ScriptValue parentValue = getValueOfParentNode(actRoot, path);
                    String expression;
                    if (bracketContents.indexOf('_') != -1) { // #[_ < 5]  
                        expression = bracketContents;
                    } else { // #[5] | #[$.foo] 
                        expression = bracketContents + " == " + arrayLength;
                    }
                    ScriptValue result = Script.evalInNashorn(expression, context, new ScriptValue(arrayLength), parentValue);
                    if (!result.isBooleanTrue()) {
                        return matchFailed(path, actValue.getValue(), expected, "array length expression did not evaluate to 'true'");
                    }
                }
                if (macroExpression.length() > endBracketPos + 1) { // expression
                    // macro-fy before attempting to re-use match-each routine
                    String expression = macroExpression.substring(endBracketPos + 1);
                    expression = StringUtils.trimToNull(expression);
                    MatchType matchType = MatchType.EACH_EQUALS;
                    if (expression != null) {
                        if (expression.startsWith("?")) {
                            expression = "'#" + expression + "'";
                        } else if (expression.startsWith("#")) {
                            expression = "'" + expression + "'";
                        } else {
                            if (isWithinParantheses(expression)) {
                                expression = stripParentheses(expression);
                            }
                            if (isContainsMacro(expression)) {
                                matchType = MatchType.EACH_CONTAINS;
                                expression = expression.substring(1);
                            } else if (isNotContainsMacro(expression)) {
                                matchType = MatchType.EACH_NOT_CONTAINS;
                                expression = expression.substring(2);                                
                            }
                        }
                        // actRoot assumed to be json in this case                        
                        return matchJsonPath(matchType, new ScriptValue(actRoot), path, expression, context);
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
                    Validator v = context.validators.get(validatorName);
                    if (v == null) {
                        return matchFailed(path, actValue.getValue(), expected, "unknown validator");
                    } else {
                        ValidationResult vr = v.validate(actValue);
                        if (!vr.isPass()) {
                            return matchFailed(path, actValue.getValue(), expected, vr.getMessage());
                        }
                    }
                }
                if (questionPos != -1) {
                    macroExpression = macroExpression.substring(questionPos + 1);
                    ScriptValue parentValue = getValueOfParentNode(actRoot, path);
                    ScriptValue result = Script.evalInNashorn(macroExpression, context, actValue, parentValue);
                    if (!result.isBooleanTrue()) {
                        return matchFailed(path, actValue.getValue(), expected, "did not evaluate to 'true'");
                    }
                }
            }
        } else {
            String actual = actValue.getAsString();
            switch (stringMatchType) {
                case CONTAINS:
                    if (!actual.contains(expected)) {
                        return matchFailed(path, actual, expected, "not a sub-string");
                    }
                    break;
                case EQUALS:
                    if (!expected.equals(actual)) {
                        return matchFailed(path, actual, expected, "not equal");
                    }
                    break;
                default:
                    throw new RuntimeException("unsupported match type for string: " + stringMatchType);
            }
        }
        return AssertionResult.PASS;
    }

    private static ScriptValue getValueOfParentNode(Object actRoot, String path) {
        if (actRoot instanceof DocumentContext) {
            Pair<String, String> parentAndLeaf = JsonUtils.getParentAndLeafPath(path);
            DocumentContext actDoc = (DocumentContext) actRoot;
            Object thisObject;
            if ("".equals(parentAndLeaf.getLeft())) { // edge case, this IS the root
                thisObject = actDoc;
            } else {
                thisObject = actDoc.read(parentAndLeaf.getLeft());
            }
            return new ScriptValue(thisObject);
        } else {
            return null;
        }
    }

    public static AssertionResult matchXmlPath(MatchType matchType, ScriptValue actual, String path, String expression, ScriptContext context) {
        Node node = actual.getValue(Node.class);
        actual = evalXmlPathOnXmlNode(node, path);
        ScriptValue expected = eval(expression, context);
        Object actObject;
        Object expObject;
        switch (expected.getType()) {
            case XML: // convert to map and then compare               
                Node expNode = expected.getValue(Node.class);
                expObject = XmlUtils.toObject(expNode);
                actObject = XmlUtils.toObject(actual.getValue(Node.class));
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
                actObject = actual.getAsString();
                expObject = expected.getAsString();
        }
        if ("/".equals(path)) {
            path = ""; // else error x-paths reported would start with "//"
        }
        return matchNestedObject('/', path, matchType, node, actObject, expObject, context);
    }

    private static MatchType getInnerMatchType(MatchType outerMatchType) {
        switch (outerMatchType) {
            case EACH_CONTAINS:
                return MatchType.CONTAINS;
            case EACH_NOT_CONTAINS:
                return MatchType.NOT_CONTAINS;
            case EACH_CONTAINS_ONLY:
                return MatchType.CONTAINS_ONLY;
            case EACH_EQUALS:
                return MatchType.EQUALS;
            default:
                throw new RuntimeException("unexpected outer match type: " + outerMatchType);
        }
    }

    public static AssertionResult matchJsonPath(MatchType matchType, ScriptValue actual, String path, String expression, ScriptContext context) {
        DocumentContext actualDoc;
        switch (actual.getType()) {
            case JSON:
                actualDoc = actual.getValue(DocumentContext.class);
                break;
            case JS_ARRAY: // happens for json resulting from nashorn
                ScriptObjectMirror som = actual.getValue(ScriptObjectMirror.class);
                actualDoc = JsonPath.parse(som.values());
                break;
            case JS_OBJECT: // is a map-like object, happens for json resulting from nashorn
            case MAP: // this happens because some jsonpath operations result in Map
                Map<String, Object> map = actual.getValue(Map.class);
                actualDoc = JsonPath.parse(map);
                break;
            case LIST: // this also happens because some jsonpath operations result in List
                List list = actual.getValue(List.class);
                actualDoc = JsonPath.parse(list);
                break;
            case XML: // auto convert !
                actualDoc = XmlUtils.toJsonDoc(actual.getValue(Node.class));
                break;
            case STRING: // an edge case when the variable is a plain string not JSON, so switch to plain string compare
                String actualString = actual.getValue(String.class);
                ScriptValue expectedString = eval(expression, context);
                // exit the function early
                if (!expectedString.isString()) {
                    return matchFailed(path, actualString, expectedString.getValue(),
                            "type of actual value is 'string' but that of expected is " + expectedString.getType());
                } else {
                    return matchStringOrPattern('.', path, matchType, null, actual, expectedString.getValue(String.class), context);
                }
            case PRIMITIVE:
                return matchPrimitive(path, actual.getValue(), eval(expression, context).getValue());
            case NULL: // edge case, assume that this is the root variable that is null and the match is for an optional e.g. '##string'
                ScriptValue expectedNull = eval(expression, context);
                if (expectedNull.isNull()) {
                    return AssertionResult.PASS;
                }
                if (!expectedNull.isString()) {
                    return matchFailed(path, null, expectedNull.getValue(), "actual value is null but expected is " + expectedNull);
                } else {
                    return matchStringOrPattern('.', path, matchType, null, actual, expectedNull.getValue(String.class), context);
                }
            default:
                throw new RuntimeException("not json, cannot do json path for value: " + actual + ", path: " + path);
        }
        Object actObject = actualDoc.read(path);
        ScriptValue expected = eval(expression, context);
        Object expObject;
        switch (expected.getType()) {
            case JSON: // convert to map or list
                expObject = expected.getValue(DocumentContext.class).read("$");
                break;
            case JS_ARRAY: // array returned by js function, needs conversion to list
                ScriptObjectMirror som = expected.getValue(ScriptObjectMirror.class);
                expObject = new ArrayList(som.values());
                break;
            default:
                expObject = expected.getValue();
        }
        switch (matchType) {
            case CONTAINS:
            case NOT_CONTAINS:
            case CONTAINS_ONLY:
                if (actObject instanceof List && !(expObject instanceof List)) { // if RHS is not a list, make it so
                    expObject = Collections.singletonList(expObject);
                }
            case EQUALS:
                return matchNestedObject('.', path, matchType, actualDoc, actObject, expObject, context);
            case EACH_CONTAINS:
            case EACH_NOT_CONTAINS:
            case EACH_CONTAINS_ONLY:
            case EACH_EQUALS:
                if (actObject instanceof List) {
                    List actList = (List) actObject;
                    MatchType listMatchType = getInnerMatchType(matchType);
                    int actSize = actList.size();
                    for (int i = 0; i < actSize; i++) {
                        Object actListObject = actList.get(i);
                        String listPath = path + "[" + i + "]";
                        AssertionResult ar = matchNestedObject('.', listPath, listMatchType, actualDoc, actListObject, expObject, context);
                        if (!ar.pass) {
                            return ar;
                        }
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
        } else {
            return o;
        }
    }

    public static AssertionResult matchFailed(String path, Object actObject, Object expObject, String reason) {
        if (path.startsWith("/")) {
            String leafName = getLeafNameFromXmlPath(path);
            actObject = toXmlString(leafName, actObject);
            expObject = toXmlString(leafName, expObject);
            path = path.replace("/@/", "/@");
        }
        String message = String.format("path: %s, actual: %s, expected: %s, reason: %s", path, actObject, expObject, reason);
        return AssertionResult.fail(message);
    }

    public static AssertionResult matchNestedObject(char delimiter, String path, MatchType matchType,
            Object actRoot, Object actObject, Object expObject, ScriptContext context) {
        if (expObject == null) {
            if (actObject != null) {
                return matchFailed(path, actObject, expObject, "actual value is not null");
            }
            return AssertionResult.PASS; // both are null
        }
        if (expObject instanceof String) {
            ScriptValue actValue = new ScriptValue(actObject);
            return matchStringOrPattern(delimiter, path, matchType, actRoot, actValue, expObject.toString(), context);
        }
        if (actObject == null) {
            return matchFailed(path, actObject, expObject, "actual value is null");
        }
        if (expObject instanceof Map) {
            if (!(actObject instanceof Map)) {
                return matchFailed(path, actObject, expObject, "actual value is not of type 'map'");
            }
            Map<String, Object> expMap = (Map) expObject;
            Map<String, Object> actMap = (Map) actObject;
            if ((matchType == MatchType.EQUALS || matchType == MatchType.CONTAINS_ONLY) && actMap.size() > expMap.size()) { // > is because of the chance of #ignore
                int sizeDiff = actMap.size() - expMap.size();
                Map<String, Object> diffMap = new LinkedHashMap(actMap);
                for (String key : expMap.keySet()) {
                    diffMap.remove(key);
                }
                return matchFailed(path, actObject, expObject, "actual value has " + sizeDiff + " more key(s) than expected: " + diffMap);
            }
            for (Map.Entry<String, Object> expEntry : expMap.entrySet()) {
                String key = expEntry.getKey();
                String childPath = delimiter == '.' ? JsonUtils.buildPath(path, key) : path + delimiter + key;
                Object childAct = actMap.get(key);
                Object childExp = expEntry.getValue();
                AssertionResult ar = matchNestedObject(delimiter, childPath, MatchType.EQUALS, actRoot, childAct, childExp, context);
                if (ar.pass && matchType == MatchType.NOT_CONTAINS) {
                    return matchFailed(childPath, childAct, childExp, "actual value contains expected");
                } else if (!ar.pass && matchType != MatchType.NOT_CONTAINS) {
                    return ar;
                }
            }
            return AssertionResult.PASS; // map compare done
        } else if (expObject instanceof List) {
            List expList = (List) expObject;
            List actList = (List) actObject;
            int actCount = actList.size();
            int expCount = expList.size();
            if ((matchType == MatchType.EQUALS || matchType == MatchType.CONTAINS_ONLY) && actCount != expCount) {
                return matchFailed(path, actObject, expObject, "actual and expected arrays are not the same size - " + actCount + ":" + expCount);
            }
            if (matchType == MatchType.CONTAINS
                    || matchType == MatchType.CONTAINS_ONLY
                    || matchType == MatchType.NOT_CONTAINS) { // just checks for existence (or non-existence)
                for (Object expListObject : expList) { // for each expected item in the list
                    boolean found = false;
                    for (int i = 0; i < actCount; i++) {
                        Object actListObject = actList.get(i);
                        String listPath = buildListPath(delimiter, path, i);
                        AssertionResult ar = matchNestedObject(delimiter, listPath, MatchType.EQUALS, actRoot, actListObject, expListObject, context);
                        if (ar.pass) { // exact match, we found it
                            found = true;
                            break;
                        }
                    }
                    if (found && matchType == MatchType.NOT_CONTAINS) {
                        return matchFailed(path + "[*]", actObject, expListObject, "actual value contains expected");
                    } else if (!found && matchType != MatchType.NOT_CONTAINS) {
                        return matchFailed(path + "[*]", actObject, expListObject, "actual value does not contain expected");
                    }
                }
                return AssertionResult.PASS; // all items were found
            } else { // exact compare of list elements and order
                for (int i = 0; i < expCount; i++) {
                    Object expListObject = expList.get(i);
                    Object actListObject = actList.get(i);
                    String listPath = buildListPath(delimiter, path, i);
                    AssertionResult ar = matchNestedObject(delimiter, listPath, MatchType.EQUALS, actRoot, actListObject, expListObject, context);
                    if (!ar.pass) {
                        return matchFailed(listPath, actListObject, expListObject, "[" + ar.message + "]");
                    }
                }
                return AssertionResult.PASS; // lists (and order) are identical
            }
        } else if (ClassUtils.isPrimitiveOrWrapper(expObject.getClass())) {
            return matchPrimitive(path, actObject, expObject);
        } else if (expObject instanceof BigDecimal) {
            BigDecimal expNumber = (BigDecimal) expObject;
            if (actObject instanceof BigDecimal) {
                BigDecimal actNumber = (BigDecimal) actObject;
                if (actNumber.compareTo(expNumber) != 0) {
                    return matchFailed(path, actObject, expObject, "not equal (big decimal)");
                }
            } else {
                BigDecimal actNumber = convertToBigDecimal(actObject);
                if (actNumber == null || actNumber.compareTo(expNumber) != 0) {
                    return matchFailed(path, actObject, expObject, "not equal (primitive : big decimal)");
                }
            }
            return AssertionResult.PASS;
        } else { // this should never happen
            throw new RuntimeException("unexpected type: " + expObject.getClass());
        }
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

    private static AssertionResult matchPrimitive(String path, Object actObject, Object expObject) {
        if (actObject == null) {
            return matchFailed(path, actObject, expObject, "actual value is null");
        }
        if (!expObject.getClass().equals(actObject.getClass())) {
            if (actObject instanceof BigDecimal) {
                BigDecimal actNumber = (BigDecimal) actObject;
                BigDecimal expNumber = convertToBigDecimal(expObject);
                if (expNumber == null || expNumber.compareTo(actNumber) != 0) {
                    return matchFailed(path, actObject, expObject, "not equal (big decimal : primitive)");
                } else {
                    return AssertionResult.PASS;
                }
            } else {
                // types are not the same, use the JS engine for a lenient equality check
                String exp = actObject + " == " + expObject;
                ScriptValue sv = evalInNashorn(exp, null);
                if (sv.isBooleanTrue()) {
                    return AssertionResult.PASS;
                } else {
                    return matchFailed(path, actObject, expObject, "not equal");
                }
            }
        }
        if (!expObject.equals(actObject)) {
            return matchFailed(path, actObject, expObject, "not equal");
        } else {
            return AssertionResult.PASS; // primitives, are equal
        }
    }

    public static void removeValueByPath(String name, String path, ScriptContext context) {
        setValueByPath(name, path, null, true, context);
    }

    public static void setValueByPath(String name, String path, String exp, ScriptContext context) {
        setValueByPath(name, path, exp, false, context);
    }

    public static void setValueByPath(String name, String path, String exp, boolean delete, ScriptContext context) {
        name = StringUtils.trim(name);
        path = StringUtils.trimToNull(path);
        if (path == null) {
            Pair<String, String> nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.getLeft();
            path = nameAndPath.getRight();
        }
        if ("request".equals(name) || "url".equals(name)) {
            throw new RuntimeException("'" + name + "' is not a variable,"
                    + " use the form '* " + name + " <expression>' to initialize the "
                    + name + ", and <expression> can be a variable");
        }
        ScriptValue value = delete ? ScriptValue.NULL : eval(exp, context);
        if (isJsonPath(path)) {
            ScriptValue target = context.vars.get(name);
            switch (target.getType()) {
                case JSON:
                    DocumentContext dc = target.getValue(DocumentContext.class);
                    JsonUtils.setValueByPath(dc, path, value.getAfterConvertingFromJsonOrXmlIfNeeded(), delete);
                    break;
                case MAP:
                    Map<String, Object> map = target.getValue(Map.class);
                    DocumentContext fromMap = JsonPath.parse(map);
                    JsonUtils.setValueByPath(fromMap, path, value.getAfterConvertingFromJsonOrXmlIfNeeded(), delete);
                    context.vars.put(name, fromMap);
                    break;
                default:
                    throw new RuntimeException("cannot set json path on unexpected type: " + target);
            }
        } else if (isXmlPath(path)) {
            Document doc = context.vars.get(name, Document.class);
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

    public static ScriptValue call(String name, String argString, ScriptContext context, boolean reuseParentConfig) {
        ScriptValue argValue = eval(argString, context);
        ScriptValue sv = eval(name, context);
        switch (sv.getType()) {
            case JS_FUNCTION:
                switch (argValue.getType()) {
                    case JSON:
                        // force to java map (or list)
                        argValue = new ScriptValue(argValue.getValue(DocumentContext.class).read("$"));
                    case JS_ARRAY:
                    case JS_OBJECT:
                    case LIST:
                    case STRING:
                    case PRIMITIVE:
                    case NULL:
                        break;
                    default:
                        throw new RuntimeException("only json or primitives allowed as (single) function call argument");
                }
                ScriptObjectMirror som = sv.getValue(ScriptObjectMirror.class);
                return evalFunctionCall(som, argValue.getValue(), context);
            case FEATURE_WRAPPER:
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
                    case JS_OBJECT:
                        callArg = argValue.getValue(ScriptObjectMirror.class);
                        break;
                    case JS_ARRAY:
                        ScriptObjectMirror temp = argValue.getValue(ScriptObjectMirror.class);
                        callArg = temp.values();
                        break;
                    case NULL:
                        break;
                    default:
                        throw new RuntimeException("only json, list/array or map allowed as feature call argument");
                }
                FeatureWrapper feature = sv.getValue(FeatureWrapper.class);
                return evalFeatureCall(feature, callArg, context, reuseParentConfig);
            default:
                context.logger.warn("not a js function or feature file: {} - {}", name, sv);
                return ScriptValue.NULL;
        }
    }

    public static ScriptValue evalFunctionCall(ScriptObjectMirror som, Object callArg, ScriptContext context) {
        // ensure that things like 'karate.get' operate on the latest variable state
        som.setMember(ScriptContext.KARATE_NAME, new ScriptBridge(context));
        // convenience for users, can use 'karate' instead of 'this.karate'
        som.eval(String.format("var %s = this.%s", ScriptContext.KARATE_NAME, ScriptContext.KARATE_NAME));
        Object result;
        try {
            if (callArg != null) {
                result = som.call(som, callArg);
            } else {
                result = som.call(som);
            }
            return new ScriptValue(result);
        } catch (Exception e) {
            String message = "javascript function call failed, arg: " + callArg + "\n" + som;
            context.logger.error(message, e);
            throw new KarateException(message, e);
        }
    }

    public static ScriptValue evalFeatureCall(FeatureWrapper feature, Object callArg, ScriptContext context, boolean reuseParentConfig) {
        if (callArg instanceof Collection) { // JSON array
            Collection items = (Collection) callArg;
            Object[] array = items.toArray();
            List result = new ArrayList(array.length);
            for (int i = 0; i < array.length; i++) {
                Object rowArg = array[i];
                if (rowArg instanceof Map) {
                    try {
                        ScriptValue rowResult = evalFeatureCall(feature, context, (Map) rowArg, reuseParentConfig);
                        result.add(rowResult.getValue());
                    } catch (KarateException ke) {
                        String message = "loop feature call failed in " + feature.getEnv() + ", index: " + i + ", arg: " + rowArg + ", items: " + items;
                        throw new KarateException(message, ke);
                    }
                } else {
                    throw new RuntimeException("argument not json or map for feature call loop array position: " + i + ", " + rowArg);
                }
            }
            return new ScriptValue(result);
        } else if (callArg == null || callArg instanceof Map) {
            try {
                return evalFeatureCall(feature, context, (Map) callArg, reuseParentConfig);
            } catch (KarateException ke) {
                String message = "feature call failed in " + feature.getEnv() + ", arg: " + callArg;
                context.logger.error(message, ke);
                throw new KarateException(message, ke);
            }
        } else {
            throw new RuntimeException("unexpected feature call arg type: " + callArg.getClass());
        }
    }

    private static ScriptValue evalFeatureCall(FeatureWrapper feature, ScriptContext context,
            Map<String, Object> callArg, boolean reuseParentConfig) {
        ScriptValueMap svm = CucumberUtils.call(feature, context, callArg, reuseParentConfig);
        Map<String, Object> map = simplify(svm);
        return new ScriptValue(map);
    }

    public static void callAndUpdateConfigAndAlsoVarsIfMapReturned(boolean callOnce, String name, String arg, ScriptContext context) {
        ScriptValue sv;
        if (callOnce) {
            sv = callWithCache(name, arg, context, true);
        } else {
            sv = call(name, arg, context, true);
        }
        Map<String, Object> result;
        switch (sv.getType()) {
            case JS_OBJECT:
            case MAP:
                result = sv.getValue(Map.class);
                break;
            default:
                context.logger.trace("no vars returned from function call result: {}", sv);
                return;
        }
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            context.vars.put(entry.getKey(), entry.getValue());
        }
    }

    public static Map<String, Validator> getDefaultValidators() {
        Map<String, Validator> map = new HashMap<>();
        map.put("ignore", IgnoreValidator.INSTANCE);
        map.put("null", NullValidator.INSTANCE);
        map.put("notnull", NotNullValidator.INSTANCE);
        map.put("uuid", UuidValidator.INSTANCE);
        map.put("string", StringValidator.INSTANCE);
        map.put("number", NumberValidator.INSTANCE);
        map.put("boolean", BooleanValidator.INSTANCE);
        map.put("array", ArrayValidator.INSTANCE);
        map.put("object", ObjectValidator.INSTANCE);
        return map;
    }

    public static AssertionResult assertBoolean(String expression, ScriptContext context) {
        ScriptValue result = Script.evalInNashorn(expression, context);
        if (!result.isBooleanTrue()) {
            return AssertionResult.fail("assert evaluated to false: " + expression);
        }
        return AssertionResult.PASS;
    }

    public static String replacePlaceholderText(String text, String token, String replaceWith, ScriptContext context) {
        if (text == null) {
            return null;
        }
        replaceWith = StringUtils.trimToNull(replaceWith);
        if (replaceWith == null) {
            return text;
        }
        try {
            ScriptValue sv = eval(replaceWith, context);
            replaceWith = sv.getAsString();
        } catch (Exception e) {
            throw new RuntimeException("expression error (replace string values need to be within quotes): " + e.getMessage());
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

    public static String replacePlaceholders(String text, List<Map<String, String>> list, ScriptContext context) {
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

    public static List<Map<String, Object>> evaluateExpressions(List<Map<String, Object>> list, ScriptContext context) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Map<String, Object> map : list) {
            Map<String, Object> row = new LinkedHashMap<>(map);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object o = entry.getValue();
                if (o instanceof String) {
                    ScriptValue sv = eval((String) o, context);
                    entry.setValue(sv.getAsString());
                }
            }
            result.add(row);
        }
        return result;
    }

}
