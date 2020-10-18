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
package com.intuit.karate.runtime;

import com.intuit.karate.Actions;
import com.intuit.karate.AssignType;
import com.intuit.karate.core.Action;
import com.intuit.karate.match.MatchStep;
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
    public void path(List<String> paths) {
        engine.path(paths);
    }

    @Override
    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, List<String> values) {
        engine.param(name, values);
    }

    @Override
    @When("^params (.+)")
    public void params(String expr) {
        engine.params(expr);
    }

    @Override
    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        engine.cookie(name, value);
    }

    @Override
    @When("^cookies (.+)")
    public void cookies(String expr) {
        engine.cookies(expr);
    }

    @Override
    @When("^csv (.+) = (.+)")
    public void csv(String name, String expression) {
        engine.assign(AssignType.CSV, name, expression);
    }

    @Override
    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, List<String> values) {
        engine.header(name, values);
    }

    @Override
    @When("^headers (.+)")
    public void headers(String expr) {
        engine.headers(expr);
    }

    @Override
    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, List<String> values) {
        engine.formField(name, values);
    }

    @Override
    @When("^form fields (.+)")
    public void formFields(String expr) {
        engine.formFields(expr);
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
        engine.replace(name, table);
    }

    @Override
    @When("^replace (\\w+).([^\\s]+) = (.+)")
    public void replace(String name, String token, String value) {
        engine.replace(name, token, value);
    }

    @Override
    @When("^def (.+) =$")
    public void defDocstring(String name, String expression) {
        engine.assign(AssignType.AUTO, name, expression);
    }

    @Override
    @When("^def (\\w+) = (.+)")
    public void def(String name, String expression) {
        engine.assign(AssignType.AUTO, name, expression);
    }

    @Override
    @When("^text (.+) =$")
    public void text(String name, String expression) {
        engine.assign(AssignType.TEXT, name, expression);
    }

    @Override
    @When("^yaml (.+) = (.+)")
    public void yaml(String name, String expression) {
        engine.assign(AssignType.YAML, name, expression);
    }

    @Override
    @When("^copy (.+) = (.+)")
    public void copy(String name, String expression) {
        engine.assign(AssignType.COPY, name, expression);
    }

    @Override
    @When("^json (.+) = (.+)")
    public void json(String name, String expression) {
        engine.assign(AssignType.JSON, name, expression);
    }

    @Override
    @When("^string (.+) = (.+)")
    public void string(String name, String expression) {
        engine.assign(AssignType.STRING, name, expression);
    }

    @Override
    @When("^xml (.+) = (.+)")
    public void xml(String name, String expression) {
        engine.assign(AssignType.XML, name, expression);
    }

    @Override
    @When("^xmlstring (.+) = (.+)")
    public void xmlstring(String name, String expression) {
        engine.assign(AssignType.XML_STRING, name, expression);
    }

    @Override
    @When("^bytes (.+) = (.+)")
    public void bytes(String name, String expression) {
        engine.assign(AssignType.BYTE_ARRAY, name, expression);
    }

    @Override
    @When("^assert (.+)")
    public void assertTrue(String expression) {
        engine.assertTrue(expression);
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
    public void multipartFields(String expr) {
        engine.multipartFields(expr);
    }

    @Override
    @When("^multipart file (.+) = (.+)")
    public void multipartFile(String name, String value) {
        engine.multipartFile(name, value);
    }

    @Override
    @When("^multipart files (.+)")
    public void multipartFiles(String expr) {
        engine.multipartFiles(expr);
    }

    @Override
    @When("^print (.+)")
    public void print(List<String> exps) {
        engine.print(exps); // TODO refactor this to single line
    }

    @Override
    @When("^status (\\d+)")
    public void status(int status) {
        engine.status(status);
    }

    @Override
    @When("^match (.+)(=|contains|any|only|deep)(.*)")
    public void match(String expression, String op1, String op2, String rhs) {
        if (op2 == null) {
            op2 = "";
        }
        if (rhs == null) {
            rhs = "";
        }
        MatchStep m = new MatchStep(expression + op1 + op2 + rhs);
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
        engine.set(name, path, table);
    }

    @Override
    @When("^remove ([^\\s]+)( .+)?")
    public void remove(String name, String path) {
        engine.remove(name, path);
    }

    @Override
    @When("^call (.+)")
    public void call(String line) {
        engine.call(false, line);
    }

    @Override
    @When("^callonce (.+)")
    public void callonce(String line) {
        engine.call(true, line);
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
    public void eval(String name, String dotOrParen, String expression) {
        engine.evalJs(name + dotOrParen + expression);
    }

    @Override
    @When("^if (.+)")
    public void evalIf(String exp) {
        engine.evalJs("if " + exp);
    }

    //==========================================================================
    //
    @Override
    @When("^driver (.+)")
    public void driver(String expression) {
        engine.driver(expression);
    }
    
    @Override
    @When("^robot (.+)")
    public void robot(String expression) {
        engine.robot(expression);
    }
    
}
