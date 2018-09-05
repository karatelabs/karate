/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class StepActions implements Actions {

    public final ScenarioContext context;
    public final CallContext callContext;

    public StepActions(FeatureContext featureContext, CallContext callContext) {
        this.callContext = callContext;
        context = new ScenarioContext(featureContext, callContext);
    }

    @Override
    public void assertTrue(String expression) {
        context.assertTrue(expression);
    }

    @Override
    public void call(String name, String arg) {
        context.call(false, name, arg);
    }

    @Override
    public void callonce(String name, String arg) {
        context.call(true, name, arg);
    }

    @Override
    public void json(String name, String expression) {
        context.assign(AssignType.JSON, name, expression);
    }

    @Override
    public void string(String name, String expression) {
        context.assign(AssignType.STRING, name, expression);
    }

    @Override
    public void xml(String name, String expression) {
        context.assign(AssignType.XML, name, expression);
    }

    @Override
    public void xmlstring(String name, String expression) {
        context.assign(AssignType.XML_STRING, name, expression);
    }

    @Override
    public void configure(String key, String exp) {
        context.configure(key, exp);
    }

    @Override
    public void configureDocstring(String key, String exp) {
        context.configure(key, exp);
    }

    @Override
    public void cookie(String name, String value) {
        context.cookie(name, value);
    }

    @Override
    public void cookies(String expr) {
        context.cookies(expr);
    }

    @Override
    public void copy(String name, String expression) {
        context.assign(AssignType.COPY, name, expression);
    }

    @Override
    public void def(String name, String expression) {
        context.assign(AssignType.AUTO, name, expression);
    }

    @Override
    public void defDocstring(String name, String expression) {
        context.assign(AssignType.AUTO, name, expression);
    }

    @Override
    public void eval(String exp) {
        context.eval(exp);
    }

    @Override
    public void evalDocstring(String exp) {
        context.eval(exp);
    }

    @Override
    public void formField(String name, List<String> values) {
        context.formField(name, values);
    }

    @Override
    public void formFields(String expr) {
        context.formFields(expr);
    }

    @Override
    public void header(String name, List<String> values) {
        context.header(name, values);
    }

    @Override
    public void headers(String expr) {
        context.headers(expr);
    }

    private static MatchType toMatchType(String eqSymbol, String each, String notContains, String only, boolean contains) {
        boolean notEquals = eqSymbol.startsWith("!");
        if (each == null) {
            if (notContains != null) {
                return MatchType.NOT_CONTAINS;
            }
            if (only != null) {
                return only.contains("only") ? MatchType.CONTAINS_ONLY : MatchType.CONTAINS_ANY;
            }
            return contains ? MatchType.CONTAINS : notEquals ? MatchType.NOT_EQUALS : MatchType.EQUALS;
        } else {
            if (notContains != null) {
                return MatchType.EACH_NOT_CONTAINS;
            }
            if (only != null) {
                return only.contains("only") ? MatchType.EACH_CONTAINS_ONLY : MatchType.EACH_CONTAINS_ANY;
            }
            return contains ? MatchType.EACH_CONTAINS : notEquals ? MatchType.EACH_NOT_EQUALS : MatchType.EACH_EQUALS;
        }
    }

    @Override
    public void matchContains(String each, String name, String path, String not, String only, String expected) {
        MatchType mt = toMatchType("==", each, not, only, true);
        context.match(mt, name, path, expected);
    }

    @Override
    public void matchContainsDocstring(String each, String name, String path, String not, String only, String expected) {
        matchContains(each, name, path, not, only, expected);
    }

    private static void validateEqualsSign(String eqSymbol) {
        if (eqSymbol.equals("=")) {
            throw new RuntimeException("use '==' for match (not '=')");
        }
    }

    @Override
    public void matchEquals(String each, String name, String path, String eqSymbol, String expected) {
        validateEqualsSign(eqSymbol);
        MatchType mt = toMatchType(eqSymbol, each, null, null, false);
        context.match(mt, name, path, expected);
    }

    @Override
    public void matchEqualsDocstring(String each, String name, String path, String eqSymbol, String expected) {
        matchEquals(each, name, path, eqSymbol, expected);
    }

    @Override
    public void method(String method) {
        context.method(method);
    }

    @Override
    public void multipartEntity(String value) {
        context.multipartField(null, value);
    }

    @Override
    public void multipartFiles(String expr) {
        context.multiPartFiles(expr);
    }

    @Override
    public void multipartField(String name, String value) {
        context.multipartField(name, value);
    }

    @Override
    public void multipartFields(String expr) {
        context.multipartFields(expr);
    }

    @Override
    public void multipartFile(String name, String value) {
        context.multipartFile(name, value);
    }

    @Override
    public void param(String name, List<String> values) {
        context.param(name, values);
    }

    @Override
    public void params(String expr) {
        context.params(expr);
    }

    @Override
    public void path(List<String> paths) {
        context.path(paths);
    }

    @Override
    public void print(List<String> exps) {
        context.print(exps);
    }

    @Override
    public void remove(String name, String path) {
        context.remove(name, path);
    }

    @Override
    public void replace(String name, List<Map<String, String>> table) {
        context.replace(name, table);
    }

    @Override
    public void replace(String name, String token, String value) {
        context.replace(name, token, value);
    }

    @Override
    public void request(String body) {
        context.request(body);
    }

    @Override
    public void requestDocstring(String body) {
        context.request(body);
    }

    @Override
    public void set(String name, String path, String value) {
        context.set(name, path, value);
    }

    @Override
    public void setDocstring(String name, String path, String value) {
        context.set(name, path, value);
    }

    @Override
    public void set(String name, String path, List<Map<String, String>> table) {
        context.set(name, path, table);
    }

    @Override
    public void soapAction(String action) {
        context.soapAction(action);
    }

    @Override
    public void status(int status) {
        context.status(status);
    }

    @Override
    public void table(String name, List<Map<String, String>> table) {
        context.table(name, table);
    }

    @Override
    public void text(String name, String expression) {
        context.assign(AssignType.TEXT, name, expression);
    }

    @Override
    public void url(String expression) {
        context.url(expression);
    }

    @Override
    public void yaml(String name, String expression) {
        context.assign(AssignType.YAML, name, expression);
    }

}
