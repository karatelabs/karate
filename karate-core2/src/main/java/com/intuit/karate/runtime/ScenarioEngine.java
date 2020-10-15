/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.runtime;

import com.intuit.karate.AssignType;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchType;
import com.intuit.karate.match.MatchValue;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Value;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author pthomas3
 */
public class ScenarioEngine {

    private final ScenarioRuntime runtime;
    private final Logger logger;
    public final Map<String, Variable> vars;
    private JsEngine JS;

    public ScenarioEngine(ScenarioRuntime runtime, Map<String, Variable> vars, Logger logger) {
        this.runtime = runtime;
        this.vars = vars;
        this.logger = logger;
    }

    // not in constructor because it has to be on Runnable.run() thread
    public void init() {
        JS = JsEngine.local();
        attachJsValuesToContext();
    }

    private void attachJsValuesToContext() {
        Map<String, Variable> updated = new HashMap();
        vars.forEach((k, v) -> {
            switch (v.type) {
                case FUNCTION: // variables are immutable, so replace
                    Value fun = JS.attachToContext(v.getValue());
                    updated.put(k, new Variable(fun));
                    break;
                case MAP:
                case LIST:
                    recurse(v.getValue());
                    break;
                default:
                // do nothing
            }
        });
        vars.putAll(updated);
    }

    private Object recurse(Object o) {
        // do this check first as graal functions are maps as well
        if (o instanceof Function) {
            return JS.attachToContext(o);
        } else if (o instanceof List) {
            List list = (List) o;
            int count = list.size();
            for (int i = 0; i < count; i++) {
                Object child = list.get(i);
                Object result = recurse(child);
                if (result != null) {
                    list.set(i, result);
                }
            }
            return null;
        } else if (o instanceof Map) {
            Map<String, Object> map = (Map) o;
            map.forEach((k, v) -> {
                Object result = recurse(v);
                if (result != null) {
                    map.put(k, result);
                }
            });
            return null;
        } else {
            return null;
        }
    }

    public Variable eval(String exp) {
        vars.forEach((k, v) -> JS.put(k, v.getValue()));
        try {
            return new Variable(JS.eval(exp));
        } catch (Exception e) {
            // do our best to make js error traces informative, else thrown exception seems to
            // get swallowed by the java reflection based method invoke flow
            if (runtime.jsEvalError == null) {
                StackTraceElement[] stack = e.getStackTrace();
                StringBuilder sb = new StringBuilder();
                sb.append(">>>> js failed:\n");
                List<String> lines = StringUtils.toStringLines(exp);
                int index = 0;
                for (String line : lines) {
                    sb.append(String.format("%02d", ++index)).append(": ").append(line).append('\n');
                }
                sb.append("<<<<\n");
                sb.append(e.toString()).append('\n');
                for (int i = 0; i < stack.length; i++) {
                    String line = stack[i].toString();
                    if (line.startsWith("org.graalvm.polyglot.Context.eval")) {
                        break;
                    }
                    sb.append("- ").append(line).append('\n');
                }
                runtime.jsEvalError = sb.toString();
            }
            throw e;
        }
    }

    public void setHiddenVariable(String key, Object value) {
        if (value instanceof Variable) {
            value = ((Variable) value).getValue();
        }
        JS.put(key, value);
    }

    public void setVariable(String key, Object value) {
        if (value instanceof Variable) {
            vars.put(key, (Variable) value);
        } else {
            vars.put(key, new Variable(value));
        }
    }

    public void setVariables(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        map.forEach((k, v) -> setVariable(k, v));
    }

    public Map<String, Variable> copyVariables(boolean deep) {
        Map<String, Variable> map = new HashMap(vars.size());
        vars.forEach((k, v) -> map.put(k, v == null ? Variable.NULL : v.copy(deep)));
        return map;
    }

    public Map<String, Object> getAllVariablesAsMap() {
        Map<String, Object> map = new HashMap(vars.size());
        vars.forEach((k, v) -> map.put(k, v == null ? null : v.getValue()));
        return map;
    }

