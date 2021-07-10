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

import com.intuit.karate.core.AssignType;
import com.intuit.karate.core.Action;
import com.intuit.karate.core.ScenarioEngine;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioActions implements Actions {

    private final ScenarioEngine engine;

    public ScenarioActions(ScenarioEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean isFailed() {
        return engine.isFailed();
    }

    @Override
    public Throwable getFailedReason() {
        return engine.getFailedReason();
    }

    @Override
    public boolean isAborted() {
        return engine.isAborted();
    }

    @Override
    @When("^configure ([^\\s]+) =$")
    public void configureDocstring(String key, String exp) {
        engine.configure(key, exp);
    }

    @Override
    @When("^configure ([^\\s]+) = (.+)")
    public void configure(String key, String exp) {
        engine.configure(key, exp);
    }

    @Override
    @When("^url (.+)")
    public void url(String exp) {
        engine.url(exp);
    }

    @Override
    @When("^path (.+)")
    public void path(String exp) {
        engine.path(exp);
    }

    @Override
    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, String exp) {
        engine.param(name, exp);
    }

    @Override
    @When("^params (.+)")
    public void params(String exp) {
        engine.params(exp);
    }

    @Override
    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        engine.cookie(name, value);
    }

    @Override
    @When("^cookies (.+)")
    public void cookies(String exp) {
        engine.cookies(exp);
    }

    @Override
    @When("^csv (.+) = (.+)")
    public void csv(String name, String exp) {
        engine.assign(AssignType.CSV, name, exp);
    }

    @Override
    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, String exp) {
        engine.header(name, exp);
    }

    @Override
    @When("^headers (.+)")
    public void headers(String exp) {
        engine.headers(exp);
    }

    @Override
    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, String exp) {
        engine.formField(name, exp);
    }

    @Override
    @When("^form fields (.+)")
    public void formFields(String exp) {
        engine.formFields(exp);
    }

    @Override
    @When("^request$")
    public void requestDocstring(String body) {
        engine.request(body);
    }

    @Override
    @When("^request (.+)")
    public void request(String body) {
        engine.request(body);
    }

    @When("^table (.+)")
    public void table(String name, DataTable table) {
        table(name, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^table (.+)")
    public void table(String name, List<Map<String, String>> table) {
        engine.table(name, table);
    }

    @When("^replace (\\w+)$")
    public void replace(String name, DataTable table) {
        replace(name, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^replace (\\w+)$")
    public void replace(String name, List<Map<String, String>> table) {
        engine.replaceTable(name, table);
    }

    @Override
    @When("^replace (\\w+).([^\\s]+) = (.+)")
    public void replace(String name, String token, String value) {
        engine.replace(name, token, value);
    }

    @Override
    @When("^def (.+) =$")
    public void defDocstring(String name, String exp) {
        engine.assign(AssignType.AUTO, name, exp);
    }

    @Override
    @When("^def (\\w+) = (.+)")
    public void def(String name, String exp) {
        engine.assign(AssignType.AUTO, name, exp);
    }

    @Override
    @When("^text (.+) =$")
    public void text(String name, String exp) {
        engine.assign(AssignType.TEXT, name, exp);
    }

    @Override
    @When("^yaml (.+) = (.+)")
    public void yaml(String name, String exp) {
        engine.assign(AssignType.YAML, name, exp);
    }

    @Override
    @When("^copy (.+) = (.+)")
    public void copy(String name, String exp) {
        engine.assign(AssignType.COPY, name, exp);
    }

    @Override
    @When("^json (.+) = (.+)")
    public void json(String name, String exp) {
        engine.assign(AssignType.JSON, name, exp);
    }

    @Override
    @When("^string (.+) = (.+)")
    public void string(String name, String exp) {
        engine.assign(AssignType.STRING, name, exp);
    }

    @Override
    @When("^xml (.+) = (.+)")
    public void xml(String name, String exp) {
        engine.assign(AssignType.XML, name, exp);
    }

    @Override
    @When("^xmlstring (.+) = (.+)")
    public void xmlstring(String name, String exp) {
        engine.assign(AssignType.XML_STRING, name, exp);
    }

    @Override
    @When("^bytes (.+) = (.+)")
    public void bytes(String name, String exp) {
        engine.assign(AssignType.BYTE_ARRAY, name, exp);
    }

    @Override
    @When("^assert (.+)")
    public void assertTrue(String exp) {
        engine.assertTrue(exp);
    }

    @Override
    @When("^method (\\w+)")
    public void method(String method) {
        engine.method(method);
    }

    @Override
    @When("^retry until (.+)")
    public void retry(String until) {
        engine.retry(until);
    }

    @Override
    @When("^soap action( .+)?")
    public void soapAction(String action) {
        engine.soapAction(action);
    }

    @Override
    @When("^multipart entity (.+)")
    public void multipartEntity(String value) {
        engine.multipartField(null, value);
    }

    @Override
    @When("^multipart field (.+) = (.+)")
    public void multipartField(String name, String value) {
        engine.multipartField(name, value);
    }

    @Override
    @When("^multipart fields (.+)")
    public void multipartFields(String exp) {
        engine.multipartFields(exp);
    }

    @Override
    @When("^multipart file (.+) = (.+)")
    public void multipartFile(String name, String value) {
        engine.multipartFile(name, value);
    }

    @Override
    @When("^multipart files (.+)")
    public void multipartFiles(String exp) {
        engine.multipartFiles(exp);
    }

    @Override
    @When("^print (.+)")
    public void print(String exp) {
        engine.print(exp);
    }

    @Override
    @When("^status (\\d+)")
    public void status(int status) {
        engine.status(status);
    }

    @Override
    @When("^match (.+)(=|contains|any|only|deep)(.*)")
    public void match(String exp, String op1, String op2, String rhs) {
        if (op2 == null) {
            op2 = "";
        }
        if (rhs == null) {
            rhs = "";
        } else {
            rhs = " " + rhs;
        }
        MatchStep m = new MatchStep(exp + op1 + op2 + rhs);
        engine.matchResult(m.type, m.name, m.path, m.expected);
    }

    @Override
    @When("^set ([^\\s]+)( .+)? =$")
    public void setDocstring(String name, String path, String value) {
        engine.set(name, path, value);
    }

    @Override
    @When("^set ([^\\s]+)( .+)? = (.+)")
    public void set(String name, String path, String value) {
        engine.set(name, path, value);
    }

    @When("^set ([^\\s]+)( [^=]+)?$")
    public void set(String name, String path, DataTable table) {
        set(name, path, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^set ([^\\s]+)( [^=]+)?$")
    public void set(String name, String path, List<Map<String, String>> table) {
        engine.setViaTable(name, path, table);
    }

    @Override
    @When("^remove ([^\\s]+)( .+)?")
    public void remove(String name, String path) {
        engine.remove(name, path);
    }

    @Override
    @When("^call (.+)")
    public void call(String line) {
        engine.call(false, line, true);
    }

    @Override
    @When("^callonce (.+)")
    public void callonce(String line) {
        engine.call(true, line, true);
    }

    @Override
    @When("^eval (.+)")
    public void eval(String exp) {
        engine.evalJs(exp);
    }

    @Override
    @When("^eval$")
    public void evalDocstring(String exp) {
        engine.evalJs(exp);
    }

    @Override
    @When("^([\\w]+)([^\\s^\\w])(.+)")
    public void eval(String name, String dotOrParen, String exp) {
        engine.evalJs(name + dotOrParen + exp);
    }

    @Override
    @When("^if (.+)")
    public void evalIf(String exp) {
        engine.evalJs("if " + exp);
    }

    @Override
    @When("^listen (.+)")
    public void listen(String body) {
        engine.listen(body);
    }
    
    @Override
    @When("^doc (.+)")
    public void doc(String exp) {
        engine.doc(exp);
    }  

    //==========================================================================
    //
    @Override
    @When("^driver (.+)")
    public void driver(String exp) {
        engine.driver(exp);
    }

    @Override
    @When("^robot (.+)")
    public void robot(String exp) {
        engine.robot(exp);
    }

}
