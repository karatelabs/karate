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
package com.intuit.karate;

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

    public static final String REGEX = "regex";

    final Match.Context context;
    final Match.Type type;
    final Match.Value actual;
    final Match.Value expected;
    final List<MatchOperation> failures;

    boolean pass = true;
    private String failReason;

    MatchOperation(Match.Type type, Match.Value actual, Match.Value expected) {
        this(JsEngine.global(), null, type, actual, expected);
    }

    MatchOperation(JsEngine js, Match.Type type, Match.Value actual, Match.Value expected) {
        this(js, null, type, actual, expected);
    }

    MatchOperation(Match.Context context, Match.Type type, Match.Value actual, Match.Value expected) {
        this(null, context, type, actual, expected);
    }

    private MatchOperation(JsEngine js, Match.Context context, Match.Type type, Match.Value actual, Match.Value expected) {
        this.type = type;
        this.actual = actual;
        this.expected = expected;
        if (context == null) {
            if (js == null) {
                js = JsEngine.global();
            }
            this.failures = new ArrayList();
            if (actual.isXml()) {
                this.context = new Match.Context(js, this, true, 0, "/", "", -1);
            } else {
                this.context = new Match.Context(js, this, false, 0, "$", "", -1);
            }
        } else {
            this.context = context;
            this.failures = context.root.failures;
        }
    }

    private Match.Type fromMatchEach() {
        switch (type) {
            case EACH_CONTAINS:
                return Match.Type.CONTAINS;
            case EACH_NOT_CONTAINS:
                return Match.Type.NOT_CONTAINS;
            case EACH_CONTAINS_ONLY:
                return Match.Type.CONTAINS_ONLY;
            case EACH_CONTAINS_ANY:
                return Match.Type.CONTAINS_ANY;
            case EACH_EQUALS:
                return Match.Type.EQUALS;
            case EACH_NOT_EQUALS:
                return Match.Type.NOT_EQUALS;
            case EACH_CONTAINS_DEEP:
                return Match.Type.CONTAINS_DEEP;
            default:
                throw new RuntimeException("unexpected outer match type: " + type);
        }
    }

    private static Match.Type macroToMatchType(boolean each, String macro) {
        if (macro.startsWith("^^")) {
            return each ? Match.Type.EACH_CONTAINS_ONLY : Match.Type.CONTAINS_ONLY;
        } else if (macro.startsWith("^+")) {
            return each ? Match.Type.EACH_CONTAINS_DEEP : Match.Type.CONTAINS_DEEP;
        } else if (macro.startsWith("^*")) {
            return each ? Match.Type.EACH_CONTAINS_ANY : Match.Type.CONTAINS_ANY;
        } else if (macro.startsWith("^")) {
            return each ? Match.Type.EACH_CONTAINS : Match.Type.CONTAINS;
        } else if (macro.startsWith("!^")) {
            return each ? Match.Type.EACH_NOT_CONTAINS : Match.Type.NOT_CONTAINS;
        } else if (macro.startsWith("!=")) {
            return each ? Match.Type.EACH_NOT_EQUALS : Match.Type.NOT_EQUALS;
        } else {
            return each ? Match.Type.EACH_EQUALS : Match.Type.EQUALS;
        }
    }

    private static int matchTypeToStartPos(Match.Type mt) {
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

    boolean execute() {
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
                    Match.Type nestedMatchType = fromMatchEach();
                    int count = list.size();
                    for (int i = 0; i < count; i++) {
                        Object o = list.get(i);
                        context.JS.put("_$", o);
                        MatchOperation mo = new MatchOperation(context.descend(i), nestedMatchType, new Match.Value(o), expected);
                        mo.execute();
                        context.JS.bindings.removeMember("_$");
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
        if (actual.isNotPresent()) {
            if (!expected.isString() || !expected.getAsString().startsWith("#")) {
                return fail("actual path does not exist");
            }
        }
        if (actual.type != expected.type) {
            switch (type) {
                case CONTAINS:
                case NOT_CONTAINS:
                case CONTAINS_ANY:
                case CONTAINS_ONLY:
                case CONTAINS_DEEP:
                case CONTAINS_ANY_DEEP:
                    if (!expected.isList()) {
                        MatchOperation mo = new MatchOperation(context, type, actual, new Match.Value(Collections.singletonList(expected.getValue())));
                        mo.execute();
                        return mo.pass ? pass() : fail(mo.failReason);
                    }
                    break;
                default:
                // do nothing
            }
            if (expected.isXml() && actual.isMap()) {
                // special case, auto-convert rhs                
                MatchOperation mo = new MatchOperation(context, type, actual, new Match.Value(XmlUtils.toObject(expected.getValue(), true)));
                mo.execute();
                return mo.pass ? pass() : fail(mo.failReason);
            }
            if (expected.isString()) {
                String expStr = expected.getValue();
                if (!expStr.startsWith("#")) { // edge case if rhs is macro
                    return type == Match.Type.NOT_EQUALS ? pass() : fail("data types don't match");
                }
            } else {
                return type == Match.Type.NOT_EQUALS ? pass() : fail("data types don't match");
            }
        }
        if (expected.isString()) {
            String expStr = expected.getValue();
            if (expStr.startsWith("#")) {
                switch (type) {
                    case NOT_EQUALS:
                        return macroEqualsExpected(expStr) ? fail("is equal") : pass();
                    case NOT_CONTAINS:
                        return macroEqualsExpected(expStr) ? fail("actual contains expected") : pass();
                    default:
                        return macroEqualsExpected(expStr) ? pass() : fail(null);
                }
            }
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
            case CONTAINS_ANY_DEEP:
                return actualContainsExpected() ? pass() : fail("actual does not contain expected");
            case NOT_CONTAINS:
                return actualContainsExpected() ? fail("actual contains expected") : pass();
            default:
                throw new RuntimeException("unexpected match type: " + type);
        }
    }

    private boolean macroEqualsExpected(String expStr) {
        boolean optional = expStr.startsWith("##");
        if (optional && actual.isNull()) { // exit early
            return true;
        }
        int minLength = optional ? 3 : 2;
        if (expStr.length() > minLength) {
            String macro = expStr.substring(minLength - 1);
            if (macro.startsWith("(") && macro.endsWith(")")) {
                macro = macro.substring(1, macro.length() - 1);
                Match.Type nestedType = macroToMatchType(false, macro);
                int startPos = matchTypeToStartPos(nestedType);
                macro = macro.substring(startPos);
                if (actual.isList()) { // special case, look for partial maps within list
                    if (nestedType == Match.Type.CONTAINS) {
                        nestedType = Match.Type.CONTAINS_DEEP;
                    } else if (nestedType == Match.Type.CONTAINS_ANY) {
                        nestedType = Match.Type.CONTAINS_ANY_DEEP;
                    }
                }
                context.JS.put("$", context.root.actual.getValue());
                context.JS.put("_", actual.getValue());
                JsValue jv = context.JS.eval(macro);
                context.JS.bindings.removeMember("$");
                context.JS.bindings.removeMember("_");
                MatchOperation mo = new MatchOperation(context, nestedType, actual, new Match.Value(jv.getValue()));
                return mo.execute();
            } else if (macro.startsWith("[")) {
                int closeBracketPos = macro.indexOf(']');
                if (closeBracketPos != -1) { // array, match each
                    if (!actual.isList()) {
                        return fail("actual is not an array");
                    }
                    if (closeBracketPos > 1) {
                        String bracketContents = macro.substring(1, closeBracketPos);
                        List listAct = actual.getValue();
                        int listSize = listAct.size();
                        context.JS.put("$", context.root.actual.getValue());
                        context.JS.put("_", listSize);
                        String sizeExpr;
                        if (bracketContents.indexOf('_') != -1) { // #[_ < 5] 
                            sizeExpr = bracketContents;
                        } else { // #[5] | #[$.foo] 
                            sizeExpr = bracketContents + " == _";
                        }
                        JsValue jv = context.JS.eval(sizeExpr);
                        context.JS.bindings.removeMember("$");
                        context.JS.bindings.removeMember("_");
                        if (!jv.isTrue()) {
                            return fail("actual array length is " + listSize);
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
                                MatchOperation mo = new MatchOperation(context, Match.Type.EACH_EQUALS, actual, new Match.Value(macro));
                                mo.execute();
                                return mo.pass ? pass() : fail("all array elements matched");
                            } else { // schema reference
                                Match.Type nestedType = macroToMatchType(true, macro); // match each
                                int startPos = matchTypeToStartPos(nestedType);
                                macro = macro.substring(startPos);
                                JsValue jv = context.JS.eval(macro);
                                MatchOperation mo = new MatchOperation(context, nestedType, actual, new Match.Value(jv.getValue()));
                                return mo.execute();
                            }
                        }
                    }
                    return true; // expression within square brackets is ok
                }
            } else { // '#? _ != 0' | '#string' | '#number? _ > 0'
                int questionPos = macro.indexOf('?');
                String validatorName = null;
                // in case of regex we don't want to remove the '?'
                if (questionPos != -1 && !macro.startsWith(REGEX)) {
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
                    Match.Validator validator = null;
                    if (validatorName.startsWith(REGEX)) {
                        String regex = validatorName.substring(5).trim();
                        validator = new Match.RegexValidator(regex);
                    } else {
                        validator = Match.VALIDATORS.get(validatorName);
                    }
                    if (validator != null) {
                        if (optional && (actual.isNotPresent() || actual.isNull())) {
                            // pass
                        } else if (!optional && actual.isNotPresent()) {
                            // if the element is not present the expected result can only be
                            // the notpresent keyword, ignored or an optional comparison
                            return expected.isNotPresent() || "#ignore".contentEquals(expected.getAsString());
                        } else {
                            Match.Result mr = validator.apply(actual);
                            if (!mr.pass) {
                                return fail(mr.message);
                            }
                        }
                    } else if (!validatorName.startsWith(REGEX)) { // expected is a string that happens to start with "#"
                        String actualValue = actual.getValue();
                        switch (type) {
                            case CONTAINS:
                                return actualValue.contains(expStr);
                            default:
                                return actualValue.equals(expStr);
                        }
                    }

                }
                macro = StringUtils.trimToNull(macro);
                if (macro != null && questionPos != -1) {
                    context.JS.put("$", context.root.actual.getValue());
                    context.JS.put("_", actual.getValue());
                    JsValue jv = context.JS.eval(macro);
                    context.JS.bindings.removeMember("$");
                    context.JS.bindings.removeMember("_");
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
                    return fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                }
                for (int i = 0; i < actListCount; i++) {
                    Match.Value actListValue = new Match.Value(actList.get(i));
                    Match.Value expListValue = new Match.Value(expList.get(i));
                    MatchOperation mo = new MatchOperation(context.descend(i), Match.Type.EQUALS, actListValue, expListValue);
                    mo.execute();
                    if (!mo.pass) {
                        return fail("array match failed at index " + i);
                    }
                }
                return true;
            case MAP:
                Map<String, Object> actMap = actual.getValue();
                Map<String, Object> expMap = expected.getValue();
                return matchMapValues(actMap, expMap);
            case XML:
                Map<String, Object> actXml = (Map) XmlUtils.toObject(actual.getValue(), true);
                Map<String, Object> expXml = (Map) XmlUtils.toObject(expected.getValue(), true);
                return matchMapValues(actXml, expXml);
            case OTHER:
                return actual.getValue().equals(expected.getValue());
            default:
                throw new RuntimeException("unexpected type (match equals): " + actual.type);
        }
    }

    private boolean matchMapValues(Map<String, Object> actMap, Map<String, Object> expMap) { // combined logic for equals and contains
        if (actMap.size() > expMap.size() && (type == Match.Type.EQUALS || type == Match.Type.CONTAINS_ONLY)) {
            int sizeDiff = actMap.size() - expMap.size();
            Map<String, Object> diffMap = new LinkedHashMap(actMap);
            for (String key : expMap.keySet()) {
                diffMap.remove(key);
            }
            return fail("actual has " + sizeDiff + " more key(s) than expected - " + JsonUtils.toJson(diffMap));
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
                        if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
                            return true; // exit early
                        }
                        unMatchedKeysExp.remove(key);
                        if (unMatchedKeysExp.isEmpty()) {
                            if (type == Match.Type.CONTAINS || type == Match.Type.CONTAINS_DEEP) {
                                return true; // all expected keys matched
                            }
                        }
                        continue;
                    }
                }
                if (type != Match.Type.CONTAINS_ANY && type != Match.Type.CONTAINS_ANY_DEEP) {
                    return fail("actual does not contain key - '" + key + "'");
                }
            }
            Match.Value childActValue = new Match.Value(actMap.get(key));
            Match.Type childMatchType;
            if (type == Match.Type.CONTAINS_DEEP) {
                childMatchType = childActValue.isMapOrListOrXml() ? Match.Type.CONTAINS_DEEP : Match.Type.EQUALS;
            } else {
                childMatchType = Match.Type.EQUALS;
            }
            MatchOperation mo = new MatchOperation(context.descend(key), childMatchType, childActValue, new Match.Value(childExp));
            mo.execute();
            if (mo.pass) {
                if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
                    return true; // exit early
                }
                unMatchedKeysExp.remove(key);
                if (unMatchedKeysExp.isEmpty()) {
                    if (type == Match.Type.CONTAINS || type == Match.Type.CONTAINS_DEEP) {
                        return true; // all expected keys matched
                    }
                }
                unMatchedKeysAct.remove(key);
            } else if (type == Match.Type.EQUALS) {
                return fail("match failed for name: '" + key + "'");
            }
        }
        if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
            return unMatchedKeysExp.isEmpty() ? true : fail("no key-values matched");
        }
        if (unMatchedKeysExp.isEmpty()) { 
            if (type == Match.Type.CONTAINS || type == Match.Type.CONTAINS_DEEP) {
                return true; // all expected keys matched, expMap was empty in the first place    
            }
            if (type == Match.Type.NOT_CONTAINS && !expMap.isEmpty()) {
                return true; // hack alert: the NOT_CONTAINS will be reversed by the calling routine
            }
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
                if (type != Match.Type.CONTAINS_ANY && type != Match.Type.CONTAINS_ANY_DEEP && expListCount > actListCount) {
                    return fail("actual array length is less than expected - " + actListCount + ":" + expListCount);
                }
                if (type == Match.Type.CONTAINS_ONLY && expListCount != actListCount) {
                    return fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                }
                for (Object exp : expList) { // for each item in the expected list
                    boolean found = false;
                    Match.Value expListValue = new Match.Value(exp);
                    for (int i = 0; i < actListCount; i++) {
                        Match.Value actListValue = new Match.Value(actList.get(i));
                        Match.Type childMatchType;
                        switch (type) {
                            case CONTAINS_DEEP:
                                childMatchType = actListValue.isMapOrListOrXml() ? Match.Type.CONTAINS_DEEP : Match.Type.EQUALS;
                                break;
                            case CONTAINS_ANY_DEEP:
                                childMatchType = actListValue.isMapOrListOrXml() ? Match.Type.CONTAINS_ANY : Match.Type.EQUALS;
                                break;
                            default:
                                childMatchType = Match.Type.EQUALS;
                        }
                        MatchOperation mo = new MatchOperation(context.descend(i), childMatchType, actListValue, expListValue);
                        mo.execute();
                        if (mo.pass) {
                            if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
                                return true; // exit early
                            } else {
                                found = true;
                                break; // next item in expected list
                            }
                        }
                    }
                    if (!found && type != Match.Type.CONTAINS_ANY && type != Match.Type.CONTAINS_ANY_DEEP) { // if we reached here, all items in the actual list were scanned
                        return fail("actual array does not contain expected item - " + expListValue.getAsString());
                    }
                }
                if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
                    return fail("actual array does not contain any of the expected items");
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
            throw new RuntimeException("expected number instead of: " + o);
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

    String getFailureReasons() {
        return collectFailureReasons(this);
    }

    private boolean isXmlAttributeOrMap() {
        return context.xml && actual.isMap()
                && (context.name.equals("@") || actual.<Map>getValue().containsKey("_"));
    }

    private static String collectFailureReasons(MatchOperation root) {
        StringBuilder sb = new StringBuilder();
        sb.append("match failed: ").append(root.type).append('\n');
        Collections.reverse(root.failures);
        Iterator<MatchOperation> iterator = root.failures.iterator();
        Set previousPaths = new HashSet();
        int index = 0;
        int prevDepth = -1;
        while (iterator.hasNext()) {
            MatchOperation mo = iterator.next();
            if (previousPaths.contains(mo.context.path) || mo.isXmlAttributeOrMap()) {
                continue;
            }
            previousPaths.add(mo.context.path);
            if (mo.context.depth != prevDepth) {
                prevDepth = mo.context.depth;
                index++;
            }
            String prefix = StringUtils.repeat(' ', index * 2);
            sb.append(prefix).append(mo.context.path).append(" | ").append(mo.failReason);
            sb.append(" (").append(mo.actual.type).append(':').append(mo.expected.type).append(")");
            sb.append('\n');
            if (mo.context.xml) {
                sb.append(prefix).append(mo.actual.getAsXmlString()).append('\n');
                sb.append(prefix).append(mo.expected.getAsXmlString()).append('\n');
            } else {
                Match.Value expected = mo.expected.getSortedLike(mo.actual);
                sb.append(prefix).append(mo.actual.getWithinSingleQuotesIfString()).append('\n');
                sb.append(prefix).append(expected.getWithinSingleQuotesIfString()).append('\n');
            }
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

}