    private static void validateVariableName(String name) {
        if (!isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        if (VariableNames.KARATE.equals(name)) {
            throw new RuntimeException("'karate' is a reserved name");
        }
        if (VariableNames.REQUEST.equals(name) || "url".equals(name)) {
            throw new RuntimeException("'" + name + "' is a reserved name, also use the form '* " + name + " <expression>' instead");
        }
    }

    private Variable evalAndCastTo(AssignType assignType, String exp) {
        Variable v = evalKarateExpression(exp);
        switch (assignType) {
            case BYTE_ARRAY:
                return new Variable(v.getAsByteArray());
            case STRING:
                return new Variable(v.getAsString());
            case XML:
                return new Variable(v.getAsXml());
            case XML_STRING:
                String xml = XmlUtils.toString(v.getAsXml());
                return new Variable(xml);
            case JSON:
                return new Variable(v.getValueAndForceParsingAsJson());
            case YAML:
                return new Variable(JsonUtils.fromYaml(v.getAsString()));
            case CSV:
                return new Variable(JsonUtils.fromCsv(v.getAsString()));
            case COPY:
                return v.copy(true);
            default: // AUTO (TEXT is pre-handled)
                return v; // as is
        }
    }

    public void assign(AssignType assignType, String name, String exp, boolean validateName) {
        name = StringUtils.trimToEmpty(name);
        if (validateName) {
            validateVariableName(name);
            if (vars.containsKey(name)) {
                logger.warn("over-writing existing variable {} with new value: {}", name, exp);
            }
        }
        if (assignType == AssignType.TEXT) {
            setVariable(name, exp);
        } else {
            setVariable(name, evalAndCastTo(assignType, exp));
        }
    }

    public boolean assertTrue(String expression) {
        return eval(expression).isTrue();
    }

    private static boolean isEmbeddedExpression(String text) {
        return text != null && (text.startsWith("#(") || text.startsWith("##(")) && text.endsWith(")");
    }

    private static class EmbedAction {

        final boolean remove;
        final Object value;

        private EmbedAction(boolean remove, Object value) {
            this.remove = remove;
            this.value = value;
        }

        static EmbedAction remove() {
            return new EmbedAction(true, null);
        }

        static EmbedAction update(Object value) {
            return new EmbedAction(false, value);
        }

    }

    public Variable evalEmbeddedExpressions(Variable value) {
        switch (value.type) {
            case STRING:
            case MAP:
            case LIST:
                EmbedAction ea = recurseEmbeddedExpressions(value);
                if (ea != null) {
                    return ea.remove ? Variable.NULL : new Variable(ea.value);
                } else {
                    return value;
                }
            case XML:
                recurseXmlEmbeddedExpressions(value.getValue());
            default:
                return value;
        }
    }

    private EmbedAction recurseEmbeddedExpressions(Variable node) {
        switch (node.type) {
            case LIST:
                List list = node.getValue();
                Set<Integer> indexesToRemove = new HashSet();
                int count = list.size();
                for (int i = 0; i < count; i++) {
                    EmbedAction ea = recurseEmbeddedExpressions(new Variable(list.get(i)));
                    if (ea != null) {
                        if (ea.remove) {
                            indexesToRemove.add(i);
                        } else {
                            list.set(i, ea.value);
                        }
                    }
                }
                if (!indexesToRemove.isEmpty()) {
                    List copy = new ArrayList(count - indexesToRemove.size());
                    for (int i = 0; i < count; i++) {
                        if (!indexesToRemove.contains(i)) {
                            copy.add(list.get(i));
                        }
                    }
                    return EmbedAction.update(copy);
                } else {
                    return null;
                }
            case MAP:
                Map<String, Object> map = node.getValue();
                List<String> keysToRemove = new ArrayList();
                map.forEach((k, v) -> {
                    EmbedAction ea = recurseEmbeddedExpressions(new Variable(v));
                    if (ea != null) {
                        if (ea.remove) {
                            keysToRemove.add(k);
                        } else {
                            map.put(k, ea.value);
                        }
                    }
                });
                for (String key : keysToRemove) {
                    map.remove(key);
                }
                return null;
            case XML:
                return null;
            case STRING:
                String value = StringUtils.trimToNull(node.getValue());
                if (!isEmbeddedExpression(value)) {
                    return null;
                }
                boolean remove = value.charAt(1) == '#';
                value = value.substring(remove ? 2 : 1);
                Variable result = eval(value);
                return remove && result.isNull() ? EmbedAction.remove() : EmbedAction.update(result.getValue());
            default:
                // do nothing
                return null;
        }
    }

    private void recurseXmlEmbeddedExpressions(Node node) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        NamedNodeMap attribs = node.getAttributes();
        int attribCount = attribs == null ? 0 : attribs.getLength();
        Set<Attr> attributesToRemove = new HashSet(attribCount);
        for (int i = 0; i < attribCount; i++) {
            Attr attrib = (Attr) attribs.item(i);
            String value = attrib.getValue();
            value = StringUtils.trimToNull(value);
            if (isEmbeddedExpression(value)) {
                boolean optional = value.charAt(1) == '#';
                value = value.substring(optional ? 2 : 1);
                try {
                    Variable v = eval(value);
                    if (optional && v.isNull()) {
                        attributesToRemove.add(attrib);
                    } else {
                        attrib.setValue(v.getAsString());
                    }
                } catch (Exception e) {
                    logger.trace("embedded xml-attribute eval failed, path: {}, reason: {}", attrib.getName(), e.getMessage());
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
                    boolean optional = value.charAt(1) == '#';
                    value = value.substring(optional ? 2 : 1);
                    try {
                        Variable v = eval(value);
                        if (optional && v.isNull()) {
                            elementsToRemove.add(child);
                        } else {
                            if (v.isXml() || v.isMap()) {
                                Node evalNode = v.isXml() ? v.getValue() : XmlUtils.fromMap(v.getValue());
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
                                child.setNodeValue(v.getAsString());
                            }
                        }
                    } catch (Exception e) {
                        logger.trace("embedded xml-text eval failed, path: {}, reason: {}", child.getNodeName(), e.getMessage());
                    }
                }
            } else if (child.hasChildNodes() || child.hasAttributes()) {
                recurseXmlEmbeddedExpressions(child);
            }
        }
        for (Node toRemove : elementsToRemove) { // because of how the above routine works, these are always of type TEXT_NODE
            Node parent = toRemove.getParentNode(); // element containing the text-node
            Node grandParent = parent.getParentNode(); // parent element
            grandParent.removeChild(parent);
        }
    }

