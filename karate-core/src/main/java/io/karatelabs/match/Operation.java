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
package io.karatelabs.match;

import io.karatelabs.common.Json;
import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import io.karatelabs.js.Engine;
import io.karatelabs.js.Terms;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Operation {

    static final String REGEX = "regex";

    final MatchContext context;
    final Match.Type type;
    final Value actual;
    final Value expected;
    final List<Operation> failures;
    final boolean matchEachEmptyAllowed;

    boolean pass = true;
    private String failReason;

    Operation(Match.Type type, Value actual, Value expected) {
        this(new Engine(), null, type, actual, expected, false);
    }

    Operation(Engine engine, Match.Type type, Value actual, Value expected) {
        this(engine, null, type, actual, expected, false);
    }

    Operation(Engine engine, Match.Type type, Value actual, Value expected, boolean matchEachEmptyAllowed) {
        this(engine, null, type, actual, expected, matchEachEmptyAllowed);
    }

    Operation(MatchContext context, Match.Type type, Value actual, Value expected, boolean matchEachEmptyAllowed) {
        this(null, context, type, actual, expected, matchEachEmptyAllowed);
    }

    private Operation(Engine engine, MatchContext context, Match.Type type, Value actual, Value expected, boolean matchEachEmptyAllowed) {
        this.type = type;
        this.actual = actual;
        this.expected = expected;
        this.matchEachEmptyAllowed = matchEachEmptyAllowed;
        if (context == null) {
            if (engine == null) {
                engine = new Engine();
            }
            this.failures = new ArrayList<>();
            if (actual.isXml()) {
                this.context = new MatchContext(engine, this, true, 0, "/", "", -1);
            } else {
                this.context = new MatchContext(engine, this, false, 0, "$", "", -1);
            }
        } else {
            this.context = context;
            this.failures = context.root.failures;
        }
    }

    private Match.Type fromMatchEach() {
        return switch (type) {
            case EACH_CONTAINS -> Match.Type.CONTAINS;
            case EACH_NOT_CONTAINS -> Match.Type.NOT_CONTAINS;
            case EACH_CONTAINS_ONLY -> Match.Type.CONTAINS_ONLY;
            case EACH_CONTAINS_ANY -> Match.Type.CONTAINS_ANY;
            case EACH_EQUALS -> Match.Type.EQUALS;
            case EACH_NOT_EQUALS -> Match.Type.NOT_EQUALS;
            case EACH_CONTAINS_DEEP -> Match.Type.CONTAINS_DEEP;
            default -> throw new RuntimeException("unexpected outer match type: " + type);
        };
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
        } else if (macro.startsWith("!<")) {
            return Match.Type.NOT_WITHIN;
        } else if (macro.startsWith("<")) {
            return Match.Type.WITHIN;
        } else if (macro.startsWith("!=")) {
            return each ? Match.Type.EACH_NOT_EQUALS : Match.Type.NOT_EQUALS;
        } else {
            return each ? Match.Type.EACH_EQUALS : Match.Type.EQUALS;
        }
    }

    private static int matchTypeToStartPos(Match.Type mt) {
        return switch (mt) {
            case CONTAINS_ONLY, EACH_CONTAINS_ONLY, CONTAINS_DEEP, EACH_CONTAINS_DEEP, CONTAINS_ANY, EACH_CONTAINS_ANY,
                 NOT_CONTAINS, EACH_NOT_CONTAINS, NOT_EQUALS, EACH_NOT_EQUALS, NOT_WITHIN -> 2;
            case CONTAINS, EACH_CONTAINS, WITHIN -> 1;
            default -> 0;
        };
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
                    int count = actual.getListSize();
                    if (count == 0 && !matchEachEmptyAllowed) {
                        return fail("match each failed, empty array / list");
                    }
                    Match.Type nestedMatchType = fromMatchEach();
                    List<Integer> failedIndices = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        Object o = actual.getListElement(i);
                        context.engine.put("_$", o);
                        Operation mo = new Operation(context.descend(i), nestedMatchType, new Value(o), expected, matchEachEmptyAllowed);
                        mo.execute();
                        context.engine.remove("_$");
                        if (!mo.pass) {
                            failedIndices.add(i);
                        }
                    }
                    if (!failedIndices.isEmpty()) {
                        if (failedIndices.size() == 1) {
                            return fail("match each failed at index " + failedIndices.getFirst());
                        }
                        return fail("match each failed at indices " + failedIndices);
                    }
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
            // Special case: STRING contains XML-looking string - do substring matching
            // Must check BEFORE the list-wrapping logic below
            if (actual.isString() && expected.isXml() && isContainsType(type)) {
                String expStr = Xml.toString((org.w3c.dom.Node) expected.getValue(), false);
                Operation mo = new Operation(context, type, actual, new Value(expStr), matchEachEmptyAllowed);
                mo.execute();
                return mo.pass ? pass() : fail(mo.failReason);
            }
            switch (type) {
                case CONTAINS:
                case NOT_CONTAINS:
                case CONTAINS_ANY:
                case CONTAINS_ONLY:
                case CONTAINS_DEEP:
                case CONTAINS_ONLY_DEEP:
                case CONTAINS_ANY_DEEP:
                    // don't tamper with strings on the RHS that represent arrays or objects
                    if (!expected.isList() && !(expected.isString() && expected.isArrayObjectOrReference())) {
                        Operation mo = new Operation(context, type, actual, new Value(Collections.singletonList(expected.getValue())), matchEachEmptyAllowed);
                        mo.execute();
                        return mo.pass ? pass() : fail(mo.failReason);
                    }
                    break;
                case WITHIN:
                case NOT_WITHIN:
                    // for WITHIN, if actual is not a list but expected is, wrap actual in a list
                    if (!actual.isList() && !(actual.isString() && actual.isArrayObjectOrReference())) {
                        Operation mo = new Operation(context, type, new Value(Collections.singletonList(actual.getValue())), expected, matchEachEmptyAllowed);
                        mo.execute();
                        return mo.pass ? pass() : fail(mo.failReason);
                    }
                    break;
                default:
                    // do nothing
            }
            if (expected.isXml() && actual.isMap()) {
                // special case, auto-convert rhs
                Operation mo = new Operation(context, type, actual, new Value(Xml.toObject(expected.getValue(), true)), matchEachEmptyAllowed);
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
                return switch (type) {
                    case NOT_EQUALS -> macroEqualsExpected(expStr) ? fail("is equal") : pass();
                    case NOT_CONTAINS -> macroEqualsExpected(expStr) ? fail("actual contains expected") : pass();
                    default -> macroEqualsExpected(expStr) ? pass() : fail(null);
                };
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
            case CONTAINS_ONLY_DEEP:
            case CONTAINS_ANY_DEEP:
                return actualContainsExpected() ? pass() : fail("actual does not contain expected");
            case NOT_CONTAINS:
                return actualContainsExpected() ? fail("actual contains expected") : pass();
            case WITHIN:
                return actualWithinExpected() ? pass() : fail("actual is not within expected");
            case NOT_WITHIN:
                return actualWithinExpected() ? fail("actual is within expected") : pass();
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
                context.engine.put("$", context.root.actual.getValue());
                context.engine.put("_", actual.getValue());
                Object evalResult = context.engine.eval(macro);
                context.engine.remove("$");
                context.engine.remove("_");
                // For #(^expr), #(^*expr), etc. where actual is a list and evalResult is a Map,
                // we need to check if any element in the list matches the expected value
                // using the nestedType. This is different from "list contains exact element".
                // When evalResult is also a List, we use normal list-contains-list logic.
                if (actual.isList() && evalResult instanceof java.util.Map
                        && (nestedType == Match.Type.CONTAINS || nestedType == Match.Type.CONTAINS_ANY)) {
                    Value expectedValue = new Value(evalResult);
                    for (int i = 0; i < actual.getListSize(); i++) {
                        Value elem = new Value(actual.getListElement(i));
                        Operation mo = new Operation(context.descend(i), nestedType, elem, expectedValue, matchEachEmptyAllowed);
                        if (mo.execute()) {
                            return true;
                        }
                    }
                    return fail("no array element matches expected");
                }
                Operation mo = new Operation(context, nestedType, actual, new Value(evalResult), matchEachEmptyAllowed);
                return mo.execute();
            } else if (macro.startsWith("[")) {
                int closeBracketPos = macro.indexOf(']');
                if (closeBracketPos != -1) { // array, match each
                    if (!actual.isList()) {
                        return fail("actual is not an array");
                    }
                    if (closeBracketPos > 1) {
                        String bracketContents = macro.substring(1, closeBracketPos);
                        List<Object> listAct = actual.getValue();
                        int listSize = listAct.size();
                        context.engine.put("$", context.root.actual.getValue());
                        context.engine.put("_", listSize);
                        String sizeExpr;
                        if (containsPlaceholderUnderscore(bracketContents)) { // #[_ < 5]
                            sizeExpr = bracketContents;
                        } else { // #[5] | #[$.foo]
                            sizeExpr = bracketContents + " == _";
                        }
                        Object evalResult = context.engine.eval(sizeExpr);
                        context.engine.remove("$");
                        context.engine.remove("_");
                        if (!Terms.isTruthy(evalResult)) {
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
                                Operation mo = new Operation(context, Match.Type.EACH_EQUALS, actual, new Value(macro), matchEachEmptyAllowed);
                                mo.execute();
                                return mo.pass ? pass() : fail("all array elements matched");
                            } else { // schema reference
                                Match.Type nestedType = macroToMatchType(true, macro); // match each
                                int startPos = matchTypeToStartPos(nestedType);
                                macro = macro.substring(startPos);
                                Object evalResult = context.engine.eval(macro);
                                Operation mo = new Operation(context, nestedType, actual, new Value(evalResult), matchEachEmptyAllowed);
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
                    Validators.Validator validator = null;
                    if (validatorName.startsWith(REGEX)) {
                        String regex = validatorName.substring(5).trim();
                        validator = new Validators.RegexValidator(regex);
                    } else {
                        validator = Validators.VALIDATORS.get(validatorName);
                    }
                    if (validator != null) {
                        if (optional && (actual.isNotPresent() || actual.isNull())) {
                            // pass
                        } else if (!optional && actual.isNotPresent()) {
                            // if the element is not present the expected result can only be
                            // the notpresent keyword, ignored or an optional comparison
                            return expected.isNotPresent() || "#ignore".contentEquals(expected.getAsString());
                        } else {
                            Result mr = validator.apply(actual);
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
                    context.engine.put("$", context.root.actual.getValue());
                    context.engine.put("_", actual.getValue());
                    Object evalResult = context.engine.eval(macro);
                    context.engine.remove("$");
                    context.engine.remove("_");
                    if (!Terms.isTruthy(evalResult)) {
                        return fail("evaluated to 'false'");
                    }
                }
            }
        }
        return true; // all ok
    }

    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("\\W_\\W|\\W_|_\\W");

    private boolean containsPlaceholderUnderscore(String bracketContents) {
        Matcher m1 = UNDERSCORE_PATTERN.matcher(bracketContents);
        while (m1.find()) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
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
                int actListCount = actual.getListSize();
                int expListCount = expected.getListSize();
                if (actListCount != expListCount) {
                    return fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                }
                List<Integer> failedListIndices = new ArrayList<>();
                for (int i = 0; i < actListCount; i++) {
                    Value actListValue = new Value(actual.getListElement(i));
                    Value expListValue = new Value(expected.getListElement(i));
                    Operation mo = new Operation(context.descend(i), Match.Type.EQUALS, actListValue, expListValue, matchEachEmptyAllowed);
                    mo.execute();
                    if (!mo.pass) {
                        failedListIndices.add(i);
                    }
                }
                if (!failedListIndices.isEmpty()) {
                    if (failedListIndices.size() == 1) {
                        return fail("array match failed at index " + failedListIndices.getFirst());
                    }
                    return fail("array match failed at indices " + failedListIndices);
                }
                return true;
            case MAP:
                Map<String, Object> actMap = actual.getValue();
                Map<String, Object> expMap = expected.getValue();
                return matchMapValues(actMap, expMap);
            case XML:
                Map<String, Object> actXml = (Map<String, Object>) Xml.toObject(actual.getValue(), true);
                Map<String, Object> expXml = (Map<String, Object>) Xml.toObject(expected.getValue(), true);
                return matchMapValues(actXml, expXml);
            case OTHER:
                return actual.getValue().equals(expected.getValue());
            default:
                throw new RuntimeException("unexpected type (match equals): " + actual.type);
        }
    }

    private boolean matchMapValues(Map<String, Object> actMap, Map<String, Object> expMap) { // combined logic for equals and contains
        if (actMap.size() > expMap.size() && (type == Match.Type.EQUALS || type == Match.Type.CONTAINS_ONLY || type == Match.Type.CONTAINS_ONLY_DEEP)) {
            int sizeDiff = actMap.size() - expMap.size();
            Map<String, Object> diffMap = new LinkedHashMap<>(actMap);
            for (String key : expMap.keySet()) {
                diffMap.remove(key);
            }
            return fail("actual has " + sizeDiff + " more key(s) than expected - " + Json.stringifyStrict(diffMap));
        }
        Set<String> unMatchedKeysAct = new LinkedHashSet<>(actMap.keySet());
        Set<String> unMatchedKeysExp = new LinkedHashSet<>(expMap.keySet());
        List<String> missingKeys = new ArrayList<>();
        List<String> failedKeys = new ArrayList<>();
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
                    missingKeys.add(key);
                }
                continue;
            }
            Value childActValue = new Value(actMap.get(key));
            Match.Type childMatchType;
            if (type == Match.Type.CONTAINS_DEEP) {
                childMatchType = childActValue.isMapOrListOrXml() ? Match.Type.CONTAINS_DEEP : Match.Type.EQUALS;
            } else if (type == Match.Type.CONTAINS_ONLY_DEEP) {
                childMatchType = childActValue.isMapOrListOrXml() ? Match.Type.CONTAINS_ONLY_DEEP : Match.Type.EQUALS;
            } else {
                childMatchType = Match.Type.EQUALS;
            }
            Operation mo = new Operation(context.descend(key), childMatchType, childActValue, new Value(childExp), matchEachEmptyAllowed);
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
                failedKeys.add(key);
            }
        }
        if (!missingKeys.isEmpty()) {
            if (missingKeys.size() == 1) {
                return fail("actual does not contain key - '" + missingKeys.get(0) + "'");
            }
            return fail("actual does not contain keys - " + missingKeys);
        }
        if (!failedKeys.isEmpty()) {
            if (failedKeys.size() == 1) {
                return fail("match failed for name: '" + failedKeys.get(0) + "'");
            }
            return fail("match failed for names: " + failedKeys);
        }
        if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
            return unMatchedKeysExp.isEmpty() || fail("no key-values matched");
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

    @SuppressWarnings("unchecked")
    private boolean actualContainsExpected() {
        switch (actual.type) {
            case STRING:
                String actString = actual.getValue();
                String expString = expected.getValue();
                return actString.contains(expString);
            case LIST:
                int actListCount = actual.getListSize();
                int expListCount = expected.getListSize();
                // visited array used to handle duplicates
                boolean[] actVisitedList = new boolean[actListCount];
                if (type != Match.Type.CONTAINS_ANY && type != Match.Type.CONTAINS_ANY_DEEP && expListCount > actListCount) {
                    return fail("actual array length is less than expected - " + actListCount + ":" + expListCount);
                }
                if ((type == Match.Type.CONTAINS_ONLY || type == Match.Type.CONTAINS_ONLY_DEEP) && expListCount != actListCount) {
                    return fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                }
                List<String> notFoundItems = new ArrayList<>();
                for (int j = 0; j < expListCount; j++) { // for each item in the expected list
                    boolean found = false;
                    Value expListValue = new Value(expected.getListElement(j));
                    int failuresBeforeSearch = failures.size(); // track failures before this search
                    for (int i = 0; i < actListCount; i++) {
                        Value actListValue = new Value(actual.getListElement(i));
                        Match.Type childMatchType;
                        switch (type) {
                            case CONTAINS_DEEP:
                                childMatchType = actListValue.isMapOrListOrXml() ? Match.Type.CONTAINS_DEEP : Match.Type.EQUALS;
                                break;
                            case CONTAINS_ONLY_DEEP:
                                childMatchType = actListValue.isMapOrListOrXml() ? Match.Type.CONTAINS_ONLY_DEEP : Match.Type.EQUALS;
                                break;
                            case CONTAINS_ANY_DEEP:
                                childMatchType = actListValue.isMapOrListOrXml() ? Match.Type.CONTAINS_ANY : Match.Type.EQUALS;
                                break;
                            default:
                                childMatchType = Match.Type.EQUALS;
                        }
                        Operation mo = new Operation(context.descend(i), childMatchType, actListValue, expListValue, matchEachEmptyAllowed);
                        mo.execute();
                        if (mo.pass) {
                            if (type == Match.Type.CONTAINS_ANY || type == Match.Type.CONTAINS_ANY_DEEP) {
                                return true; // exit early
                            }
                            // contains only : If element is found also check its occurrence in actVisitedList
                            else if (type == Match.Type.CONTAINS_ONLY) {
                                // if not yet visited
                                if (!actVisitedList[i]) {
                                    // mark it visited
                                    actVisitedList[i] = true;
                                    found = true;
                                    break; // next item in expected list
                                }
                                // else do nothing does not consider it a match
                            } else {
                                found = true;
                                break; // next item in expected list
                            }
                        }
                    }
                    if (found) {
                        // Remove search failures - they were just "not this one, keep looking"
                        while (failures.size() > failuresBeforeSearch) {
                            failures.removeLast();
                        }
                    }
                    if (!found && type != Match.Type.CONTAINS_ANY && type != Match.Type.CONTAINS_ANY_DEEP) {
                        notFoundItems.add(expListValue.getAsString());
                    }
                }
                if (!notFoundItems.isEmpty()) {
                    if (notFoundItems.size() == 1) {
                        return fail("actual array does not contain expected item - " + notFoundItems.get(0));
                    }
                    return fail("actual array does not contain expected items - " + notFoundItems);
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
                Map<String, Object> actXml = (Map<String, Object>) Xml.toObject(actual.getValue());
                Map<String, Object> expXml = (Map<String, Object>) Xml.toObject(expected.getValue());
                return matchMapValues(actXml, expXml);
            default:
                throw new RuntimeException("unexpected type (match contains): " + actual.type);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean actualWithinExpected() {
        switch (actual.type) {
            case STRING:
                String actString = actual.getValue();
                String expString = expected.getValue();
                return expString.contains(actString); // reversed: expected contains actual
            case LIST:
                int actListCount = actual.getListSize();
                int expListCount = expected.getListSize();
                if (actListCount > expListCount) {
                    return fail("actual array length is greater than expected - " + actListCount + ":" + expListCount);
                }
                List<String> notFoundItems = new ArrayList<>();
                // for each item in actual, check if it exists in expected
                for (int i = 0; i < actListCount; i++) {
                    boolean found = false;
                    Value actListValue = new Value(actual.getListElement(i));
                    int failuresBeforeSearch = failures.size();
                    for (int j = 0; j < expListCount; j++) {
                        Value expListValue = new Value(expected.getListElement(j));
                        Operation mo = new Operation(context.descend(i), Match.Type.EQUALS, actListValue, expListValue, matchEachEmptyAllowed);
                        mo.execute();
                        if (mo.pass) {
                            found = true;
                            break;
                        }
                    }
                    // Remove search failures - they are just "not this one, keep looking"
                    while (failures.size() > failuresBeforeSearch) {
                        failures.removeLast();
                    }
                    if (!found) {
                        notFoundItems.add(actListValue.getAsString());
                    }
                }
                if (!notFoundItems.isEmpty()) {
                    if (notFoundItems.size() == 1) {
                        return fail("expected does not contain actual item - " + notFoundItems.get(0));
                    }
                    return fail("expected does not contain actual items - " + notFoundItems);
                }
                return true;
            case MAP:
                Map<String, Object> actMap = actual.getValue();
                Map<String, Object> expMap = expected.getValue();
                // for each key in actual, check if it exists in expected with same value
                List<String> missingKeys = new ArrayList<>();
                List<String> failedKeys = new ArrayList<>();
                for (Map.Entry<String, Object> actEntry : actMap.entrySet()) {
                    String key = actEntry.getKey();
                    if (!expMap.containsKey(key)) {
                        missingKeys.add(key);
                    } else {
                        Value actValue = new Value(actEntry.getValue());
                        Value expValue = new Value(expMap.get(key));
                        Operation mo = new Operation(context.descend(key), Match.Type.EQUALS, actValue, expValue, matchEachEmptyAllowed);
                        mo.execute();
                        if (!mo.pass) {
                            failedKeys.add(key);
                        }
                    }
                }
                if (!missingKeys.isEmpty()) {
                    if (missingKeys.size() == 1) {
                        return fail("expected does not contain key - '" + missingKeys.getFirst() + "'");
                    }
                    return fail("expected does not contain keys - " + missingKeys);
                }
                if (!failedKeys.isEmpty()) {
                    if (failedKeys.size() == 1) {
                        return fail("match failed for name: '" + failedKeys.getFirst() + "'");
                    }
                    return fail("match failed for names: " + failedKeys);
                }
                return true;
            case XML:
                Map<String, Object> actXml = (Map<String, Object>) Xml.toObject(actual.getValue());
                Map<String, Object> expXml = (Map<String, Object>) Xml.toObject(expected.getValue());
                // Recursively check using WITHIN with the converted maps
                Operation mo = new Operation(context, Match.Type.WITHIN, new Value(actXml), new Value(expXml), matchEachEmptyAllowed);
                mo.execute();
                return mo.pass;
            default:
                throw new RuntimeException("unexpected type (match within): " + actual.type);
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

    boolean pass() {
        pass = true;
        return true;
    }

    boolean fail(String reason) {
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

    /**
     * Returns a Result with both string message and structured failures.
     */
    Result getResult() {
        if (pass) {
            return Result.PASS;
        }
        List<Result.Failure> structuredFailures = collectStructuredFailures(this);
        String message = collectFailureReasons(this);
        return Result.fail(message, structuredFailures);
    }

    private static List<Result.Failure> collectStructuredFailures(Operation root) {
        List<Result.Failure> result = new ArrayList<>();
        List<Operation> failuresCopy = new ArrayList<>(root.failures);
        Collections.reverse(failuresCopy);
        Set<String> previousPaths = new HashSet<>();
        for (Operation mo : failuresCopy) {
            if (previousPaths.contains(mo.context.path) || mo.isXmlAttributeOrMap()) {
                continue;
            }
            previousPaths.add(mo.context.path);
            result.add(new Result.Failure(
                    mo.context.path,
                    mo.failReason,
                    mo.actual.type,
                    mo.expected.type,
                    mo.actual.getValue(),
                    mo.expected.getValue(),
                    mo.context.depth
            ));
        }
        return result;
    }

    private boolean isXmlAttributeOrMap() {
        return context.xml && actual.isMap()
                && (context.name.equals("@") || actual.<Map>getValue().containsKey("_"));
    }

    private static String collectFailureReasons(Operation root) {
        StringBuilder sb = new StringBuilder();
        sb.append("match failed: ").append(root.type).append('\n');
        Collections.reverse(root.failures);
        Iterator<Operation> iterator = root.failures.iterator();
        Set<String> previousPaths = new HashSet<>();
        int index = 0;
        int prevDepth = -1;
        while (iterator.hasNext()) {
            Operation mo = iterator.next();
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
                Value expected = mo.expected.getSortedLike(mo.actual);
                sb.append(prefix).append(mo.actual.getWithinSingleQuotesIfString()).append('\n');
                sb.append(prefix).append(expected.getWithinSingleQuotesIfString()).append('\n');
            }
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean isContainsType(Match.Type t) {
        return t == Match.Type.CONTAINS || t == Match.Type.NOT_CONTAINS
                || t == Match.Type.CONTAINS_ANY || t == Match.Type.CONTAINS_ONLY
                || t == Match.Type.CONTAINS_DEEP || t == Match.Type.CONTAINS_ONLY_DEEP
                || t == Match.Type.CONTAINS_ANY_DEEP;
    }

}
