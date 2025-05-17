package com.intuit.karate;

import com.intuit.karate.graal.JsValue;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intuit.karate.MatchOperation.REGEX;

public interface MatchOperator {

    public boolean execute(MatchOperation operation);

    class EachOperator implements MatchOperator {
        private final MatchOperator delegate;
        private final boolean matchEachEmptyAllowed;

        EachOperator(MatchOperator delegate, boolean matchEachEmptyAllowed) {
            this.delegate = delegate;
            this.matchEachEmptyAllowed = matchEachEmptyAllowed;
        }

        public String toString() {
            return "EACH_"+delegate;
        }

        public boolean execute(MatchOperation operation) {
            Match.Value actual = operation.actual;
            Match.Context context = operation.context;
            if (actual.isList()) {
                List list = actual.getValue();
                if (list.isEmpty() && !matchEachEmptyAllowed) {
                    return operation.fail("match each failed, empty array / list");
                }
                int count = list.size();
                for (int i = 0; i < count; i++) {
                    Object o = list.get(i);
                    context.JS.put("_$", o);
                    MatchOperation mo = new MatchOperation(context.descend(i), delegate, new Match.Value(o), operation.expected);
                    mo.execute();
                    context.JS.bindings.removeMember("_$");
                    if (!mo.pass) {
                        return operation.fail("match each failed at index " + i);
                    }
                }
                // if we reached here all / each LHS items completed successfully
                return true;
            } else {
                return operation.fail("actual is not an array or list");
            }
        }

    }

    class NotOperator implements MatchOperator {
        private final CoreOperator delegate;
        private final String failureMessage;

        NotOperator(CoreOperator delegate, String failureMessage) {
            this.delegate = delegate;
            this.failureMessage = failureMessage;
        }

        public String toString() {
            return "NOT_"+delegate;
        }

        public boolean execute(MatchOperation operation) {
            Match.Value expected = operation.expected;
            // TODO Possible regression: pre 2515 would only apply this hack to CONTAINS and not CONTAINS_DEEP
            if (delegate.isContains() && expected.isMap() && expected.<Map<?, ?>>getValue().isEmpty()) {
                return true; // hack alert: support for match some_map not contains {}
            }
            MatchOperation mo = new MatchOperation(operation.context, delegate, operation.actual, expected);
            mo.execute();
            if (!mo.pass) {
                return operation.pass();
            }
            return operation.fail(failureMessage);
        }
    }

    class CoreOperator implements MatchOperator {

        private final boolean isEquals;
        private final boolean isContains;
        private final boolean isContainsAny;
        private final boolean isContainsOnly;
        private final boolean isDeep;
        private final boolean matchEachEmptyAllowed;
        // NOt strictly required. We could create a new instance in childOperator but keeping it as an instance field
        // is a minor optimization.
        private final CoreOperator equalsOperator;

        private CoreOperator(boolean isEquals, boolean isContains, boolean isContainsAny, boolean isContainsOnly, boolean matchEachEmptyAllowed) {
            this(isEquals, isContains, isContainsAny, isContainsOnly, false, matchEachEmptyAllowed);
        }

        private CoreOperator(boolean isEquals, boolean isContains, boolean isContainsAny, boolean isContainsOnly, boolean isDeep, boolean matchEachEmptyAllowed) {
            this.isEquals = isEquals;
            this.isContains = isContains;
            this.isContainsAny = isContainsAny;
            this.isContainsOnly = isContainsOnly;
            this.isDeep = isDeep;
            this.matchEachEmptyAllowed = matchEachEmptyAllowed;
            this.equalsOperator = isEquals?this:equalsOperator(matchEachEmptyAllowed);
        }

        public boolean execute(MatchOperation operation) {
            Match.Value actual = operation.actual;
            Match.Value expected = operation.expected;
            Match.Context context = operation.context;
            if (actual.isNotPresent()) {
                if (!expected.isString() || !expected.getAsString().startsWith("#")) {
                    return operation.fail("actual path does not exist");
                }
            }
            boolean isContainsFamily = isContainsFamily();
            if (actual.type != expected.type) {
                if (isContainsFamily &&
                        // don't tamper with strings on the RHS that represent arrays or objects
                        (!expected.isList() && !(expected.isString() && expected.isArrayObjectOrReference()))) {
                    MatchOperation mo = new MatchOperation(context, this, actual, new Match.Value(Collections.singletonList(expected.getValue())));
                    mo.execute();
                    return mo.pass ? operation.pass() : operation.fail(mo.failReason);
                }
                if (expected.isXml() && actual.isMap()) {
                    // special case, auto-convert rhs
                    MatchOperation mo = new MatchOperation(context, this, actual, new Match.Value(XmlUtils.toObject(expected.getValue(), true)));
                    mo.execute();
                    return mo.pass ? operation.pass() : operation.fail(mo.failReason);
                }
                if (expected.isString()) {
                    String expStr = expected.getValue();
                    if (!expStr.startsWith("#")) { // edge case if rhs is macro
                        return operation.fail("data types don't match");
                    }
                } else {
                    return operation.fail("data types don't match");
                }
            }
            if (expected.isString()) {
                String expStr = expected.getValue();
                if (expStr.startsWith("#")) {
                    return macroEqualsExpected(operation, expStr) ? operation.pass() : operation.fail(null);
                }
            }
            if (isEquals()) {
                return actualEqualsExpected(operation) ? operation.pass() : operation.fail("not equal");
            } else if (isContainsFamily) {
                return actualContainsExpected(operation) ? operation.pass() : operation.fail("actual does not contain expected");
            }
            throw new RuntimeException("unexpected match operator: " + this);
        }