    private String getVarAsString(String name) {
        Variable v = vars.get(name);
        if (v == null) {
            throw new RuntimeException("no variable found with name: " + name);
        }
        return v.getAsString();
    }

    public String replacePlaceholderText(String text, String token, String replaceWith) {
        if (text == null) {
            return null;
        }
        replaceWith = StringUtils.trimToNull(replaceWith);
        if (replaceWith == null) {
            return text;
        }
        try {
            Variable v = evalKarateExpression(replaceWith);
            replaceWith = v.getAsString();
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

    public void replace(String name, String token, String value) {
        name = name.trim();
        String text = getVarAsString(name);
        String replaced = replacePlaceholderText(text, token, value);
        vars.put(name, new Variable(replaced));
    }

    private static final String TOKEN = "token";

    public void replaceTable(String text, List<Map<String, String>> list) {
        if (text == null) {
            return;
        }
        if (list == null) {
            return;
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
                replace(text, token, value);
            }
        }

    }

    public void remove(String name, String path) {
        set(name, path, null, true, false);
    }

    public void set(String name, String path, String exp) {
        set(name, path, exp, false, false);
    }

    public void set(String name, String path, Variable value) {
        set(name, path, false, value, false, false);
    }

    private void set(String name, String path, String exp, boolean delete, boolean viaTable) {
        set(name, path, isWithinParentheses(exp), evalKarateExpression(exp), delete, viaTable);
    }

    private void set(String name, String path, boolean isWithinParentheses, Variable value, boolean delete, boolean viaTable) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path);
        if (viaTable && value.isNull() && !isWithinParentheses) {
            // by default, skip any expression that evaluates to null unless the user expressed
            // intent to over-ride by enclosing the expression in parentheses
            return;
        }
        if (path == null) {
            StringUtils.Pair nameAndPath = parseVariableAndPath(name);
            name = nameAndPath.left;
            path = nameAndPath.right;
        }
        Variable target = vars.get(name);
        if (isDollarPrefixedJsonPath(path)) {
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    Json json;
                    if (path.startsWith("$[") && !path.startsWith("$['")) {
                        json = new Json("[]");
                    } else {
                        json = new Json("{}");
                    }
                    target = new Variable(json.asMapOrList());
                    vars.put(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            Json json;
            if (target.isMap()) {
                json = new Json(target.<Map>getValue());
            } else if (target.isList()) {
                json = new Json(target.<List>getValue());
            } else {
                throw new RuntimeException("cannot set json path on type: " + target);
            }
            if (delete) {
                json.remove(path);
            } else {
                json.set(path, value.<Object>getValue());
            }
        } else if (isXmlPath(path)) {
            if (target == null || target.isNull()) {
                if (viaTable) { // auto create if using set via cucumber table as a convenience
                    Document empty = XmlUtils.newDocument();
                    target = new Variable(empty);
                    vars.put(name, target);
                } else {
                    throw new RuntimeException("variable is null or not set '" + name + "'");
                }
            }
            Document doc = target.getValue();
            if (delete) {
                XmlUtils.removeByPath(doc, path);
            } else if (value.isXml()) {
                Node node = value.getValue();
                XmlUtils.setByPath(doc, path, node);
            } else if (value.isMap()) { // cast to xml
                Node node = XmlUtils.fromMap(value.getValue());
                XmlUtils.setByPath(doc, path, node);
            } else {
                XmlUtils.setByPath(doc, path, value.getAsString());
            }
        } else {
            throw new RuntimeException("unexpected path: " + path);
        }
    }

