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
package com.intuit.karate.match;

import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class MatchOperation {

    public final MatchContext context;
    public final MatchType type;
    public final MatchValue actual;
    public final MatchValue expected;
    public final List<MatchOperation> failures;

    boolean pass = true;
    String failReason;

    public MatchOperation(MatchType type, MatchValue actual, MatchValue expected) {
        this(null, type, actual, expected);
    }

    public MatchOperation(MatchContext context, MatchType type, MatchValue actual, MatchValue expected) {
        this.type = type;
        this.actual = actual;
        this.expected = expected;
        if (context == null) {
            this.failures = new ArrayList();
            if (actual.isXml()) {
                this.context = new MatchContext(this, true, 0, "/", "/", -1);
            } else {
                this.context = new MatchContext(this, false, 0, "$", "", -1);
            }

        } else {
            this.context = context;
            this.failures = context.root.failures;
        }
    }

    private MatchType fromMatchEach() {
        switch (type) {
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
                return MatchType.NOT_EQUALS;
            case EACH_CONTAINS_DEEP:
                return MatchType.CONTAINS_DEEP;
            default:
                throw new RuntimeException("unexpected outer match type: " + type);
        }
    }

    private static MatchType macroToMatchType(boolean each, String macro) {
        if (macro.startsWith("^^")) {
            return each ? MatchType.EACH_CONTAINS_ONLY : MatchType.CONTAINS_ONLY;
        } else if (macro.startsWith("^+")) {
            return each ? MatchType.EACH_CONTAINS_DEEP : MatchType.CONTAINS_DEEP;
        } else if (macro.startsWith("^*")) {
            return each ? MatchType.EACH_CONTAINS_ANY : MatchType.CONTAINS_ANY;
        } else if (macro.startsWith("^")) {
            return each ? MatchType.EACH_CONTAINS : MatchType.CONTAINS;
        } else if (macro.startsWith("!^")) {
            return each ? MatchType.EACH_NOT_CONTAINS : MatchType.NOT_CONTAINS;
        } else if (macro.startsWith("!=")) {
            return each ? MatchType.EACH_NOT_EQUALS : MatchType.NOT_EQUALS;
        } else {
            return each ? MatchType.EACH_EQUALS : MatchType.EQUALS;
        }
    }

    private static int matchTypeToStartPos(MatchType mt) {
        switch (mt) {
            case CONTAINS_ONLY:
            case EACH_CONTAINS_ONLY:
            case CONTAINS_DEEP:
            case EACH_CONTAINS_DEEP:
            case CONTAINS_ANY:
            case EACH_CONTAINS_ANY:
            case NOT_CONTAINS:
            case EACH_NOT_CONTAINS:
            case NOT_EQUALS:
            case EACH_NOT_EQUALS:
                return 2;
            case CONTAINS:
            case EACH_CONTAINS:
                return 1;
            default:
                return 0;
        }
    }

    public boolean execute() {
        switch (type) {
            case EACH_CONTAINS:
            case EACH_NOT_CONTAINS:
            case EACH_CONTAINS_ONLY:
            case EACH_CONTAINS_ANY:
            case EACH_EQUALS:
            case EACH_NOT_EQUALS:
            case EACH_CONTAINS_DEEP:
                if (actual.isList()) {
                    List list = actual.getValue();
                    MatchType nestedMatchType = fromMatchEach();
                    int count = list.size();
                    JsEngine jsEngine = JsEngine.global();
                    for (int i = 0; i < count; i++) {
                        Object o = list.get(i);
                        jsEngine.put("_$", o);
                        MatchOperation mo = new MatchOperation(context.descend(i), nestedMatchType, new MatchValue(o), expected);
                        mo.execute();
                        if (!mo.pass) {
                            return fail("match each failed at index " + i);
                        }
                    }
                    // if we reached here all / each LHS items completed successfully
                    return true;
                } else {
                    return fail("actual is not an array or list");
                }
            default:
            // do nothing
        }
        if (expected.isString()) {
            String expStr = expected.getValue();
            if (expStr.startsWith("#")) {
                if (type == MatchType.EQUALS) {
                    return macroEqualsExpected(expStr) ? pass() : fail(null);
                } else {
                    return macroEqualsExpected(expStr) ? fail("is equal") : pass();
                }
            }
        }
        if (actual.type != expected.type) {
            switch (type) {
                case CONTAINS:
                case CONTAINS_ANY:
                case CONTAINS_ONLY:
                case CONTAINS_DEEP:
                    if (!expected.isList()) {
                        MatchOperation mo = new MatchOperation(context, type, actual, new MatchValue(Collections.singletonList(expected.getValue())));
                        return mo.execute();
                    }
                    break;
                default:
                // do nothing
            }
            return type == MatchType.NOT_EQUALS ? pass() : fail("data types don't match");
        }
        switch (type) {
            case EQUALS:
                return actualEqualsExpected() ? pass() : fail("not equal");
            case NOT_EQUALS:
                return actualEqualsExpected() ? fail("is equal") : pass();
            case CONTAINS:
            case CONTAINS_ANY:
            case CONTAINS_ONLY:
            case CONTAINS_DEEP:
                return actualContainsExpected() ? pass() : fail("actual does not contain expected");
            case NOT_CONTAINS:
                return actualContainsExpected() ? fail("actual contains expected") : pass();
            default:
                throw new RuntimeException("unexpected match type: " + type);
        }
    }

    private boolean macroEqualsExpected(String expStr) {
        boolean optional = expStr.startsWith("##");
        int minLength = optional ? 3 : 2;
        if (expStr.length() > minLength) {
            String macro = expStr.substring(minLength - 1);
            if (macro.startsWith("(") && macro.endsWith(")")) {
                macro = macro.substring(1, macro.length() - 1);
                MatchType nestedType = macroToMatchType(false, macro);
                int startPos = matchTypeToStartPos(nestedType);
                macro = macro.substring(startPos);
                JsValue jv = JsEngine.evalGlobal(macro);
                MatchOperation mo = new MatchOperation(context, nestedType, actual, new MatchValue(jv.getValue()));
                return mo.execute();
            } else if (macro.startsWith("[")) {
                int closeBracketPos = macro.indexOf(']');
                if (closeBracketPos != -1) { // array, match each
                    if (!actual.isList()) {
                        return fail("actual is not a list or array");
                    }
                    if (closeBracketPos > 1) {
                        String bracketContents = macro.substring(1, closeBracketPos);
                        List listAct = actual.getValue();
                        int listSize = listAct.size();
                        JsEngine jsEngine = JsEngine.global();
                        jsEngine.put("$", context.root.actual.getValue());
                        jsEngine.put("_", listSize);
                        String sizeExpr;
                        if (bracketContents.indexOf('_') != -1) { // #[_ < 5] 
                            sizeExpr = bracketContents;
                        } else { // #[5] | #[$.foo] 
                            sizeExpr = bracketContents + " == _";
                        }
                        JsValue jv = jsEngine.eval(sizeExpr);
                        if (!jv.isTrue()) {
                            return fail("actual array / list size is " + listSize);
                        }
                    }
                    if (macro.length() > closeBracketPos + 1) {
                        macro = StringUtils.trimToNull(macro.substring(closeBracketPos + 1));
                        if (macro != null) {
                            if (macro.startsWith("(") && macro.endsWith(")")) {
                                macro = macro.substring(1, macro.length() - 1); // strip parens
                            }
                            if (macro.startsWith("?")) { // #[]? _.length == 3
                                macro = "#" + macro;
                            }
                            if (macro.startsWith("#")) {
                                MatchType nestedType = type == MatchType.EQUALS ? MatchType.EACH_EQUALS : MatchType.EACH_NOT_EQUALS;
                                MatchOperation mo = new MatchOperation(context, nestedType, actual, new MatchValue(macro));
                                return mo.execute();
                            } else { // schema reference
                                MatchType nestedType = macroToMatchType(true, macro); // match each
                                int startPos = matchTypeToStartPos(nestedType);
                                macro = macro.substring(startPos);
                                JsValue jv = JsEngine.evalGlobal(macro);
                                MatchOperation mo = new MatchOperation(context, nestedType, actual, new MatchValue(jv.getValue()));
                                return mo.execute();
                            }
                        }
                    }
                    return true; // expression within square brackets is ok
                }
            } else { // '#? _ != 0' | '#string' | '#number? _ > 0'
                int questionPos = macro.indexOf('?');
                String validatorName = null;
                if (questionPos != -1) {
                    validatorName = macro.substring(0, questionPos);
                    if (macro.length() > questionPos + 1) {
                        macro = StringUtils.trimToEmpty(macro.substring(questionPos + 1));
                    } else {
                        macro = "";
                    }
                } else {
                    validatorName = macro;
                    macro = "";
                }
                validatorName = StringUtils.trimToNull(validatorName);
                if (validatorName != null) {
                    if (validatorName.startsWith("regex")) {
                        String regex = validatorName.substring(5).trim();
                        RegexValidator validator = new RegexValidator(regex);
                        ValidatorResult vr = validator.apply(actual);
                        if (!vr.pass) {
                            return fail(vr.message);
                        }
                    } else {
                        Validator validator = Match.VALIDATORS.get(validatorName);
                        if (validator != null) {
                            ValidatorResult vr = validator.apply(actual);
                            if (!vr.pass) {
                                return fail(vr.message);
                            }
                        } else { // validator part was not used
                            macro = validatorName + macro;
                        }
                    }
                }
                macro = StringUtils.trimToNull(macro);
                if (macro != null && questionPos != -1) {
                    JsEngine jsEngine = JsEngine.global();
                    jsEngine.put("$", context.root.actual.getValue());
                    jsEngine.put("_", actual.getValue());
                    JsValue jv = jsEngine.eval(macro);
                    if (!jv.isTrue()) {
                        return fail("evaluated to 'false'");
                    }
                }
            }
        }
        return true; // all ok
    }

    private boolean actualEqualsExpected() {
        switch (actual.type) {
            case NULL:
                return true; // both are null
            case BOOLEAN:
                boolean actBoolean = actual.getValue();
                boolean expBoolean = expected.getValue();
                return actBoolean == expBoolean;
            case NUMBER:
                if (actual.getValue() instanceof BigDecimal || expected.getValue() instanceof BigDecimal) {
                    BigDecimal actBigDecimal = toBigDecimal(actual.getValue());
                    BigDecimal expBigDecimal = toBigDecimal(expected.getValue());
                    return actBigDecimal.compareTo(expBigDecimal) == 0;
                } else {
                    Number actNumber = actual.getValue();
                    Number expNumber = expected.getValue();
                    return actNumber.doubleValue() == expNumber.doubleValue();
                }
            case STRING:
                return actual.getValue().equals(expected.getValue());
            case BYTES:
                byte[] actBytes = actual.getValue();
                byte[] expBytes = expected.getValue();
                return Arrays.equals(actBytes, expBytes);
            case LIST:
                List actList = actual.getValue();
                List expList = expected.getValue();
                int actListCount = actList.size();
                int expListCount = expList.size();
                if (actListCount != expListCount) {
                    return fail("actual size is not equal to expected size - " + actListCount + ":" + expListCount);
                }
                for (int i = 0; i < actListCount; i++) {
                    MatchValue actListValue = new MatchValue(actList.get(i));
                    MatchValue expListValue = new MatchValue(expList.get(i));
                    MatchOperation mo = new MatchOperation(context.descend(i), MatchType.EQUALS, actListValue, expListValue);
                    mo.execute();
                    if (!mo.pass) {
                        return fail("array / list match failed at index " + i);
                    }
                }
                return true;
            case MAP:
                Map<String, Object> actMap = actual.getValue();
                Map<String, Object> expMap = expected.getValue();
                return matchMapValues(actMap, expMap);
            case XML:
                Map<String, Object> actXml = (Map) XmlUtils.toObject(actual.getValue());
                Map<String, Object> expXml = (Map) XmlUtils.toObject(expected.getValue());
                return matchMapValues(actXml, expXml);
            case OTHER:
                return actual.getValue().equals(expected.getValue());
            default:
                throw new RuntimeException("unexpected type (match equals): " + actual.type);
        }
    }

    private boolean matchMapValues(Map<String, Object> actMap, Map<String, Object> expMap) { // combined logic for equals and contains
        if (actMap.size() > expMap.size() && (type == MatchType.EQUALS || type == MatchType.CONTAINS_ONLY)) {
            int sizeDiff = actMap.size() - expMap.size();
            Map<String, Object> diffMap = new LinkedHashMap(actMap);
            for (String key : expMap.keySet()) {
                diffMap.remove(key);
            }
            return fail("actual has " + sizeDiff + " more key(s) than expected - " + diffMap);
        }
        Set<String> unMatchedKeysAct = new LinkedHashSet(actMap.keySet());
        Set<String> unMatchedKeysExp = new LinkedHashSet(expMap.keySet());
        for (Map.Entry<String, Object> expEntry : expMap.entrySet()) {
            String key = expEntry.getKey();
            Object childExp = expEntry.getValue();
            if (!actMap.containsKey(key)) {
                if (childExp instanceof String) {
                    String childString = (String) childExp;
                    if (childString.startsWith("##") || childString.equals("#ignore") || childString.equals("#notpresent")) {
                        if (type == MatchType.CONTAINS_ANY) {
                            return true; // exit early
                        }
                        unMatchedKeysExp.remove(key);
                        if (unMatchedKeysExp.isEmpty()) {
                            if (type == MatchType.CONTAINS || type == MatchType.CONTAINS_DEEP) {
                                return true; // all expected keys matched
                            }
                        }
                        continue;
                    }
                }
                if (type != MatchType.CONTAINS_ANY) {
                    return fail("actual does not contain key - '" + key + "'");
                }
            }
            MatchValue childActValue = new MatchValue(actMap.get(key));
            MatchType childMatchType;
            if (type == MatchType.CONTAINS_DEEP) {
                childMatchType = childActValue.isMapOrListOrXml() ? MatchType.CONTAINS_DEEP : MatchType.EQUALS;
            } else {
                childMatchType = MatchType.EQUALS;
            }
            MatchOperation mo = new MatchOperation(context.descend(key), childMatchType, childActValue, new MatchValue(childExp));
            mo.execute();
            if (mo.pass) {
                if (type == MatchType.CONTAINS_ANY) {
                    return true; // exit early
                }
                unMatchedKeysExp.remove(key);
                if (unMatchedKeysExp.isEmpty()) {
                    if (type == MatchType.CONTAINS || type == MatchType.CONTAINS_DEEP) {
                        return true; // all expected keys matched
                    }
                }
                unMatchedKeysAct.remove(key);
            } else if (type == MatchType.EQUALS) {
                return fail("match failed for name: '" + key + "'");

            }
        }
        if (type == MatchType.CONTAINS_ANY) {
            return fail("no key-values matched");
        }
        if (!unMatchedKeysExp.isEmpty()) {
            return fail("all key-values did not match, expected has un-matched keys - " + unMatchedKeysExp);
        }
        if (!unMatchedKeysAct.isEmpty()) {
            return fail("all key-values did not match, actual has un-matched keys - " + unMatchedKeysAct);
        }
        return true;
    }

    private boolean actualContainsExpected() {
        switch (actual.type) {
            case STRING:
                String actString = actual.getValue();
                String expString = expected.getValue();
                return actString.contains(expString);
            case LIST:
                List actList = actual.getValue();
                List expList = expected.getValue();
                int actListCount = actList.size();
                int expListCount = expList.size();
                if (expListCount > actListCount) {
                    return fail("actual size is less than expected size - " + actListCount + ":" + expListCount);
                }
                if (type == MatchType.CONTAINS_ONLY && expListCount != actListCount) {
                    return fail("actual size is not equal to expected size - " + actListCount + ":" + expListCount);
                }
                for (Object exp : expList) { // for each item in the expected list
                    boolean found = false;
                    MatchValue expListValue = new MatchValue(exp);
                    for (int i = 0; i < actListCount; i++) {
                        MatchValue actListValue = new MatchValue(actList.get(i));
                        MatchType childMatchType;
                        if (type == MatchType.CONTAINS_DEEP) {
                            childMatchType = actListValue.isMapOrListOrXml() ? MatchType.CONTAINS_DEEP : MatchType.EQUALS;
                        } else {
                            childMatchType = MatchType.EQUALS;
                        }
                        MatchOperation mo = new MatchOperation(context.descend(i), childMatchType, actListValue, expListValue);
                        mo.execute();
                        if (mo.pass) {
                            if (type == MatchType.CONTAINS_ANY) {
                                return true; // exit early
                            } else {
                                found = true;
                                break; // next item in expected list
                            }
                        }
                    }
                    if (!found && type != MatchType.CONTAINS_ANY) { // if we reached here, all items in the actual list were scanned
                        return fail("actual list does not contain expected item - " + exp);
                    }
                }
                if (type == MatchType.CONTAINS_ANY) {
                    return fail("actual list does not contain any expected item");
                }
                return true; // if we reached here, all items in the expected list were found
            case MAP:
                Map<String, Object> actMap = actual.getValue();
                Map<String, Object> expMap = expected.getValue();
                return matchMapValues(actMap, expMap);
            case XML:
                Map<String, Object> actXml = (Map) XmlUtils.toObject(actual.getValue());
                Map<String, Object> expXml = (Map) XmlUtils.toObject(expected.getValue());
                return matchMapValues(actXml, expXml);
            default:
                throw new RuntimeException("unexpected type (match contains): " + actual.type);
        }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        } else if (o instanceof Number) {
            Number n = (Number) o;
            return BigDecimal.valueOf(n.doubleValue());
        } else {
            throw new RuntimeException("expected number or big-decimal: " + o);
        }
    }

    private boolean pass() {
        pass = true;
        return true;
    }

    private boolean fail(String reason) {
        pass = false;
        if (reason == null) {
            return false;
        }
        failReason = failReason == null ? reason : reason + " | " + failReason;
        context.root.failures.add(this);
        return false;
    }

    public String getFailureReasons() {
        return collectFailureReasons(this);
    }
    
    private boolean isXmlAttributeOrMap() {
        return context.xml && actual.isMap() 
                && (context.name.equals("@") || actual.<Map>getValue().containsKey("_"));
    }    

    private static String collectFailureReasons(MatchOperation root) {
        StringBuilder sb = new StringBuilder();
        sb.append("match failed: ").append(root.type).append('\n');
        int depth = 0;
        Collections.reverse(root.failures);
        Iterator<MatchOperation> iterator = root.failures.iterator();
        Set previousPaths = new HashSet();
        while (iterator.hasNext()) {
            MatchOperation mo = iterator.next();
            if (previousPaths.contains(mo.context.path) || mo.isXmlAttributeOrMap()) {
                continue;
            }
            previousPaths.add(mo.context.path);
            String prefix = StringUtils.repeat(' ', depth++ * 2);
            sb.append(prefix).append(mo.context.path).append(" | ").append(mo.failReason);
            sb.append(" (").append(mo.actual.type).append(':').append(mo.expected.type).append(")");
            sb.append('\n');
            if (mo.context.xml) {
                sb.append(prefix).append(mo.actual.getAsXmlString()).append('\n');
                sb.append(prefix).append(mo.expected.getAsXmlString()).append('\n');
            } else {
                sb.append(prefix).append(mo.actual.getDisplayString()).append('\n');
                sb.append(prefix).append(mo.expected.getDisplayString()).append('\n');
            }
        }
        return sb.toString();
    }

}
