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

import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepDefs {

    private static final Logger LOGGER = LoggerFactory.getLogger(StepDefs.class);

    public StepDefs() { // zero-arg constructor for IDE support
        this(getFeatureEnv(), new CallContext(null, 0, null, -1, false, true, null, null));
    }

    private static ScriptEnv ideScriptEnv;

    private static ScriptEnv getFeatureEnv() {
        if (ideScriptEnv == null) {
            String cwd = new File("").getAbsoluteFile().getPath();
            String javaCommand = System.getProperty("sun.java.command");
            String featurePath = FileUtils.getFeaturePath(javaCommand, cwd);
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (featurePath == null) {
                File file = new File("");
                LOGGER.warn("IDE runner - unable to derive feature file path, using: {}", file.getAbsolutePath());
                ideScriptEnv = ScriptEnv.init(file, null, classLoader);
            } else {
                File file = new File(featurePath);
                LOGGER.info("IDE runner - init karate env: {}", file);
                ideScriptEnv = ScriptEnv.init(file.getParentFile(), file.getName(), classLoader);
            }
        } else {
            LOGGER.info("IDE runner - reusing karate env: {}", ideScriptEnv);
        }
        return ideScriptEnv;
    }

    public StepDefs(ScriptEnv scriptEnv, CallContext call) {
        context = new ScriptContext(scriptEnv, call);
        request = new HttpRequestBuilder();
    }

    private final ScriptContext context;
    private HttpRequestBuilder request;
    private HttpResponse response;

    public ScriptContext getContext() {
        return context;
    }

    @When("^configure ([^\\s]+) =$")
    public void configureDocString(String key, String exp) {
        configure(key, exp);
    }

    @When("^configure ([^\\s]+) = (.+)")
    public void configure(String key, String exp) {
        context.configure(key, exp);
    }

    @When("^url (.+)")
    public void url(String expression) {
        String temp = Script.evalKarateExpression(expression, context).getAsString();
        request.setUrl(temp);
    }

    @When("^path (.+)")
    public void path(List<String> paths) {
        for (String path : paths) {
            ScriptValue temp = Script.evalKarateExpression(path, context);
            if (temp.isListLike()) {
                List list = temp.getAsList();
                for (Object o : list) {
                    if (o == null) {
                        continue;
                    }
                    request.addPath(o.toString());
                }
            } else {
                request.addPath(temp.getAsString());
            }
        }
    }

    private List<String> evalList(List<String> values) {
        List<String> list = new ArrayList(values.size());
        try {
            for (String value : values) {
                ScriptValue temp = Script.evalKarateExpression(value, context);
                list.add(temp.getAsString());
            }
        } catch (Exception e) { // hack. for e.g. json with commas would land here
            String joined = StringUtils.join(values, ',');
            ScriptValue temp = Script.evalKarateExpression(joined, context);
            if (temp.isListLike()) {
                return temp.getAsList();
            } else {
                return Collections.singletonList(temp.getAsString());
            }
        }
        return list;
    }

    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, List<String> values) {
        List<String> list = evalList(values);
        request.setParam(name, list);
    }

    public Map<String, Object> evalMapExpr(String expr) {
        ScriptValue value = Script.evalKarateExpression(expr, context);
        if (!value.isMapLike()) {
            throw new KarateException("cannot convert to map: " + expr);
        }
        return value.getAsMap();
    }

    @When("^params (.+)")
    public void params(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeParam(key);
            } else {
                if (temp instanceof List) {
                    request.setParam(key, (List) temp);
                } else {
                    request.setParam(key, temp.toString());
                }
            }
        }
    }

    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        ScriptValue sv = Script.evalKarateExpression(value, context);
        Cookie cookie;
        if (sv.isMapLike()) {
            cookie = new Cookie((Map) sv.getAsMap());
            cookie.put(Cookie.NAME, name);
        } else {
            cookie = new Cookie(name, sv.getAsString());
        }
        request.setCookie(cookie);
    }

    @When("^cookies (.+)")
    public void cookies(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeCookie(key);
            } else {
                request.setCookie(new Cookie(key, temp.toString()));
            }
        }
    }

    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, List<String> values) {
        List<String> list = evalList(values);
        request.setHeader(name, list);
    }

    @When("^headers (.+)")
    public void headers(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeHeader(key);
            } else {
                if (temp instanceof List) {
                    request.setHeader(key, (List) temp);
                } else {
                    request.setHeader(key, temp.toString());
                }
            }
        }
    }

    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, List<String> values) {
        List<String> list = evalList(values);
        request.setFormField(name, list);
    }

    @When("^form fields (.+)")
    public void formFields(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object temp = entry.getValue();
            if (temp == null) {
                request.removeFormField(key);
            } else {
                if (temp instanceof List) {
                    request.setFormField(key, (List) temp);
                } else {
                    request.setFormField(key, temp.toString());
                }
            }
        }
    }

    @When("^request$")
    public void requestDocString(String requestBody) {
        request(requestBody);
    }

    @When("^request (.+)")
    public void request(String requestBody) {
        ScriptValue temp = Script.evalKarateExpression(requestBody, context);
        request.setBody(temp);
    }

    @When("^table (.+)")
    public void table(String name, DataTable table) {
        int pos = name.indexOf('='); // backward compatibility, we used to require this till v0.5.0
        if (pos != -1) {
            name = name.substring(0, pos);
        }
        List<Map<String, Object>> list = table.asMaps(String.class, Object.class);
        list = Script.evalTable(list, context);
        DocumentContext doc = JsonPath.parse(list);
        context.vars.put(name.trim(), doc);
    }

    private String getVarAsString(String name) {
        ScriptValue sv = context.vars.get(name);
        if (sv == null) {
            throw new RuntimeException("no variable found with name: " + name);
        }
        return sv.getAsString();
    }

    @When("^replace (\\w+)$")
    public void replace(String name, DataTable table) {
        name = name.trim();
        String text = getVarAsString(name);
        List<Map<String, String>> list = table.asMaps(String.class, String.class);
        String replaced = Script.replacePlaceholders(text, list, context);
        context.vars.put(name, replaced);
    }

    @When("^replace (\\w+).([^\\s]+) = (.+)")
    public void replace(String name, String token, String value) {
        name = name.trim();
        String text = getVarAsString(name);
        String replaced = Script.replacePlaceholderText(text, token, value, context);
        context.vars.put(name, replaced);
    }
    
    @When("^def (.+) =$")
    public void defDocString(String name, String expression) {
        def(name, expression);
    }

    @When("^def (\\w+) = (.+)")
    public void def(String name, String expression) {
        Script.assign(name, expression, context, true);
    }    

    @When("^text (.+) =$")
    public void textDocString(String name, String expression) {
        Script.assignText(name, expression, context, true);
    }

    @When("^yaml (.+) =$")
    public void yamlDocString(String name, String expression) {
        Script.assignYaml(name, expression, context, true);
    }
    
    @When("^copy (.+) = (.+)")
    public void copy(String name, String expression) {
        Script.copy(name, expression, context, true);
    }    

    @When("^json (.+) = (.+)")
    public void castToJson(String name, String expression) {
        Script.assignJson(name, expression, context, true);
    }

    @When("^string (.+) = (.+)")
    public void castToString(String name, String expression) {
        Script.assignString(name, expression, context, true);
    }

    @When("^xml (.+) = (.+)")
    public void castToXml(String name, String expression) {
        Script.assignXml(name, expression, context, true);
    }

    @When("^xmlstring (.+) = (.+)")
    public void castToXmlString(String name, String expression) {
        Script.assignXmlString(name, expression, context, true);
    }

    @When("^assert (.+)")
    public void asssertBoolean(String expression) {
        try {
            AssertionResult ar = Script.assertBoolean(expression, context);
            handleFailure(ar);
        } catch (Exception e) {
            throw new KarateException(e.getMessage());
        }
    }

    @When("^method (\\w+)")
    public void method(String method) {
        if (!HttpUtils.HTTP_METHODS.contains(method.toUpperCase())) { // support expressions also
            method = Script.evalKarateExpression(method, context).getAsString();
        }
        request.setMethod(method);
        try {
            response = context.client.invoke(request, context);
        } catch (Exception e) {
            String message = e.getMessage();
            context.logger.error("http request failed: {}", message);
            throw new KarateException(message); // reduce log verbosity
        }
        HttpUtils.updateRequestVars(request, context.vars, context);
        HttpUtils.updateResponseVars(response, context.vars, context);
        String prevUrl = request.getUrl();
        request = new HttpRequestBuilder();
        request.setUrl(prevUrl);
    }

    @When("^soap action( .+)?")
    public void soapAction(String action) {
        action = Script.evalKarateExpression(action, context).getAsString();
        if (action == null) {
            action = "";
        }
        request.setHeader("SOAPAction", action);
        request.setHeader("Content-Type", "text/xml");
        method("post");
    }

    @When("^multipart entity (.+)")
    public void multiPartEntity(String value) {
        multiPart(null, value);
    }

    @When("^multipart field (.+) = (.+)")
    public void multiPartFormField(String name, String value) {
        multiPart(name, value);
    }

    private static String asString(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? null : o.toString();
    }

    @When("^multipart file (.+) = (.+)")
    public void multiPartFile(String name, String value) {
        name = name.trim();
        ScriptValue sv = Script.evalKarateExpression(value, context);
        if (!sv.isMapLike()) {
            throw new RuntimeException("mutipart file value should be json");
        }
        Map<String, Object> map = sv.getAsMap();
        String read = asString(map, "read");
        if (read == null) {
            throw new RuntimeException("mutipart file json should have a value for 'read'");
        }
        ScriptValue fileValue = FileUtils.readFile(read, context);
        MultiPartItem item = new MultiPartItem(name, fileValue);
        String filename = asString(map, "filename");
        if (filename == null) {
            filename = name;
        }
        item.setFilename(filename);
        String contentType = asString(map, "contentType");
        if (contentType != null) {
            item.setContentType(contentType);
        }
        request.addMultiPartItem(item);
    }

    @When("^multipart files (.+)")
     public void multiPartFiles(String expr) {
        Map<String, Object> map = evalMapExpr(expr);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            ScriptValue sv = new ScriptValue(value);
            multiPartFile(key, sv.getAsString());
        }
    }

    public void multiPart(String name, String value) {
        ScriptValue sv = Script.evalKarateExpression(value, context);
        request.addMultiPartItem(name, sv);
    }

    @When("^print (.+)")
    public void print(List<String> exps) {
        if (context.isPrintEnabled() && context.logger.isInfoEnabled()) {
            String prev = ""; // handle rogue commas embedded in string literals
            StringBuilder sb = new StringBuilder();
            sb.append("[print]");
            for (String exp : exps) {
                if (!prev.isEmpty()) {
                    exp = prev + exp;
                }
                exp = StringUtils.trimToNull(exp);
                if (exp == null) {
                    sb.append("null");
                } else {
                    ScriptValue sv = Script.getIfVariableReference(exp, context);
                    if (sv == null) {
                        try {
                            sv = Script.evalJsExpression(exp, context);
                            prev = ""; // evalKarateExpression success, reset rogue comma detector
                        } catch (Exception e) {
                            prev = exp + ", ";
                            continue;
                        }
                    }
                    sb.append(' ').append(sv.getAsPrettyString());
                }
            }
            context.logger.info("{}", sb);
        }
    }

    @When("^status (\\d+)")
    public void status(int status) {
        if (status != response.getStatus()) {
            String rawResponse = context.vars.get(ScriptValueMap.VAR_RESPONSE).getAsString();
            String responseTime = context.vars.get(ScriptValueMap.VAR_RESPONSE_TIME).getAsString();
            String message = "status code was: " + response.getStatus() + ", expected: " + status
                    + ", response time: " + responseTime + ", url: " + response.getUri() + ", response: " + rawResponse;
            context.logger.error(message);
            throw new KarateException(message);
        }
    }

    private static MatchType toMatchType(String eqSymbol, String each, String notContains, String only, boolean contains) {
        boolean notEquals = eqSymbol.startsWith("!");
        if (each == null) {
            if (notContains != null) {
                return MatchType.NOT_CONTAINS;
            }
            if (only != null) {
                return only.contains("only") ? MatchType.CONTAINS_ONLY: MatchType.CONTAINS_ANY;
            }
            return contains ? MatchType.CONTAINS : notEquals ? MatchType.NOT_EQUALS : MatchType.EQUALS;
        } else {
            if (notContains != null) {
                return MatchType.EACH_NOT_CONTAINS;
            }
            if (only != null) {
                return only.contains("only") ? MatchType.EACH_CONTAINS_ONLY: MatchType.EACH_CONTAINS_ANY;
            }
            return contains ? MatchType.EACH_CONTAINS : notEquals ? MatchType.EACH_NOT_EQUALS : MatchType.EACH_EQUALS;
        }
    }

    private static void validateEqualsSign(String eqSymbol) {
        if (eqSymbol.equals("=")) {
            throw new RuntimeException("use '==' for match (not '=')");
        }
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (==?|!=)$")
    public void matchEqualsDocString(String each, String name, String path, String eqSymbol, String expected) {
        validateEqualsSign(eqSymbol);
        matchEquals(each, name, path, eqSymbol, expected);
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only| any)?$")
    public void matchContainsDocString(String each, String name, String path, String not, String only, String expected) {
        matchContains(each, name, path, not, only, expected);
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (==?|!=) (.+)")
    public void matchEquals(String each, String name, String path, String eqSymbol, String expected) {
        validateEqualsSign(eqSymbol);
        MatchType mt = toMatchType(eqSymbol, each, null, null, false);
        matchNamed(mt, name, path, expected);
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only| any)?(.+)")
    public void matchContains(String each, String name, String path, String not, String only, String expected) {
        MatchType mt = toMatchType("==", each, not, only, true);
        matchNamed(mt, name, path, expected);
    }

    public void matchNamed(MatchType matchType, String name, String path, String expected) {
        try {
            AssertionResult ar = Script.matchNamed(matchType, name, path, expected, context);
            handleFailure(ar);
        } catch (Exception e) {
            throw new KarateException(e.getMessage());
        }
    }

    @When("^set ([^\\s]+)( .+)? =$")
    public void setByPathDocString(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    @When("^set ([^\\s]+)( .+)? = (.+)")
    public void setByPath(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    @When("^set ([^\\s]+)( [^=]+)?$")
    public void setByPathTable(String name, String path, DataTable table) {
        List<Map<String, String>> list = table.asMaps(String.class, String.class);
        Script.setByPathTable(name, path, list, context);
    }

    public void setNamedByPath(String name, String path, String value) {
        Script.setValueByPath(name, path, value, context);
    }

    @When("^remove ([^\\s]+)( .+)?")
    public void removeByPath(String name, String path) {
        Script.removeValueByPath(name, path, context);
    }

    @When("^call ([^\\s]+)( .*)?")
    public void callAndUpdateConfigAndVars(String name, String arg) {
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, name, arg, context);
    }

    @When("^callonce ([^\\s]+)( .*)?")
    public void callOnceAndUpdateConfigAndVars(String name, String arg) {
        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(true, name, arg, context);
    }

    @When("^eval (.+)")
    public final void eval(String exp) {
        Script.evalJsExpression(exp, context);
    }
    
    @When("^eval$")
    public void evalDocString(String exp) {
        eval(exp);
    }    
    
    private void handleFailure(AssertionResult ar) {
        if (!ar.pass) {
            context.logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

}