    private static final String PATH = "path";

    public void setViaTable(String name, String path, List<Map<String, String>> list) {
        name = StringUtils.trimToEmpty(name);
        path = StringUtils.trimToNull(path);
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
                    // and users can over-ride this by simply enclosing the expression in parentheses
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
                set(name, finalPath, expression, false, true);
            }
        }
    }

    public void print(String line) {
        eval("karate.log('[print]'," + line + ")");
    }

    public void table(String name, List<Map<String, String>> rows) {
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, String> map : rows) {
            Map<String, Object> row = new LinkedHashMap<>(map);
            List<String> toRemove = new ArrayList(map.size());
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String exp = (String) entry.getValue();
                Variable sv = evalKarateExpression(exp);
                if (sv.isNull() && !isWithinParentheses(exp)) { // by default empty / null will be stripped, force null like this: '(null)'
                    toRemove.add(entry.getKey());
                } else {
                    if (sv.isString()) {
                        entry.setValue(sv.getAsString());
                    } else { // map, list etc
                        entry.setValue(sv.getValue());
                    }
                }
            }
            for (String keyToRemove : toRemove) {
                row.remove(keyToRemove);
            }
            result.add(row);
        }
        vars.put(name.trim(), new Variable(result));
    }

    public static StringUtils.Pair parseVariableAndPath(String text) {
        Matcher matcher = VAR_AND_PATH_PATTERN.matcher(text);
        matcher.find();
        String name = text.substring(0, matcher.end());
        String path;
        if (matcher.end() == text.length()) {
            path = "";
        } else {
            path = text.substring(matcher.end()).trim();
        }
        if (isXmlPath(path) || isXmlPathFunction(path)) {
            // xml, don't prefix for json
        } else {
            path = "$" + path;
        }
        return StringUtils.pair(name, path);
    }

    public MatchResult match(MatchType matchType, String expression, String path, String rhs) {
        String name = StringUtils.trimToEmpty(expression);
        if (isDollarPrefixedJsonPath(name) || isXmlPath(name)) { // 
            path = name;
            name = VariableNames.RESPONSE;
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
        if ("header".equals(name)) { // convenience shortcut for asserting against response header
            return match(matchType, VariableNames.RESPONSE_HEADERS, "$['" + path + "'][0]", rhs);
        }
        Variable actual;
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
            actual = evalKarateExpression(name);
            // edge case: java property getter, e.g. "driver.cookies"
            if (!actual.isMap() && !actual.isList() && !isXmlPath(path) && !isXmlPathFunction(path)) {
                actual = evalKarateExpression(expression); // fall back to JS eval of entire LHS
                path = "$";
            }
        } else {
            actual = evalKarateExpression(expression); // JS eval of entire LHS
            path = "$";
        }
        if ("$".equals(path) || "/".equals(path)) {
            // we have eval-ed the entire LHS, so proceed to match RHS to "$"
        } else {
            if (isDollarPrefixed(path)) { // json-path
                actual = evalJsonPath(actual, path);
            } else { // xpath
                actual = evalXmlPath(actual, path);
            }
        }
        Variable expected = evalKarateExpression(rhs);
        return match(matchType, actual.getValue(), expected.getValue());
    }

    public MatchResult match(MatchType matchType, Object actual, Object expected) {
        return Match.execute(JS, matchType, new MatchValue(actual), new MatchValue(expected));
    }

    private static final Pattern VAR_AND_PATH_PATTERN = Pattern.compile("\\w+");
    private static final String VARIABLE_PATTERN_STRING = "[a-zA-Z][\\w]*";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE_PATTERN_STRING);
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("^function[^(]*\\(");

    public static boolean isJavaScriptFunction(String text) {
        return FUNCTION_PATTERN.matcher(text).find();
    }

    public static String fixJavaScriptFunction(String text) {
        Matcher matcher = FUNCTION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.replaceFirst("function(");
        } else {
            return text;
        }
    }

    public static boolean isValidVariableName(String name) {
        return VARIABLE_PATTERN.matcher(name).matches();
    }

    public static final boolean isVariableAndSpaceAndPath(String text) {
        return text.matches("^" + VARIABLE_PATTERN_STRING + "\\s+.+");
    }

    public static final boolean isVariable(String text) {
        return VARIABLE_PATTERN.matcher(text).matches();
    }

    public static final boolean isWithinParentheses(String text) {
        return text != null && text.startsWith("(") && text.endsWith(")");
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

    public static final boolean isXml(String text) {
        return text.startsWith("<");
    }

    public static boolean isXmlPath(String text) {
        return text.startsWith("/");
    }

    public static boolean isXmlPathFunction(String text) {
        return text.matches("^[a-z-]+\\(.+");
    }

    public static final boolean isJsonPath(String text) {
        return text.indexOf('*') != -1 || text.contains("..") || text.contains("[?");
    }

    public static final boolean isDollarPrefixed(String text) {
        return text.startsWith("$");
    }

    public static final boolean isDollarPrefixedJsonPath(String text) {
        return text.startsWith("$.") || text.startsWith("$[") || text.equals("$");
    }

    public static StringUtils.Pair parseCallArgs(String line) {
        int pos = line.indexOf("read(");
        if (pos != -1) {
            pos = line.indexOf(')');
            if (pos == -1) {
                throw new RuntimeException("failed to parse call arguments: " + line);
            }
            return new StringUtils.Pair(line.substring(0, pos + 1), StringUtils.trimToNull(line.substring(pos + 1)));
        }
        pos = line.indexOf(' ');
        if (pos == -1) {
            return new StringUtils.Pair(line, null);
        }
        return new StringUtils.Pair(line.substring(0, pos), StringUtils.trimToNull(line.substring(pos)));
    }

    public Variable call(Variable called, Variable arg, boolean sharedScope) {
        switch (called.type) {
            case FUNCTION:
                return arg == null ? called.invokeFunction() : called.invokeFunction(new Object[]{arg.getValue()});
            case FEATURE:
                return callFeature(called.getValue(), arg, -1, sharedScope);
            default:
                throw new RuntimeException("not a callable feature or js function: " + called);
        }
    }

    public Variable call(boolean callOnce, String exp, boolean sharedScope) {
        StringUtils.Pair pair = parseCallArgs(exp);
        Variable called = evalKarateExpression(pair.left);
        Variable arg = pair.right == null ? null : evalKarateExpression(pair.right);
        if (callOnce) {
            return callOnce(exp, called, arg, sharedScope);
        } else {
            return call(called, arg, sharedScope);
        }
    }

    private Variable result(ScenarioCall.Result result, boolean sharedScope) {
        if (sharedScope) { // if shared scope
            runtime.configure(new Config(result.config)); // re-apply config from time of snapshot
            if (result.vars != null) {
                // don't clear vars because karate-config.js or background or scenario outline may have set some !
                // vars.clear(); 
                vars.putAll(copyVariables(false)); // clone for safety 
            }
        }
        return result.value.copy(false); // clone result for safety       
    }

    private Variable callOnce(String cacheKey, Variable called, Variable arg, boolean sharedScope) {
        // IMPORTANT: the call result is always shallow-cloned before returning
        // so that call result (especially if a java Map) is not mutated by other scenarios
        final Map<String, ScenarioCall.Result> CACHE = runtime.featureRuntime.FEATURE_CACHE;
        ScenarioCall.Result result = CACHE.get(cacheKey);
        if (result != null) {
            runtime.logger.trace("callonce cache hit for: {}", cacheKey);
            return result(result, sharedScope);
        }
        long startTime = System.currentTimeMillis();
        runtime.logger.trace("callonce waiting for lock: {}", cacheKey);
        synchronized (CACHE) {
            result = CACHE.get(cacheKey); // retry
            if (result != null) {
                long endTime = System.currentTimeMillis() - startTime;
                runtime.logger.warn("this thread waited {} milliseconds for callonce lock: {}", endTime, cacheKey);
                return result(result, sharedScope);
            }
            // this thread is the 'winner'
            runtime.logger.info(">> lock acquired, begin callonce: {}", cacheKey);
            Variable resultValue = call(called, arg, sharedScope);
            // we clone result (and config) here, to snapshot state at the point the callonce was invoked
            // this prevents the state from being clobbered by the subsequent steps of this
            // first scenario that is about to use the result
            Map<String, Variable> clonedVars = called.isFeature() && sharedScope ? copyVariables(false) : null;
            result = new ScenarioCall.Result(resultValue.copy(false), new Config(runtime.getConfig()), clonedVars);
            CACHE.put(cacheKey, result);
            runtime.logger.info("<< lock released, cached callonce: {}", cacheKey);
            return resultValue; // another routine will apply globally if needed
        }
    }

    public Variable callFeature(Feature feature, Variable arg, int index, boolean sharedScope) {
        if (arg == null || arg.isMap()) {
            ScenarioCall call = new ScenarioCall(runtime, feature);
            call.setArg(arg);
            call.setLoopIndex(index);
            call.setSharedScope(sharedScope);
            FeatureRuntime fr = new FeatureRuntime(call);
            fr.run();
            if (fr.result.isFailed()) {
                KarateException ke = fr.result.getErrorsCombined();
                if (index == -1) {
                    runtime.logError(ke.getMessage());
                }
                throw ke;
            } else {
                return fr.getResultVariable();
            }
        } else if (arg.isList() || arg.isFunction()) {
            List result = new ArrayList();
            List<String> errors = new ArrayList();
            int loopIndex = 0;
            boolean isList = arg.isList();
            Iterator iterator = isList ? arg.<List>getValue().iterator() : null;
            while (true) {
                Variable loopArg;
                if (isList) {
                    loopArg = iterator.hasNext() ? new Variable(iterator.next()) : Variable.NULL;
                } else { // function
                    loopArg = arg.invokeFunction(loopIndex);
                }
                if (!loopArg.isMap()) {
                    if (!isList) {
                        logger.info("feature call loop function ended at index {}, returned: {}", loopIndex, loopArg);
                    }
                    break;
                }
                try {
                    Variable loopResult = callFeature(feature, loopArg, loopIndex, sharedScope);
                    result.add(loopResult.getValue());
                } catch (Exception e) {
                    String message = "feature call loop failed at index: " + loopIndex + ", " + e.getMessage();
                    errors.add(message);
                    runtime.logError(message);
                    if (!isList) { // this is a generator function, abort infinite loop !
                        break;
                    }
                }
                loopIndex++;
            }
            if (errors.isEmpty()) {
                return new Variable(result);
            } else {
                String errorMessage = StringUtils.join(errors, '\n');
                throw new KarateException(errorMessage);
            }
        } else {
            throw new RuntimeException("feature call argument is not a json object or array: " + arg);
        }
    }

    public Variable evalJsonPath(Variable v, String path) {
        Json json = new Json(v.getValueAndForceParsingAsJson());
        try {
            return new Variable(json.get(path));
        } catch (PathNotFoundException e) {
            return Variable.NOT_PRESENT;
        }
    }

    public static Variable evalXmlPath(Variable xml, String path) {
        NodeList nodeList;
        Node doc = xml.getAsXml();
        try {
            nodeList = XmlUtils.getNodeListByPath(doc, path);
        } catch (Exception e) {
            // hack, this happens for xpath functions that don't return nodes (e.g. count)
            String strValue = XmlUtils.getTextValueByPath(doc, path);
            Variable v = new Variable(strValue);
            if (path.startsWith("count")) { // special case
                return new Variable(v.getAsInt());
            } else {
                return v;
            }
        }
        int count = nodeList.getLength();
        if (count == 0) { // xpath / node does not exist !
            return Variable.NOT_PRESENT;
        }
        if (count == 1) {
            return nodeToValue(nodeList.item(0));
        }
        List list = new ArrayList();
        for (int i = 0; i < count; i++) {
            Variable v = nodeToValue(nodeList.item(i));
            list.add(v.getValue());
        }
        return new Variable(list);
    }

    private static Variable nodeToValue(Node node) {
        int childElementCount = XmlUtils.getChildElementCount(node);
        if (childElementCount == 0) {
            // hack assuming this is the most common "intent"
            return new Variable(node.getTextContent());
        }
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return new Variable(node);
        } else { // make sure we create a fresh doc else future xpath would run against original root
            return new Variable(XmlUtils.toNewDocument(node));
        }
    }

    public Variable evalJsonPathOnVariableByName(String name, String path) {
        return evalJsonPath(vars.get(name), path);
    }

    public Variable evalXmlPathOnVariableByName(String name, String path) {
        return evalXmlPath(vars.get(name), path);
    }

    public Variable evalKarateExpression(String text) {
        text = StringUtils.trimToNull(text);
        if (text == null) {
            return Variable.NULL;
        }
        // don't re-evaluate if this is clearly a direct reference to a variable
        // this avoids un-necessary conversion of xml into a map in some cases
        // e.g. 'Given request foo' - where foo is a Variable of type XML      
        if (vars.containsKey(text)) {
            return vars.get(text);
        }
        boolean callOnce = isCallOnceSyntax(text);
        if (callOnce || isCallSyntax(text)) { // special case in form "callBegin foo arg"
            if (callOnce) {
                text = text.substring(9);
            } else {
                text = text.substring(5);
            }
            return call(callOnce, text, false);
        } else if (isDollarPrefixedJsonPath(text)) {
            return evalJsonPathOnVariableByName(VariableNames.RESPONSE, text);
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
                left = VariableNames.RESPONSE;
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
            Variable sv;
            if (isXmlPath(right) || isXmlPathFunction(right)) {
                sv = evalXmlPathOnVariableByName(left, right);
            } else {
                sv = evalJsonPathOnVariableByName(left, right);
            }
            if (index != -1 && sv.isList()) {
                List list = sv.getValue();
                if (!list.isEmpty()) {
                    return new Variable(list.get(index));
                }
            }
            return sv;
        } else if (isJson(text)) {
            Json json = new Json(text);
            return evalEmbeddedExpressions(new Variable(json.asMapOrList()));
        } else if (isXml(text)) {
            Document doc = XmlUtils.toXmlDoc(text);
            return evalEmbeddedExpressions(new Variable(doc));
        } else if (isXmlPath(text)) {
            return evalXmlPathOnVariableByName(VariableNames.RESPONSE, text);
        } else {
            // old school function declarations e.g. function() { } need wrapping in graal
            if (isJavaScriptFunction(text)) {
                text = "(" + text + ")";
            }
            // js expressions e.g. foo, foo(bar), foo.bar, foo + bar, foo + '', 5, true
            // including arrow functions e.g. x => x + 1
            return eval(text);
        }
    }

}
