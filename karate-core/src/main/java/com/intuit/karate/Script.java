package com.intuit.karate;

import static com.intuit.karate.ScriptValue.Type.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public static final String VAR_CONTEXT = "_context";
    public static final String VAR_SELF = "_";

    private Script() {
        // only static methods
    }

    private static final Logger logger = LoggerFactory.getLogger(Script.class);

    public static final boolean isCallSyntax(String text) {
        return text.startsWith("call ");
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

    public static final boolean isEmbeddedExpression(String text) {
        return text.startsWith("#(") && text.endsWith(")");
    }

    public static final boolean isJsonPath(String text) {
        return text.startsWith("$.") || text.startsWith("$[") || text.equals("$");
    }

    public static final boolean isVariable(String text) {
        return VARIABLE_PATTERN.matcher(text).matches();
    }

    public static final boolean isVariableAndJsonPath(String text) {
        return !text.endsWith(")") && text.indexOf('.') != -1 && text.matches("^[^.^/][^\\s^/]+");
    }

    public static final boolean isVariableAndXmlPath(String text) {
        return text.matches("^[^\\s^/]+/[^\\s]*");
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
        if (!path.startsWith("/")) {
            path = "$" + path;
        }
        return Pair.of(name, path);
    }

    public static ScriptValue preEval(String text, ScriptContext context) {
        text = StringUtils.trimToEmpty(text);
        if (text.isEmpty()) {
            logger.trace("script is empty");
            return ScriptValue.NULL;
        }
        if (isCallSyntax(text)) { // special case in form "call foo arg"
            text = text.substring(5);
            int pos = text.indexOf(' ');
            String arg;
            if (pos != -1) {
                arg = text.substring(pos);
                text = text.substring(0, pos);
            } else {
                arg = null;
            }
            return call(text, arg, context);
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
            return eval(text, context);
        } else if (isVariableAndJsonPath(text)) {
            Pair<String, String> pair = parseVariableAndPath(text);
            return evalJsonPathOnVarByName(pair.getLeft(), pair.getRight(), context);
        } else if (isVariableAndXmlPath(text)) {
            Pair<String, String> pair = parseVariableAndPath(text);
            return evalXmlPathOnVarByName(pair.getLeft(), pair.getRight(), context);
        } else {
            // js expressions e.g. foo, foo(bar), foo.bar, foo + bar, 5, true
            // including function declarations e.g. function() { }
            return eval(text, context);
        }
    }

    public static ScriptValue evalXmlPathOnVarByName(String name, String exp, ScriptContext context) {
        ScriptValue value = context.vars.get(name);
        if (value == null) {
            logger.warn("no var found with name: {}", name);
            return ScriptValue.NULL;
        }
        switch (value.getType()) {
            case XML:
                Node doc = value.getValue(Node.class);
                String strVal = XmlUtils.getValueByPath(doc, exp);
                if (strVal != null) { // hack assuming this is the most common "intent"
                    return new ScriptValue(strVal);
                } else {
                    Node node = XmlUtils.getNodeByPath(doc, exp);
                    return new ScriptValue(node);
                }
            default:
                throw new RuntimeException("cannot run xpath on type: " + value);
        }
    }

    public static ScriptValue evalJsonPathOnVarByName(String name, String exp, ScriptContext context) {
        ScriptValue value = context.vars.get(name);
        if (value == null) {
            logger.warn("no var found with name: {}", name);
            return ScriptValue.NULL;
        }
        switch (value.getType()) {
            case JSON:
                DocumentContext doc = value.getValue(DocumentContext.class);
                return new ScriptValue(doc.read(exp));
            case MAP: // this happens because some jsonpath expressions evaluate to Map
                Map<String, Object> map = value.getValue(Map.class);
                DocumentContext fromMap = JsonPath.parse(map);
                return new ScriptValue(fromMap.read(exp));
            default:
                throw new RuntimeException("cannot run jsonpath on type: " + value);
        }
    }
    
    public static ScriptValue eval(String exp, ScriptContext context) {
        return eval(exp, context, null);
    }

    public static ScriptValue eval(String exp, ScriptContext context, ScriptValue self) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine nashorn = manager.getEngineByName("nashorn");
        Bindings bindings = nashorn.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
        Map<String, Object> simple = simplify(context.vars);
        for (Map.Entry<String, Object> entry : simple.entrySet()) {
            bindings.put(entry.getKey(), entry.getValue());
        }
        // for future function calls if needed, and see FileUtils.getFileReaderFunction()
        bindings.put(VAR_CONTEXT, context);
        if (self != null) {
            bindings.put(VAR_SELF, self.getValue());
        }
        try {
            Object o = nashorn.eval(exp);
            ScriptValue result = new ScriptValue(o);
            logger.trace("nashorn returned: {}", result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("script failed: " + exp, e);
        }
    }

    public static Map<String, Object> simplify(ScriptValueMap vars) {
        Map<String, Object> map = new HashMap<>(vars.size());
        for (Map.Entry<String, ScriptValue> entry : vars.entrySet()) {
            String key = entry.getKey();
            ScriptValue sv = entry.getValue();
            if (sv == null) {
                logger.warn("vars has null vaue for key: {}", key);
                continue;
            }
            map.put(key, sv.getAfterConvertingToMapIfNeeded());
        }
        return map;
    }

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("[a-zA-Z][\\w]*");

    public static boolean isValidVariableName(String name) {
        return VARIABLE_PATTERN.matcher(name).matches();
    }

    public static void evalJsonEmbeddedExpressions(DocumentContext doc, ScriptContext context) {
        Object o = doc.read("$");
        recurseJson("$", o, context, doc);
    }

    private static void recurseJson(String path, Object o, ScriptContext context, DocumentContext root) {
        if (o == null) {
            return;
        }
        if (o instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) o;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String childPath = path + "." + entry.getKey();
                recurseJson(childPath, entry.getValue(), context, root);
            }
        } else if (o instanceof List) {
            List list = (List) o;
            int size = list.size();
            for (int i = 0; i < size; i++) {
                Object child = list.get(i);
                String childPath = path + "[" + i + "]";
                recurseJson(childPath, child, context, root);
            }
        } else if (o instanceof String) {
            String value = (String) o;
            value = StringUtils.trim(value);
            if (isEmbeddedExpression(value)) {
                try {
                    ScriptValue sv = eval(value.substring(1), context);
                    root.set(path, sv.getValue());
                } catch (Exception e) {
                    logger.warn("embedded json script eval failed at path {}: {}", path, e.getMessage());
                }
            }
        } else {
            logger.trace("ignoring type: {} - {}", o.getClass(), o);
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
                    ScriptValue sv = eval(value.substring(1), context);
                    attrib.setValue(sv.getAsString());
                } catch (Exception e) {
                    logger.warn("embedded xml-attribute script eval failed: {}", e.getMessage());
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
                        ScriptValue sv = eval(value.substring(1), context);
                        child.setNodeValue(sv.getAsString());
                    } catch (Exception e) {
                        logger.warn("embedded xml-text script eval failed: {}", e.getMessage());
                    }
                }
            } else if (child.hasChildNodes()) {
                evalXmlEmbeddedExpressions(child, context);
            }
        }
    }

    public static void assign(String name, String exp, ScriptContext context) {
        name = StringUtils.trim(name);
        if (!isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        ScriptValue sv = preEval(exp, context);
        logger.trace("assigning {} = {} evaluated to {}", name, exp, sv);
        context.vars.put(name, sv);
    }

    public static boolean isStar(String exp) {
        return exp.equals("*");
    }

    public static boolean isQuoted(String exp) {
        return exp.startsWith("'") || exp.startsWith("\"");
    }

    public static AssertionResult matchNamed(String name, String path, String expected, ScriptContext context) {
        return matchNamed(false, name, path, expected, context);
    }

    public static AssertionResult matchNamed(boolean contains, String name, String path, String expected, ScriptContext context) {
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
            return matchNamed(contains, ScriptValueMap.VAR_RESPONSE_HEADERS, "$['" + path + "'][0]", expected, context);
        } else if (isStar(path)) {
            return matchString(contains, name, expected, path, context);
        } else if (isXmlPath(path)) {
            return matchXmlPath(contains, name, path, expected, context);
        } else {
            return matchJsonPath(contains, name, path, expected, context);
        }
    }

    public static AssertionResult matchString(boolean contains, String name, String expected, String path, ScriptContext context) {
        ScriptValue actual = context.vars.get(name);
        ScriptValue expectedValue = preEval(expected, context);
        expected = expectedValue.getAsString();
        return matchStringOrPattern(contains, actual, expected, path, context);
    }

    public static boolean isValidator(String text) {
        return text.startsWith("#");
    }

    public static AssertionResult matchStringOrPattern(boolean contains, ScriptValue actValue, String expected, String path, ScriptContext context) {
        if (expected == null) {
            if (!actValue.isNull()) {
                return matchFailed(path, actValue.getValue(), expected);
            }
        } else if (isValidator(expected)) {
            String validatorName = expected.substring(1);
            if (validatorName.startsWith("regex")) {
                String regex = validatorName.substring(5);
                RegexValidator v = new RegexValidator(regex);
                ValidationResult vr = v.validate(actValue);
                if (!vr.isPass()) { // TODO wrap string values in quotes
                    return matchFailed(path, actValue.getValue(), expected + ", reason: " + vr.getMessage());
                }
            } else if (validatorName.startsWith("?")) {
                String exp = validatorName.substring(1);
                String result = eval(exp, context, actValue).getAsString();
                if (!"true".equals(result)) {
                    return matchFailed(path, actValue.getValue(), expected + ", reason: false");
                }
            } else {
                Validator v = context.validators.get(validatorName);
                if (v == null) {
                    return matchFailed(path, actValue.getValue(), expected + ", reason: (unknown validator)");
                } else {
                    ValidationResult vr = v.validate(actValue);
                    if (!vr.isPass()) {
                        return matchFailed(path, actValue.getValue(), expected + ", reason: " + vr.getMessage());
                    }
                }
            }
        } else {
            String actual = actValue.getAsString();
            if (contains) {
                if (!actual.contains(expected)) {
                    return matchFailed(path, actual, expected + " (not a sub-string)");
                }
            } else if (!expected.equals(actual)) {
                return matchFailed(path, actual, expected);
            }
        }
        return AssertionResult.PASS;
    }

    public static AssertionResult matchXmlObject(boolean contains, Object act, Object exp, ScriptContext context) {
        return matchNestedObject('/', "", contains, act, exp, context);
    }

    public static AssertionResult matchXmlPath(boolean contains, String name, String path, String expression, ScriptContext context) {
        ScriptValue actual = context.vars.get(name);
        if (actual.getType() != XML) {
            throw new RuntimeException("not xml, cannot do xml path for name: " + name + ", path: " + path);
        }
        Document actualDoc = actual.getValue(Document.class);
        Node actNode = XmlUtils.getNodeByPath(actualDoc, path);
        ScriptValue expected = preEval(expression, context);
        Object actObject;
        Object expObject;
        switch (expected.getType()) {
            case XML: // convert to map and then compare               
                Node expNode = expected.getValue(Node.class);
                expObject = XmlUtils.toMap(expNode);
                actObject = XmlUtils.toMap(actNode);
                break;
            default: // try string comparison
                actObject = new ScriptValue(actNode).getAsString();
                expObject = expected.getAsString();
        }
        return matchNestedObject('/', path, contains, actObject, expObject, context);
    }

    public static AssertionResult matchJsonPath(boolean contains, String name, String path, String expression, ScriptContext context) {
        DocumentContext actualDoc;
        ScriptValue actual = context.vars.get(name);
        switch (actual.getType()) {
            case JSON:
                actualDoc = actual.getValue(DocumentContext.class);
                break;
            case MAP: // this happens because some jsonpath operations result in Map
                Map<String, Object> map = actual.getValue(Map.class);
                actualDoc = JsonPath.parse(map);
                break;
            case XML: // auto convert !
                actualDoc = XmlUtils.toJsonDoc(actual.getValue(Node.class));
                break;
            case STRING: // an edge case when the variable is a plain string not JSON, so switch to plain string compare
                String actualString = actual.getValue(String.class);
                ScriptValue expected = preEval(expression, context);
                // exit the function early
                if (!expected.isString()) {
                    return matchFailed(path, actualString, expected.getValue());
                } else {
                    return matchStringOrPattern(contains, actual, expected.getValue(String.class), path, context);
                }
            default:
                throw new RuntimeException("not json, cannot do json path for name: " + name + ", path: " + path);
        }
        Object actObject = actualDoc.read(path);
        ScriptValue expected = preEval(expression, context);
        Object expObject;
        switch (expected.getType()) {
            case JSON:
                expObject = expected.getValue(DocumentContext.class).read("$");
                break;
            default:
                expObject = expected.getValue();
        }
        return matchNestedObject('.', path, contains, actObject, expObject, context);
    }

    public static AssertionResult matchJsonObject(Object act, Object exp, ScriptContext context) {
        return matchNestedObject('.', "$", false, act, exp, context);
    }

    public static AssertionResult matchJsonObject(boolean contains, Object act, Object exp, ScriptContext context) {
        return matchNestedObject('.', "$", contains, act, exp, context);
    }

    public static AssertionResult matchFailed(String path, Object actObject, Object expObject) {
        String message = String.format("path: %s, actual: %s, expected: %s", path, actObject, expObject);
        logger.trace("assertion failed - {}", message);
        return AssertionResult.fail(message);
    }

    public static AssertionResult matchNestedObject(char delimiter, String path, boolean contains, Object actObject, Object expObject, ScriptContext context) {
        logger.trace("path: {}, actual: '{}', expected: '{}'", path, actObject, expObject);
        if (expObject == null) {
            if (actObject != null) {
                return matchFailed(path, actObject, expObject);
            }
            return AssertionResult.PASS; // both are null
        }
        if (expObject instanceof String) {
            ScriptValue actValue = new ScriptValue(actObject);
            return matchStringOrPattern(contains, actValue, expObject.toString(), path, context);
        } else if (expObject instanceof Map) {
            if (!(actObject instanceof Map)) {
                return matchFailed(path, actObject, expObject);
            }
            Map<String, Object> expMap = (Map) expObject;
            Map<String, Object> actMap = (Map) actObject;
            if (!contains && actMap.size() > expMap.size()) { // > is because of the chance of #ignore
                return matchFailed(path, actObject, expObject);
            }
            for (Map.Entry<String, Object> expEntry : expMap.entrySet()) {
                String key = expEntry.getKey();
                String childPath = path + delimiter + key;
                AssertionResult ar = matchNestedObject(delimiter, childPath, Boolean.FALSE, actMap.get(key), expEntry.getValue(), context);
                if (!ar.pass) {
                    return ar;
                }
            }
            return AssertionResult.PASS; // map compare done
        } else if (expObject instanceof List) {
            List expList = (List) expObject;
            List actList = (List) actObject;
            int actCount = actList.size();
            int expCount = expList.size();
            if (!contains && actCount != expCount) {
                return matchFailed(path, actObject, expObject);
            }
            if (contains) { // just checks for existence
                for (Object expListObject : expList) { // for each expected item in the list
                    boolean found = false;
                    for (int i = 0; i < actCount; i++) {
                        Object actListObject = actList.get(i);
                        String listPath = path + "[" + i + "]";
                        AssertionResult ar = matchNestedObject(delimiter, listPath, Boolean.FALSE, actListObject, expListObject, context);
                        if (ar.pass) { // exact match, we found it
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return matchFailed(path, actObject, expObject + " (not contained in)");
                    }
                }
                return AssertionResult.PASS; // all items were found
            } else { // exact compare of list elements and order
                for (int i = 0; i < expCount; i++) {
                    Object expListObject = expList.get(i);
                    Object actListObject = actList.get(i);
                    String listPath = path + "[" + i + "]";
                    AssertionResult ar = matchNestedObject(delimiter, listPath, Boolean.FALSE, actListObject, expListObject, context);
                    if (!ar.pass) {
                        return matchFailed(path, actObject, expObject);
                    }
                }
                return AssertionResult.PASS; // lists (and order) are identical
            }
        } else if (ClassUtils.isPrimitiveOrWrapper(actObject.getClass())) {
            if (!expObject.equals(actObject)) {
                return matchFailed(path, actObject, expObject);
            } else {
                return AssertionResult.PASS; // primitives, are equal
            }
        } else { // this should never happen
            throw new RuntimeException("unexpected type: " + expObject.getClass());
        }
    }

    public static void setValueByPath(String name, String path, String exp, ScriptContext context) {
        name = StringUtils.trim(name);
        if (name.startsWith("$")) {
            name = name.substring(1);
        }
        path = StringUtils.trimToNull(path);
        if (path == null) {
            Pair<String, String> pair = parseVariableAndPath(name);
            name = pair.getLeft();
            path = pair.getRight();
        }
        if (isJsonPath(path)) {
            ScriptValue target = context.vars.get(name);
            ScriptValue value = preEval(exp, context);
            switch (target.getType()) {
                case JSON:
                    DocumentContext dc = target.getValue(DocumentContext.class);
                    JsonUtils.setValueByPath(dc, path, value.getValue());
                    break;
                case MAP:
                    Map<String, Object> map = target.getValue(Map.class);
                    DocumentContext fromMap = JsonPath.parse(map);
                    JsonUtils.setValueByPath(fromMap, path, value.getValue());
                    context.vars.put(name, fromMap);
                    break;
                default:
                    throw new RuntimeException("cannot set json path on unexpected type: " + target);
            }
        } else if (isXmlPath(path)) {
            Document doc = context.vars.get(name, Document.class);
            ScriptValue sv = preEval(exp, context);
            if (sv.getType() != STRING) {
                throw new RuntimeException("TODO set non-string XML values");
            }
            XmlUtils.setByPath(doc, path, sv.getAsString());
        } else {
            throw new RuntimeException("unexpected path: " + path);
        }
    }

    public static ScriptValue evalFunctionCall(ScriptObjectMirror som, ScriptValue argValue, ScriptContext context) {
        context.injectInto(som);
        Object result;
        if (argValue.getType() != NULL) {
            result = som.call(som, argValue.getValue());
        } else {
            result = som.call(som);
        }
        return new ScriptValue(result);
    }

    public static ScriptValue call(String name, String arg, ScriptContext context) {
        ScriptValue argValue = preEval(arg, context);
        switch (argValue.getType()) {
            case JSON:
                // force to java map (or list)
                argValue = new ScriptValue(argValue.getValue(DocumentContext.class).read("$"));
            case STRING:
            case PRIMITIVE:
            case NULL:
                break;
            default:
                throw new RuntimeException("only json or primitives allowed as (single) function call argument");
        }
        ScriptValue sv = preEval(name, context);
        if (sv.getType() != JS_FUNCTION) {
            logger.warn("not a js function: {} - {}", name, sv);
            return ScriptValue.NULL;
        }
        ScriptObjectMirror som = sv.getValue(ScriptObjectMirror.class);
        return evalFunctionCall(som, argValue, context);
    }

    public static Map<String, Object> toMap(ScriptObjectMirror som) {
        String[] keys = som.getOwnKeys(false);
        Map<String, Object> map = new HashMap<>(keys.length);
        for (String key : keys) {
            Object value = som.get(key);
            map.put(key, value);
            logger.trace("unpacked from js: {}: {}", key, value);
        }
        return map;
    }

    public static void callAndUpdateVars(String name, String arg, ScriptContext context) {
        ScriptValue sv = call(name, arg, context);
        Map<String, Object> result;
        switch (sv.getType()) {
            case JS_OBJECT:
                result = toMap(sv.getValue(ScriptObjectMirror.class));
                break;
            case MAP:
                result = sv.getValue(Map.class);
                break;
            default:
                logger.debug("no vars returned from function call result: {}", sv);
                return;
        }
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            context.vars.put(entry.getKey(), entry.getValue());
            logger.trace("unpacked var from map: {}", entry);
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
        String result = Script.eval(expression, context).getAsString();
        if (!"true".equals(result)) {
            return AssertionResult.fail("evaluated to false: " + expression);
        }
        return AssertionResult.PASS;
    }

}
