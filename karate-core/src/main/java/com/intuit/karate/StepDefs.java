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

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepDefs implements Actions {

    private static final Logger LOGGER = LoggerFactory.getLogger(StepDefs.class);

    public StepDefs() { // zero-arg constructor for IDE support
        this(getFeatureEnv(), new CallContext(null, true));
    }

    private static FeatureContext IDE_FEATURE_CONTEXT;

    private static FeatureContext getFeatureEnv() {
        if (IDE_FEATURE_CONTEXT == null) {
            String cwd = new File("").getAbsoluteFile().getPath();
            String javaCommand = System.getProperty("sun.java.command");
            String featurePath = FileUtils.getFeaturePath(javaCommand, cwd);
            if (featurePath == null) {
                LOGGER.warn("IDE runner - unable to derive feature file path, using: {}", cwd);
                IDE_FEATURE_CONTEXT = FeatureContext.forEnv();
            } else {
                File file = new File(featurePath);
                LOGGER.info("IDE runner - init karate env: {}", file);
                Feature feature = FeatureParser.parse(file);
                IDE_FEATURE_CONTEXT = new FeatureContext(feature, null);
            }
        } else {
            LOGGER.info("IDE runner - reusing karate env: {}", IDE_FEATURE_CONTEXT);
        }
        return IDE_FEATURE_CONTEXT;
    }

    private final Actions actions;

    public StepDefs(FeatureContext scriptEnv, CallContext callContext) {
        actions = new StepActions(scriptEnv, callContext);
    }

    @When(CONFIGURE_DOCSTRING)
    @Override
    public void configureDocstring(String key, String exp) {
        actions.configure(key, exp);
    }

    @When(CONFIGURE)
    @Override
    public void configure(String key, String exp) {
        actions.configure(key, exp);
    }

    @When(URL)
    @Override
    public void url(String expression) {
        actions.url(expression);
    }

    @When(PATH)
    @Override
    public void path(List<String> paths) {
        actions.path(paths);
    }

    @When(PARAM)
    @Override
    public void param(String name, List<String> values) {
        actions.param(name, values);
    }

    @When(PARAMS)
    @Override
    public void params(String expr) {
        actions.params(expr);
    }

    @When(COOKIE)
    @Override
    public void cookie(String name, String value) {
        actions.cookie(name, value);
    }

    @When(COOKIES)
    @Override
    public void cookies(String expr) {
        actions.cookies(expr);
    }

    @When(HEADER)
    @Override
    public void header(String name, List<String> values) {
        actions.header(name, values);
    }

    @When(HEADERS)
    @Override
    public void headers(String expr) {
        actions.headers(expr);
    }

    @When(FORM_FIELD)
    @Override
    public void formField(String name, List<String> values) {
        actions.formField(name, values);
    }

    @When(FORM_FIELDS)
    @Override
    public void formFields(String expr) {
        actions.formFields(expr);
    }

    @When(REQUEST_DOCSTRING)
    @Override
    public void requestDocstring(String body) {
        actions.requestDocstring(body);
    }

    @When(REQUEST)
    @Override
    public void request(String body) {
        actions.request(body);
    }

    @When(TABLE) // ** data-table **
    public void table(String name, DataTable table) {
        table(name, table.asMaps(String.class, String.class));
    }

    @Override
    public void table(String name, List<Map<String, String>> table) {
        actions.table(name, table);
    }

    @When(REPLACE_TABLE) // ** data-table **
    public void replace(String name, DataTable table) {
        replace(name, table.asMaps(String.class, String.class));
    }

    @Override
    public void replace(String name, List<Map<String, String>> table) {
        actions.replace(name, table);
    }

    @When(REPLACE)
    @Override
    public void replace(String name, String token, String value) {
        actions.replace(name, token, value);
    }

    @When(DEF_DOCSTRING)
    @Override
    public void defDocstring(String name, String expression) {
        actions.defDocstring(name, expression);
    }

    @When(DEF)
    @Override
    public void def(String name, String expression) {
        actions.def(name, expression);
    }

    @When(TEXT)
    @Override
    public void text(String name, String expression) {
        actions.text(name, expression);
    }

    @When(YAML)
    @Override
    public void yaml(String name, String expression) {
        actions.yaml(name, expression);
    }

    @When(COPY)
    @Override
    public void copy(String name, String expression) {
        actions.copy(name, expression);
    }

    @When(JSON)
    @Override
    public void json(String name, String expression) {
        actions.json(name, expression);
    }

    @When(STRING)
    @Override
    public void string(String name, String expression) {
        actions.string(name, expression);
    }

    @When(XML)
    @Override
    public void xml(String name, String expression) {
        actions.xml(name, expression);
    }

    @When(XMLSTRING)
    @Override
    public void xmlstring(String name, String expression) {
        actions.xmlstring(name, expression);
    }

    @When(ASSERT)
    @Override
    public void assertTrue(String expression) {
        actions.assertTrue(expression);
    }

    @When(METHOD)
    @Override
    public void method(String method) {
        actions.method(method);
    }

    @When(SOAP_ACTION)
    @Override
    public void soapAction(String action) {
        actions.soapAction(action);
    }

    @When(MULTIPART_ENTITY)
    @Override
    public void multipartEntity(String value) {
        actions.multipartEntity(value);
    }

    @When(MULTIPART_FIELD)
    @Override
    public void multipartField(String name, String value) {
        actions.multipartField(name, value);
    }

    @When(MULTIPART_FIELDS)
    @Override
    public void multipartFields(String expr) {
        actions.multipartFields(expr);
    }

    @When(MULTIPART_FILE)
    @Override
    public void multipartFile(String name, String value) {
        actions.multipartFile(name, value);
    }

    @When(MULTIPART_FILES)
    @Override
    public void multipartFiles(String expr) {
        actions.multipartFiles(expr);
    }

    @When(PRINT)
    @Override
    public void print(List<String> exps) {
        actions.print(exps);
    }

    @When(STATUS)
    @Override
    public void status(int status) {
        actions.status(status);
    }

    @When(MATCH_EQUALS_DOCSTRING)
    @Override
    public void matchEqualsDocstring(String each, String name, String path, String eqSymbol, String expected) {
        actions.matchEqualsDocstring(each, name, path, eqSymbol, expected);
    }

    @When(MATCH_CONTAINS_DOCSTRING)
    @Override
    public void matchContainsDocstring(String each, String name, String path, String not, String only, String expected) {
        actions.matchContainsDocstring(each, name, path, not, only, expected);
    }

    @When(MATCH_EQUALS)
    @Override
    public void matchEquals(String each, String name, String path, String eqSymbol, String expected) {
        actions.matchEquals(each, name, path, eqSymbol, expected);
    }

    @When(MATCH_CONTAINS)
    @Override
    public void matchContains(String each, String name, String path, String not, String only, String expected) {
        actions.matchContains(each, name, path, not, only, expected);
    }

    @When(SET_DOCSTRING)
    @Override
    public void setDocstring(String name, String path, String value) {
        actions.setDocstring(name, path, value);
    }

    @When(SET)
    @Override
    public void set(String name, String path, String value) {
        actions.set(name, path, value);
    }

    @When(SET_TABLE) // ** data-table **
    public void set(String name, String path, DataTable table) {
        set(name, path, table.asMaps(String.class, String.class));
    }

    @Override
    public void set(String name, String path, List<Map<String, String>> table) {
        actions.set(name, path, table);
    }

    @When(REMOVE)
    @Override
    public void remove(String name, String path) {
        actions.remove(name, path);
    }

    @When(CALL)
    @Override
    public void call(String name, String arg) {
        actions.call(name, arg);
    }

    @When(CALLONCE)
    @Override
    public void callonce(String name, String arg) {
        actions.callonce(name, arg);
    }

    @When(EVAL)
    @Override
    public void eval(String exp) {
        actions.eval(exp);
    }

    @When(EVAL_DOCSTRING)
    @Override
    public void evalDocstring(String exp) {
        actions.evalDocstring(exp);
    }

}