        private boolean macroEqualsExpected(MatchOperation operation, String expStr) {
            Match.Value actual = operation.actual;
            Match.Value expected = operation.expected;
            Match.Context context = operation.context;

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
                    MatchOperator nestedOperator = nestedType.operator(isMatchEachEmptyAllowed());
                    if (isContainsFamily() && actual.isList()) { // special case, look for partial maps within list
                        nestedOperator = macroOperator(nestedOperator);
                    }
                    context.JS.put("$", context.root.actual.getValue());
                    context.JS.put("_", actual.getValue());
                    JsValue jv = context.JS.eval(macro);
                    context.JS.bindings.removeMember("$");
                    context.JS.bindings.removeMember("_");
                    MatchOperation mo = new MatchOperation(context, nestedOperator, actual, new Match.Value(jv.getValue()));
                    return mo.execute();
                } else if (macro.startsWith("[")) {
                    int closeBracketPos = macro.indexOf(']');
                    if (closeBracketPos != -1) { // array, match each
                        if (!actual.isList()) {
                            return operation.fail("actual is not an array");
                        }
                        if (closeBracketPos > 1) {
                            String bracketContents = macro.substring(1, closeBracketPos);
                            List listAct = actual.getValue();
                            int listSize = listAct.size();
                            context.JS.put("$", context.root.actual.getValue());
                            context.JS.put("_", listSize);
                            String sizeExpr;
                            if (containsPlaceholderUnderscore(bracketContents)) { // #[_ < 5]
                                sizeExpr = bracketContents;
                            } else { // #[5] | #[$.foo]
                                sizeExpr = bracketContents + " == _";
                            }
                            JsValue jv = context.JS.eval(sizeExpr);
                            context.JS.bindings.removeMember("$");
                            context.JS.bindings.removeMember("_");
                            if (!jv.isTrue()) {
                                return operation.fail("actual array length is " + listSize);
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
                                    MatchOperation mo = new MatchOperation(context, Match.Type.EACH_EQUALS.operator(isMatchEachEmptyAllowed()), actual, new Match.Value(macro));
                                    mo.execute();
                                    return mo.pass ? operation.pass() : operation.fail("all array elements matched");
                                } else { // schema reference
                                    Match.Type nestedType = macroToMatchType(true, macro); // match each
                                    int startPos = matchTypeToStartPos(nestedType);
                                    macro = macro.substring(startPos);
                                    JsValue jv = context.JS.eval(macro);
                                    MatchOperation mo = new MatchOperation(context, nestedType.operator(isMatchEachEmptyAllowed()), actual, new Match.Value(jv.getValue()));
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
                                    return operation.fail(mr.message);
                                }
                            }
                        } else if (!validatorName.startsWith(REGEX)) { // expected is a string that happens to start with "#"
                            String actualValue = actual.getValue();
                            // TODO possible regression: pre 2515 checked only CONTAINS and not CONTAINS_DEEP.
                            return isContains()?actualValue.contains(expStr):actualValue.equals(expStr);
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
                            return operation.fail("evaluated to 'false'");
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

        private boolean actualEqualsExpected(MatchOperation operation) {
            Match.Value actual = operation.actual;
            Match.Value expected = operation.expected;
            Match.Context context = operation.context;
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
                        return operation.fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                    }
                    for (int i = 0; i < actListCount; i++) {
                        Match.Value actListValue = new Match.Value(actList.get(i));
                        Match.Value expListValue = new Match.Value(expList.get(i));
                        MatchOperation mo = new MatchOperation(context.descend(i), equalsOperator, actListValue, expListValue);
                        mo.execute();
                        if (!mo.pass) {
                            return operation.fail("array match failed at index " + i);
                        }
                    }
                    return true;
                case MAP:
                    Map<String, Object> actMap = actual.getValue();
                    Map<String, Object> expMap = expected.getValue();
                    return matchMapValues(actMap, expMap, operation);
                case XML:
                    Map<String, Object> actXml = (Map) XmlUtils.toObject(actual.getValue(), true);
                    Map<String, Object> expXml = (Map) XmlUtils.toObject(expected.getValue(), true);
                    return matchMapValues(actXml, expXml, operation);
                case OTHER:
                    return actual.getValue().equals(expected.getValue());
                default:
                    throw new RuntimeException("unexpected type (match equals): " + actual.type);
            }
        }

