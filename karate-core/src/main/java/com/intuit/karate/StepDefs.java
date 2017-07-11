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

import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class StepDefs {

    private static final Logger LOGGER = LoggerFactory.getLogger(StepDefs.class);

    public StepDefs() { // zero-arg constructor for IDE support
        this(getFeatureEnv(), null, null);
    }

    private static ScriptEnv getFeatureEnv() {
        String cwd = new File("").getAbsoluteFile().getPath();
        String javaCommand = System.getProperty("sun.java.command");
        String featurePath = FileUtils.getFeaturePath(javaCommand, cwd);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (featurePath == null) {
            File file = new File("");
            LOGGER.warn("unable to derive feature file path, using: {}", file.getAbsolutePath());
            return ScriptEnv.init(file, null, classLoader);
        } else {
            File file = new File(featurePath);
            LOGGER.info("ide running: {}", file);
            return ScriptEnv.init(file.getParentFile(), file.getName(), classLoader);
        }
    }

    public StepDefs(ScriptEnv env, ScriptContext parentContext, Map<String, Object> callArg) {
        context = new ScriptContext(env, parentContext, callArg);
        request = new HttpRequest();
    }

    private final ScriptContext context;
    private HttpRequest request;
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
        String temp = Script.eval(expression, context).getAsString();
        request.setUrl(temp);
    }

    @When("^path (.+)")
    public void path(List<String> paths) {
        for (String path : paths) {
            ScriptValue temp = Script.eval(path, context);
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
                ScriptValue temp = Script.eval(value, context);
                list.add(temp.getAsString());
            }
        } catch (Exception e) { // hack. for e.g. json with commas would land here
            String joined = StringUtils.join(values, ',');
            ScriptValue temp = Script.eval(joined, context);
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
        ScriptValue value = Script.eval(expr, context);
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
                    List list = (List) temp;
                    String csv = StringUtils.join(list, ',');
                    request.setParam(key, csv);
                } else {
                    request.setParam(key, temp.toString());
                }
            }
        }
    }

    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        String temp = Script.eval(value, context).getAsString();
        request.setCookie(new Cookie(name, temp));
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
        ScriptValue temp = Script.eval(requestBody, context);
        request.setBody(temp);
    }

    @When("^def (.+) =$")
    public void defDocString(String name, String expression) {
        def(name, expression);
    }

    @When("^def (.+) = (.+)")
    public void def(String name, String expression) {
        Script.assign(name, expression, context);
    }

    private static DocumentContext toJson(DataTable table) {
        return JsonPath.parse(table.asMaps(String.class, Object.class));
    }

    @When("^table (.+) =$")
    public void table(String name, DataTable table) {
        DocumentContext doc = toJson(table);
        context.vars.put(name.trim(), doc);
    }
    
    @When("^replace (\\w+)$")
    public void replace(String name, DataTable table) {
        name = name.trim();
        String text = context.vars.get(name).getAsString();
        List<Map<String, String>> list = table.asMaps(String.class, String.class);
        String replaced = Script.replacePlaceholders(text, list, context);
        context.vars.put(name, replaced);
    }

    @When("^replace (\\w+).([^\\s]+) = (.+)")
    public void replace(String name, String token, String value) {
        name = name.trim();
        String text = context.vars.get(name).getAsString();
        String replaced = Script.replacePlaceholderText(text, token, value, context);
        context.vars.put(name, replaced);
    }

    @When("^text (.+) =$")
    public void textDocString(String name, String expression) {
        Script.assignText(name, expression, context);
    }

    @When("^yaml (.+) =$")
    public void yamlDocString(String name, String expression) {
        Script.assignYaml(name, expression, context);
    }
    
    @When("^json (.+) = (.+)")
    public void castToJson(String name, String expression) {
        Script.assignJson(name, expression, context);
    }    
    
    @When("^string (.+) = (.+)")
    public void castToString(String name, String expression) {
        Script.assignString(name, expression, context);
    }
    
    @When("^xml (.+) = (.+)")
    public void castToXml(String name, String expression) {
        Script.assignXml(name, expression, context);
    }  

    @When("^xmlstring (.+) = (.+)")
    public void castToXmlString(String name, String expression) {
        Script.assignXmlString(name, expression, context);
    }     

    @When("^assert (.+)")
    public void asssertBoolean(String expression) {
        AssertionResult ar = Script.assertBoolean(expression, context);
        handleFailure(ar);
    }

    @When("^method (\\w+)")
    public void method(String method) {
        request.setMethod(method);
        response = context.client.invoke(request, context);
        context.vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        context.vars.put(ScriptValueMap.VAR_RESPONSE_TIME, response.getTime());
        context.vars.put(ScriptValueMap.VAR_RESPONSE_COOKIES, response.getCookies());
        if (response.getHeaders() != null) {
            DocumentContext headers = JsonPath.parse(response.getHeaders());
            context.vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, headers);
        } else {
            context.vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, ScriptValue.NULL);
        }
        Object responseBody = convertResponseBody(response.getBody());
        if (responseBody instanceof String) {
            String responseString = (String) responseBody;
            if (Script.isJson(responseString)) {
                DocumentContext doc = JsonUtils.toJsonDoc(responseString);
                responseBody = doc;
                if (context.logPrettyResponse && context.logger.isDebugEnabled()) {
                    context.logger.debug("response:\n{}", JsonUtils.toPrettyJsonString(doc));
                }
            } else if (Script.isXml(responseString)) {
                try {
                    Document doc = XmlUtils.toXmlDoc(responseString);
                    responseBody = doc;
                    if (context.logPrettyResponse && context.logger.isDebugEnabled()) {
                        context.logger.debug("response:\n{}", XmlUtils.toString(doc, true));
                    }
                } catch (Exception e) {
                    context.logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
                }
            }
        }
        context.vars.put(ScriptValueMap.VAR_RESPONSE, responseBody);
        String prevUrl = request.getUrl();
        Map<String, Cookie> prevCookies = request.getCookies();
        request = new HttpRequest();
        request.setUrl(prevUrl);
        if (prevCookies == null) {
            request.setCookies(response.getCookies());
        } else {
            if (response.getCookies() != null) {
                prevCookies.putAll(response.getCookies());
            }
            request.setCookies(prevCookies);
        }
    }

    private Object convertResponseBody(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        // if a byte array contains a negative-signed byte,
        // then the string conversion will corrupt it.
        // in that case just return the byte array stream
        try {
            String rawString = new String(bytes, "utf-8");
            if (Arrays.equals(bytes, rawString.getBytes())) {
                return rawString;
            }
        } catch (Exception e) {
            context.logger.warn("response bytes to string conversion failed: {}", e.getMessage());
        }
        return new ByteArrayInputStream(bytes);
    }

    @When("^soap action( .+)?")
    public void soapAction(String action) {
        action = Script.eval(action, context).getAsString();
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

    public void multiPart(String name, String value) {
        ScriptValue sv = Script.eval(value, context);
        request.addMultiPartItem(name, sv);
    }

    @When("^print (.+)")
    public void print(String exp) {
        String temp = Script.evalInNashorn(exp, context).getAsString();
        context.logger.info("[print] {}", temp);
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

    private static MatchType toMatchType(String each, String not, String only, boolean contains) {
        if (each == null) {
            if (not != null) {
                return MatchType.NOT_CONTAINS;
            }
            if (only != null) {
                return MatchType.CONTAINS_ONLY;
            }
            return contains ? MatchType.CONTAINS : MatchType.EQUALS;
        } else {
            if (not != null) {
                return MatchType.EACH_NOT_CONTAINS;
            }
            if (only != null) {
                return MatchType.EACH_CONTAINS_ONLY;
            }
            return contains ? MatchType.EACH_CONTAINS : MatchType.EACH_EQUALS;
        }
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? ==$")
    public void matchEqualsDocString(String each, String name, String path, String expected) {
        matchEquals(each, name, path, expected);
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only)?$")
    public void matchContainsDocString(String each, String name, String path, String not, String only, String expected) {
        matchContains(each, name, path, not, only, expected);
    }   

    @When("^match (each )?([^\\s]+)( [^\\s]+)? == (.+)")
    public void matchEquals(String each, String name, String path, String expected) {
        MatchType mt = toMatchType(each, null, null, false);
        matchNamed(mt, name, path, expected);
    }

    @When("^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only)?(.+)")
    public void matchContains(String each, String name, String path, String not, String only, String expected) {
        MatchType mt = toMatchType(each, not, only, true);
        matchNamed(mt, name, path, expected);
    }   

    public void matchNamed(MatchType matchType, String name, String path, String expected) {
        AssertionResult ar = Script.matchNamed(matchType, name, path, expected, context);
        handleFailure(ar);
    }

    @When("^set ([^\\s]+)( .+)? =$")
    public void setByPathDocString(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    @When("^set ([^\\s]+)( .+)? = (.+)")
    public void setByPath(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    public void setNamedByPath(String name, String path, String value) {
        Script.setValueByPath(name, path, value, context);
    }
    
    @When("^remove ([^\\s]+)( .+)?")
    public void removeByPath(String name, String path) {
        Script.removeValueByPath(name, path, context);
    }    

    @When("^call ([^\\s]+)( .*)?")
    public final void callAndUpdateVars(String name, String arg) {
        Script.callAndUpdateVarsIfMapReturned(name, arg, context);
    }

    private void handleFailure(AssertionResult ar) {
        if (!ar.pass) {
            context.logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

}
