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
import com.intuit.karate.http.DummyHttpClient;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONValue;

/**
 *
 * @author pthomas3
 */
public class Match {

    protected final ScenarioContext context;
    private ScriptValue prevValue = ScriptValue.NULL;

    public static Match forHttp(LogAppender appender) {
        return new Match(appender, null);
    }  

    public static Match forHttp(ScenarioContext context) {
        return new Match(context);
    }     
    
    public Match(String exp) {
        this(null, exp);
    }

    public Match() {
        this(null, null);
    }

    public void clear() {
        prevValue = ScriptValue.NULL;
    }

    public static Match init(Object o) {
        Match match = new Match(null, null);
        match.prevValue = new ScriptValue(o);
        return match;
    }
    
    private Match(ScenarioContext context) {
        this.context = context;
    }

    private Match(LogAppender appender, String exp) {
        FeatureContext featureContext = FeatureContext.forEnv();
        String httpClass = appender == null ? DummyHttpClient.class.getName() : null;
        CallContext callContext = new CallContext(null, null, 0, null, -1, false, false,
                httpClass, null, null, false);
        context = new ScenarioContext(featureContext, callContext, null, appender);
        if (exp != null) {
            prevValue = Script.evalKarateExpression(exp, context);
            if (prevValue.isMapLike()) {
                putAll(prevValue.evalAsMap(context));
            }
        }
    }

    private void handleFailure(AssertionResult ar) {
        if (!ar.pass) {
            context.logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

    public Match text(String name, String exp) {
        prevValue = Script.assign(AssignType.TEXT, name, exp, context, false);
        return this;
    }

    public Match putAll(Map<String, Object> map) {
        if (map != null) {
            map.forEach((k, v) -> context.vars.put(k, v));
        }
        return this;
    }

    public Match eval(String exp) {
        prevValue = Script.evalKarateExpression(exp, context);
        return this;
    }

    public Match def(String name, String exp) {
        prevValue = Script.assign(AssignType.AUTO, name, exp, context, false);
        return this;
    }

    public Match def(String name, Object o) {
        prevValue = context.vars.put(name, o);
        return this;
    }
    
    public Match get(String key) {
        prevValue = context.vars.get(key);
        return this;
    }

    public Match jsonPath(String exp) {
        prevValue = Script.evalKarateExpression(exp, context);
        return this;
    }  

    public ScriptValue value() {
        return prevValue;
    }

    public boolean isBooleanTrue() {
        return prevValue.isBooleanTrue();
    }

    public String asString() {
        return prevValue.getAsString();
    }
    
    public int asInt() {
        return prevValue.getAsInt();
    }    

    public <T> T asType(Class<T> clazz) {
        return prevValue.getValue(clazz);
    }

    public Map<String, Object> asMap(String exp) {
        eval(exp);
        return prevValue.getAsMap();
    }

    public Map<String, Object> asMap() {
        return prevValue == null ? null : prevValue.getAsMap();
    }

    public String asJson() {
        return JsonUtils.toJson(prevValue.getAsMap());
    }

    public Map<String, Object> allAsMap() {
        return context.vars.toPrimitiveMap();
    }

    public ScriptValueMap vars() {
        return context.vars;
    }

    public String allAsJson() {
        return JsonUtils.toJson(context.vars.toPrimitiveMap());
    }

    public List<Object> asList(String exp) {
        eval(exp);
        return prevValue.getAsList();
    }

    public List asList() {
        return prevValue.getAsList();
    }

    public Match equalsText(String exp) {
        return matchText(exp, MatchType.EQUALS);
    }

    public static String quote(String exp) {
        return exp == null ? "null" : "\"" + JSONValue.escape(exp) + "\"";
    }

    public Match matchText(String exp, MatchType matchType) {
        return match(quote(exp), matchType);
    }

    private Match match(String exp, MatchType matchType) {
        AssertionResult result = Script.matchScriptValue(matchType, prevValue, "$", exp, context);
        handleFailure(result);
        return this;
    }

    public Match equals(String exp) {
        return match(exp, MatchType.EQUALS);
    }

    public Match contains(String exp) {
        return match(exp, MatchType.CONTAINS);
    }

    // ideally 'equals' but conflicts with Java
    public Match equalsObject(Object o) {
        return match(o, MatchType.EQUALS);
    }

    public Match contains(Object o) {
        return match(o, MatchType.CONTAINS);
    }

    private Match match(Object o, MatchType matchType) {
        Script.matchNestedObject('.', "$", matchType,
                prevValue.getValue(), null, null, o, context);
        return this;
    }

    // static ==================================================================
    //
    public static Match equals(Object o, String exp) {
        return match(o, exp, MatchType.EQUALS);
    }

    public static Match equalsText(Object o, String exp) {
        return matchText(o, exp, MatchType.EQUALS);
    }

    public static Match contains(Object o, String exp) {
        return match(o, exp, MatchType.CONTAINS);
    }

    public static Match containsText(Object o, String exp) {
        return matchText(o, exp, MatchType.CONTAINS);
    }

    private static Match match(Object o, String exp, MatchType matchType) {
        Match m = new Match();
        m.prevValue = new ScriptValue(o);
        return m.match(exp, matchType);
    }

    private static Match matchText(Object o, String exp, MatchType matchType) {
        Match m = new Match();
        m.prevValue = new ScriptValue(o);
        return m.matchText(exp, matchType);
    }

    public Match config(String key, String value) {
        context.configure(key, value);
        return this;
    }
    
    public Match config(Map<String, Object> config) {
        config.forEach((k, v) -> context.configure(k, new ScriptValue(v)));
        return this;
    }

}