        private boolean matchMapValues(Map<String, Object> actMap, Map<String, Object> expMap, MatchOperation operation) { // combined logic for equals and contains
            if (actMap.size() > expMap.size() && (isEquals() || isContainsOnly())) {
                int sizeDiff = actMap.size() - expMap.size();
                Map<String, Object> diffMap = new LinkedHashMap(actMap);
                for (String key : expMap.keySet()) {
                    diffMap.remove(key);
                }
                return operation.fail("actual has " + sizeDiff + " more key(s) than expected - " + JsonUtils.toJson(diffMap));
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
                            if (isContainsAny()) {
                                return true; // exit early
                            }
                            unMatchedKeysExp.remove(key);
                            if (unMatchedKeysExp.isEmpty()) {
                                if (isContains()) {
                                    return true; // all expected keys matched
                                }
                            }
                            continue;
                        }
                    }
                    if (!isContainsAny()) {
                        return operation.fail("actual does not contain key - '" + key + "'");
                    }
                }
                Match.Value childActValue = new Match.Value(actMap.get(key));
                MatchOperator childMatchType = childOperator(childActValue);
                MatchOperation mo = new MatchOperation(operation.context.descend(key), childMatchType, childActValue, new Match.Value(childExp));
                mo.execute();
                if (mo.pass) {
                    if (isContainsAny()) {
                        return true; // exit early
                    }
                    unMatchedKeysExp.remove(key);
                    if (unMatchedKeysExp.isEmpty()) {
                        if (isContains()) {
                            return true; // all expected keys matched
                        }
                    }
                    unMatchedKeysAct.remove(key);
                } else if (isEquals()) {
                    return operation.fail("match failed for name: '" + key + "'");
                }
            }
            if (isContainsAny()) {
                return unMatchedKeysExp.isEmpty() ? true : operation.fail("no key-values matched");
            }
            if (unMatchedKeysExp.isEmpty()) {
                if (isContains()) {
                    return true; // all expected keys matched, expMap was empty in the first place
                }
                // Special hack in pre 2515 to support match some_map not contains {} is now handled in execute() directly
            }
            if (!unMatchedKeysExp.isEmpty()) {
                return operation.fail("all key-values did not match, expected has un-matched keys - " + unMatchedKeysExp);
            }
            if (!unMatchedKeysAct.isEmpty()) {
                return operation.fail("all key-values did not match, actual has un-matched keys - " + unMatchedKeysAct);
            }
            return true;
        }

        private boolean actualContainsExpected(MatchOperation operation) {
            Match.Value actual = operation.actual;
            Match.Value expected = operation.expected;
            Match.Context context = operation.context;
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
                    // visited array used to handle duplicates
                    boolean[] actVisitedList = new boolean[actListCount];
                    if (!isContainsAny() && expListCount > actListCount) {
                        return operation.fail("actual array length is less than expected - " + actListCount + ":" + expListCount);
                    }
                    if (isContainsOnly() && expListCount != actListCount) {
                        return operation.fail("actual array length is not equal to expected - " + actListCount + ":" + expListCount);
                    }
                    for (Object exp : expList) { // for each item in the expected list
                        boolean found = false;
                        Match.Value expListValue = new Match.Value(exp);
                        for (int i = 0; i < actListCount; i++) {
                            Match.Value actListValue = new Match.Value(actList.get(i));
                            MatchOperator childMatchType = childOperator(actListValue);
                            MatchOperation mo = new MatchOperation(context.descend(i), childMatchType, actListValue, expListValue);
                            mo.execute();
                            if (mo.pass) {
                                if (isContainsAny()) {
                                    return true; // exit early
                                }
                                // contains only : If element is found also check its occurrence in actVisitedList
                                // TODO Possible regression: pre 2515, only contains only (and not contains only deep) was checked
                                else if(isContainsOnly()) {
                                    // if not yet visited
                                    if(!actVisitedList[i]) {
                                        // mark it visited
                                        actVisitedList[i]  = true;
                                        found = true;
                                        break; // next item in expected list
                                    }
                                    // else do nothing does not consider it a match
                                }
                                else {
                                    found = true;
                                    break; // next item in expected list
                                }
                            }
                        }
                        if (!found && !isContainsAny()) { // if we reached here, all items in the actual list were scanned
                            return operation.fail("actual array does not contain expected item - " + expListValue.getAsString());
                        }
                    }
                    if (isContainsAny()) {
                        return operation.fail("actual array does not contain any of the expected items");
                    }
                    return true; // if we reached here, all items in the expected list were found
                case MAP:
                    Map<String, Object> actMap = actual.getValue();
                    Map<String, Object> expMap = expected.getValue();
                    return matchMapValues(actMap, expMap, operation);
                case XML:
                    Map<String, Object> actXml = (Map) XmlUtils.toObject(actual.getValue());
                    Map<String, Object> expXml = (Map) XmlUtils.toObject(expected.getValue());
                    return matchMapValues(actXml, expXml, operation);
                default:
                    throw new RuntimeException("unexpected type (match contains): " + actual.type);
            }
        }

        CoreOperator deep() {
            return new CoreOperator(isEquals, isContains, isContainsAny, isContainsOnly, true, matchEachEmptyAllowed);
        }

        static CoreOperator equalsOperator(boolean matchEachEmptyAllowed) {
            return new CoreOperator(true, false, false, false, matchEachEmptyAllowed);
        }

        static CoreOperator containsOperator(boolean matchEachEmptyAllowed) {
            return new CoreOperator(false, true, false, false, matchEachEmptyAllowed);
        }

        static CoreOperator containsAnyOperator(boolean matchEachEmptyAllowed) {
            return new CoreOperator(false, false, true, false, matchEachEmptyAllowed);
        }

        static CoreOperator containsOnlyOperator(boolean matchEachEmptyAllowed) {
            return new CoreOperator(false, false, false, true, matchEachEmptyAllowed);
        }

        boolean isEquals() {
            return isEquals;
        }

        boolean isContains() {
            return isContains;
        }

        boolean isContainsAny() {
            return isContainsAny;
        }

        boolean isContainsOnly() {
            return isContainsOnly;
        }

        boolean isContainsFamily() {
            return isContains() || isContainsOnly() || isContainsAny();
        }

        boolean isMatchEachEmptyAllowed() {
            return matchEachEmptyAllowed;
        }

        MatchOperator childOperator(Match.Value value) {
            // TODO why force equals here?
            // match [['foo'], ['bar']] contains deep 'fo'
            // will fail if leaves are matched with equals, but should it not pass?
            return isDeep && value.isMapOrListOrXml()?this:equalsOperator;
        }

        /**
         * Hook to adjust the operator used for macro.
         * <p>
         * Whatever operator the user specified (^, ^+, ...) will be supplied as the specifiedOperator parameter.
         * However, the Contains operator may need to tweak it a little bit.
         * <p>
         * Given
         * * def actual = [{ a: 1, b: 'x' }, { a: 2, b: 'y' }]
         * * def part = { a: 1 }
         * * match actual contains '#(^part)'
         * <p>
         * specifiedOperator will be Contains. However, in this example:
         * - the specified operator will be applied while processing the list
         * - child operators will be applied while processing the objects within the list.
         * And per {@link #childOperator(Match.Value)}, Contains' child operators are Equals, so the code would end up
         * trying to match { a: 1, b: 'x' } equals { a: 1 }, which would fail.
         * <p>
         * What we really want here is to keep both Contains, the one from the match instruction and the one from the macro.
         * This method does just that by creating a custom Operator that will apply 2 contains.
         * <p>
         * Note that should a third processing be needed (e.g. because the objects in actual contain other objects),
         * it would use the child operator of the child operator, which would be Equals.
         * This behavior differs from the Legacy implementation that would force a Deep Contains which would in turn cause issue #2515.
         * <p>
         * However, Contains Deep may still be specified at user's discretion e.g. to handle objects in objects in lists.
         */
        protected MatchOperator macroOperator(MatchOperator specifiedOperator) {
            if (isContainsFamily()) {
                return isDeep ? this : new CoreOperator(false, isContains(), isContainsAny(), isContainsOnly(), isMatchEachEmptyAllowed()) {
                    protected MatchOperator childOperator(Match.Value actual) {
                        return specifiedOperator;
                    }
                };
            }
            return specifiedOperator;
        }

        public String toString() {
            String operatorString = isEquals?"EQUALS":isContains?"CONTAINS":isContainsAny?"CONTAINS_ANY":"CONTAINS_ONLY";
            return isDeep?operatorString+"_DEEP":operatorString;
        }
    }

}
