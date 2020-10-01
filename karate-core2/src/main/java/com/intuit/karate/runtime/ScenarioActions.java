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
    
    private final ScenarioRuntime runtime;
    
    public ScenarioActions(ScenarioRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @When("^configure ([^\\s]+) =$")
    public void configureDocstring(String key, String exp) {
        runtime.configure(key, exp);
    }

    @Override
    @When("^configure ([^\\s]+) = (.+)")
    public void configure(String key, String exp) {
        runtime.configure(key, exp);
    }

    @Override
    @When("^url (.+)")
    public void url(String exp) {
        runtime.url(exp);
    }

    @Override
    @When("^path (.+)")
    public void path(List<String> paths) {
        runtime.path(paths);
    }

    @Override
    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, List<String> values) {
        runtime.param(name, values);
    }

    @Override
    @When("^params (.+)")
    public void params(String expr) {
        runtime.params(expr);
    }

    @Override
    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        runtime.cookie(name, value);
    }

    @Override
    @When("^cookies (.+)")
    public void cookies(String expr) {
        runtime.cookies(expr);
    }

    @Override
    @When("^csv (.+) = (.+)")
    public void csv(String name, String expression) {
        runtime.assign(AssignType.CSV, name, expression);
    }

    @Override
    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, List<String> values) {
        runtime.header(name, values);
    }

    @Override
    @When("^headers (.+)")
    public void headers(String expr) {
        runtime.headers(expr);
    }

    @Override
    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, List<String> values) {
        runtime.formField(name, values);
    }

    @Override
    @When("^form fields (.+)")
    public void formFields(String expr) {
        runtime.formFields(expr);
    }

    @Override
    @When("^request$")
    public void requestDocstring(String body) {
        runtime.request(body);
    }

    @Override
    @When("^request (.+)")
    public void request(String body) {
        runtime.request(body);
    }

    @When("^table (.+)")
    public void table(String name, DataTable table) {
        table(name, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^table (.+)")
    public void table(String name, List<Map<String, String>> table) {
        runtime.table(name, table);
    }

    @When("^replace (\\w+)$")
    public void replace(String name, DataTable table) {
        replace(name, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^replace (\\w+)$")
    public void replace(String name, List<Map<String, String>> table) {
        runtime.replace(name, table);
    }

    @Override
    @When("^replace (\\w+).([^\\s]+) = (.+)")
    public void replace(String name, String token, String value) {
        runtime.replace(name, token, value);
    }

    @Override
    @When("^def (.+) =$")
    public void defDocstring(String name, String expression) {
        runtime.assign(AssignType.AUTO, name, expression);
    }

    @Override
    @When("^def (\\w+) = (.+)")
    public void def(String name, String expression) {
        runtime.assign(AssignType.AUTO, name, expression);
    }

    @Override
    @When("^text (.+) =$")
    public void text(String name, String expression) {
        runtime.assign(AssignType.TEXT, name, expression);
    }

    @Override
    @When("^yaml (.+) = (.+)")
    public void yaml(String name, String expression) {
        runtime.assign(AssignType.YAML, name, expression);
    }

    @Override
    @When("^copy (.+) = (.+)")
    public void copy(String name, String expression) {
        runtime.assign(AssignType.COPY, name, expression);
    }

    @Override
    @When("^json (.+) = (.+)")
    public void json(String name, String expression) {
        runtime.assign(AssignType.JSON, name, expression);
    }

    @Override
    @When("^string (.+) = (.+)")
    public void string(String name, String expression) {
        runtime.assign(AssignType.STRING, name, expression);
    }

    @Override
    @When("^xml (.+) = (.+)")
    public void xml(String name, String expression) {
        runtime.assign(AssignType.XML, name, expression);
    }

    @Override
    @When("^xmlstring (.+) = (.+)")
    public void xmlstring(String name, String expression) {
        runtime.assign(AssignType.XML_STRING, name, expression);
    }

    @Override
    @When("^bytes (.+) = (.+)")
    public void bytes(String name, String expression) {
        runtime.assign(AssignType.BYTE_ARRAY, name, expression);
    }

    @Override
    @When("^assert (.+)")
    public void assertTrue(String expression) {
        runtime.assertTrue(expression);
    }

    @Override
    @When("^method (\\w+)")
    public void method(String method) {
        runtime.method(method);
    }

    @Override
    @When("^retry until (.+)")
    public void retry(String until) {
        runtime.retry(until);
    }

    @Override
    @When("^soap action( .+)?")
    public void soapAction(String action) {
        runtime.soapAction(action);
    }

    @Override
    @When("^multipart entity (.+)")
    public void multipartEntity(String value) {
        runtime.multipartField(null, value);
    }

    @Override
    @When("^multipart field (.+) = (.+)")
    public void multipartField(String name, String value) {
        runtime.multipartField(name, value);
    }

    @Override
    @When("^multipart fields (.+)")
    public void multipartFields(String expr) {
        runtime.multipartFields(expr);
    }

    @Override
    @When("^multipart file (.+) = (.+)")
    public void multipartFile(String name, String value) {
        runtime.multipartFile(name, value);
    }

    @Override
    @When("^multipart files (.+)")
    public void multipartFiles(String expr) {
        runtime.multipartFiles(expr);
    }

    @Override
    @When("^print (.+)")
    public void print(List<String> exps) {
        runtime.print(exps);
    }

    @Override
    @When("^status (\\d+)")
    public void status(int status) {
        runtime.status(status);
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
        runtime.match(m.type, m.name, m.path, m.expected);
    }

    @Override
    @When("^set ([^\\s]+)( .+)? =$")
    public void setDocstring(String name, String path, String value) {
        runtime.set(name, path, value);
    }

    @Override
    @When("^set ([^\\s]+)( .+)? = (.+)")
    public void set(String name, String path, String value) {
        runtime.set(name, path, value);
    }

    @When("^set ([^\\s]+)( [^=]+)?$")
    public void set(String name, String path, DataTable table) {
        set(name, path, table.asMaps(String.class, String.class));
    }

    @Override
    @Action("^set ([^\\s]+)( [^=]+)?$")
    public void set(String name, String path, List<Map<String, String>> table) {
        runtime.set(name, path, table);
    }

    @Override
    @When("^remove ([^\\s]+)( .+)?")
    public void remove(String name, String path) {
        runtime.remove(name, path);
    }

    @Override
    @When("^call (.+)")
    public void call(String line) {
        runtime.call(false, line);
    }

    @Override
    @When("^callonce (.+)")
    public void callonce(String line) {
        runtime.call(true, line);
    }

    @Override
    @When("^eval (.+)")
    public void eval(String exp) {
        runtime.eval(exp);
    }

    @Override
    @When("^eval$")
    public void evalDocstring(String exp) {
        runtime.eval(exp);
    }

    @Override
    @When("^([\\w]+)([^\\s^\\w])(.+)")
    public void eval(String name, String dotOrParen, String expression) {
        runtime.eval(name + dotOrParen + expression);
    }

    @Override
    @When("^if (.+)")
    public void evalIf(String exp) {
        runtime.eval("if " + exp);
    }

    //==========================================================================
    //
    @Override
    @When("^driver (.+)")
    public void driver(String expression) {
        runtime.driver(expression);
    }
    
    @Override
    @When("^robot (.+)")
    public void robot(String expression) {
        runtime.robot(expression);
    }
    
}
