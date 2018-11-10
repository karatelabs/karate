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
import java.util.ArrayList;
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

    public static Match withHttp(Logger logger) {
        if (logger == null) {
            logger = new Logger();
        }
        return new Match(logger, null);
    }

    public Match(String exp) {
        this(null, exp);
    }

    public Match() {
        this(null, null);
    }

    private Match(Logger logger, String exp) {
        FeatureContext featureContext = FeatureContext.forLogger(logger);
        String httpClass = logger == null ? DummyHttpClient.class.getName() : null;
        CallContext callContext = new CallContext(null, null, 0, null, -1, false, false,
                httpClass, null, false);
        context = new ScenarioContext(featureContext, callContext);
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

    public Match jsonPath(String exp) {
        prevValue = Script.evalKarateExpression(exp, context);
        return this;
    }

    public ScriptValue value() {
        return prevValue;
    }

    public boolean statusIs(Integer expected) {
        prevValue = Script.evalKarateExpression("responseStatus", context);
        return expected.equals(prevValue.getValue());
    }

    public boolean isBooleanTrue() {
        return prevValue.isBooleanTrue();
    }

    public String asString() {
        return prevValue.getAsString();
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

    private static String quote(String exp) {
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

    // http ====================================================================
    //
    public Http http() {
        return new Http(this);
    }

    public Match url(String url) {
        context.url(quote(url));
        return this;
    }

    public Match path(String... paths) {
        List<String> list = new ArrayList(paths.length);
        for (String p : paths) {
            list.add(quote(p));
        }
        context.path(list);
        return this;
    }

    public Match httpGet() {
        context.method("get");
        return this;
    }

    public Match httpPost(ScriptValue body) {
        context.request(body);
        context.method("post");
        return this;
    }

    public Match httpPost(Object body) {
        context.request(new ScriptValue(body).getAsString());
        context.method("post");
        return this;
    }

    public Match httpDelete() {
        context.method("delete");
        return this;
    }

    public Match response() {
        jsonPath("response");
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

}
