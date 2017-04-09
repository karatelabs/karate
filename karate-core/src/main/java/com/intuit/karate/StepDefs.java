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
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StepDefs {

    private static final Logger logger = LoggerFactory.getLogger(StepDefs.class);

    public StepDefs() { // zero-arg constructor for IDE support
        this(ScriptEnv.init(getFeatureDir(), Thread.currentThread().getContextClassLoader()), null, null);
    }

    private static File getFeatureDir() {
        String cwd = new File("").getAbsoluteFile().getPath();
        // TODO non-mac (confirm), non oracle jvm-s
        String javaCommand = System.getProperty("sun.java.command");
        String featurePath = FileUtils.getFeaturePath(javaCommand, cwd);
        if (featurePath == null) {
            File file = new File("");
            logger.warn("unable to derive feature file path, using: {}", file.getAbsolutePath());
            return file;
        } else {
            File file = new File(featurePath);
            logger.info("ide running: {}", file);
            return file.getParentFile();
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
            String temp = Script.eval(path, context).getAsString();
            request.addPath(temp);
        }
    }

    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, String value) {
        String temp = Script.eval(value, context).getAsString();
        request.addParam(name, temp);
    }

    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        String temp = Script.eval(value, context).getAsString();
        request.addCookie(new Cookie(name, temp));
    }

    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, String value) {
        String temp = Script.eval(value, context).getAsString();
        request.addHeader(name, temp);
    }

    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, String value) {
        String temp = Script.eval(value, context).getAsString();
        request.addFormField(name, temp);
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
        name = StringUtils.trim(name);
        context.vars.put(name, doc);
    }

    @When("^text (.+) =$")
    public void textDocString(String name, String expression) {
        Script.assignText(name, expression, context);
    }

    @When("^yaml (.+) =$")
    public void yamlDocString(String name, String expression) {
        Script.assignYaml(name, expression, context);
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
        context.vars.put(ScriptValueMap.VAR_COOKIES, response.getCookies());
        DocumentContext headers = JsonPath.parse(response.getHeaders());
        context.vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, headers);
        Object responseBody = convertResponseBody(response.getBody());
        if (responseBody instanceof String) {
            String responseString = (String) responseBody;
            if (Script.isJson(responseString)) {
                responseBody = JsonUtils.toJsonDoc(responseString);
            } else if (Script.isXml(responseString)) {
                try {
                    responseBody = XmlUtils.toXmlDoc(responseString);
                } catch (Exception e) {
                    logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
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

    private static Object convertResponseBody(byte[] bytes) {
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
            logger.warn("response bytes to string conversion failed: {}", e.getMessage());
        }
        return new ByteArrayInputStream(bytes);
    }

    @When("^soap action( .+)?")
    public void soapAction(String action) {
        action = Script.eval(action, context).getAsString();
        if (action == null) {
            action = "";
        }
        request.addHeader("SOAPAction", action);
        request.addHeader("Content-Type", "text/xml");
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

    @Then("^print (.+)")
    public void print(String exp) {
        String temp = Script.eval(exp, context).getAsString();
        logger.info("[print] {}", temp);
    }

    @Then("^status (\\d+)")
    public void status(int status) {
        if (status != response.getStatus()) {
            String rawResponse = context.vars.get(ScriptValueMap.VAR_RESPONSE).getAsString();
            String responseTime = context.vars.get(ScriptValueMap.VAR_RESPONSE_TIME).getAsString();
            String message = "status code was: " + response.getStatus() + ", expected: " + status
                    + ", response time: " + responseTime + ", url: " + response.getUri() + ", response: " + rawResponse;
            logger.error(message);
            throw new KarateException(message);
        }
    }

    private static MatchType toMatchType(String each, String only, boolean contains) {
        if (each == null) {
            if (contains) {
                return only == null ? MatchType.CONTAINS : MatchType.CONTAINS_ONLY;
            } else {
                return MatchType.EQUALS;
            }
        } else {
            if (contains) {
                return MatchType.EACH_CONTAINS;
            } else {
                return MatchType.EACH_EQUALS;
            }
        }
    }

    @Then("^match (each )?([^\\s]+)( [^\\s]+)? ==$")
    public void matchEqualsDocString(String each, String name, String path, String expected) {
        matchEquals(each, name, path, expected);
    }

    @Then("^match (each )?([^\\s]+)( [^\\s]+)? contains( only)?$")
    public void matchContainsDocString(String each, String name, String path, String only, String expected) {
        matchContains(each, name, path, only, expected);
    }

    @Then("^match (each )?([^\\s]+)( [^\\s]+)? == (.+)")
    public void matchEquals(String each, String name, String path, String expected) {
        MatchType mt = toMatchType(each, null, false);
        matchNamed(mt, name, path, expected);
    }

    @Then("^match (each )?([^\\s]+)( [^\\s]+)? contains( only)?(.+)")
    public void matchContains(String each, String name, String path, String only, String expected) {
        MatchType mt = toMatchType(each, only, true);
        matchNamed(mt, name, path, expected);
    }

    public void matchNamed(MatchType matchType, String name, String path, String expected) {
        AssertionResult ar = Script.matchNamed(matchType, name, path, expected, context);
        handleFailure(ar);
    }

    @Then("^set ([^\\s]+)( .+)? =$")
    public void setByPathDocString(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    @Then("^set ([^\\s]+)( .+)? = (.+)")
    public void setByPath(String name, String path, String value) {
        setNamedByPath(name, path, value);
    }

    public void setNamedByPath(String name, String path, String value) {
        Script.setValueByPath(name, path, value, context);
    }

    @Given("^call ([^\\s]+)( .*)?")
    public final void callAndUpdateVars(String name, String arg) {
        Script.callAndUpdateVars(name, arg, context);
    }

    private void handleFailure(AssertionResult ar) {
        if (!ar.pass) {
            logger.error("{}", ar);
            throw new KarateException(ar.message);
        }
    }

}
