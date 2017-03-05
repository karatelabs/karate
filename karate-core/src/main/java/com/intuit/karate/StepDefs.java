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

import com.intuit.karate.ScriptValue.Type;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class StepDefs {

    private static final Logger logger = LoggerFactory.getLogger(StepDefs.class);

    public StepDefs(String featureDir, ClassLoader fileClassLoader, String env) {
        context = new ScriptContext(false, featureDir, fileClassLoader, env);
    }

    private String url;
    private WebTarget target;
    private Response response;
    private long startTime;

    private Map<String, Object> headers;
    private ScriptValue request;
    private MultiPart multiPart;
    private MultivaluedMap<String, Object> formFields;

    private final ScriptContext context;

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
        this.url = temp;
        target = context.client.target(temp);
    }

    private void hasUrlBeenSet() {
        if (target == null) {
            throw new RuntimeException("url not set, please refer to the syntax for 'url'");
        }
    }

    @When("^path (.+)")
    public void path(List<String> paths) {
        hasUrlBeenSet();
        for (String path : paths) {
            String temp = Script.eval(path, context).getAsString();
            target = target.path(temp);
        }
    }

    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, String value) {
        hasUrlBeenSet();
        String temp = Script.eval(value, context).getAsString();
        target = target.queryParam(name, temp);
    }

    private Map<String, String> getCookies() {
        Map<String, String> cookies = context.vars.get(ScriptValueMap.VAR_COOKIES, Map.class);
        if (cookies == null) {
            cookies = new HashMap<>();
            context.vars.put(ScriptValueMap.VAR_COOKIES, cookies);
        }
        return cookies;
    }

    @When("^cookie ([^\\s]+) = (.+)")
    public void cookie(String name, String value) {
        Map<String, String> cookies = getCookies();
        String temp = Script.eval(value, context).getAsString();
        cookies.put(name, temp);
    }

    private Map<String, Object> getHeaders() {
        if (headers == null) {
            headers = new HashMap<>();
        }
        return headers;
    }

    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, String value) {
        Map<String, Object> headers = getHeaders();
        String temp = Script.eval(value, context).getAsString();
        headers.put(name, temp);
    }

    private MultivaluedMap<String, Object> getFormFields() {
        if (formFields == null) {
            formFields = new MultivaluedHashMap<>();
        }
        return formFields;
    }

    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, String value) {
        MultivaluedMap<String, Object> formFields = getFormFields();
        String temp = Script.eval(value, context).getAsString();
        formFields.add(name, temp);
    }

    @When("^request$")
    public void requestDocString(String requestBody) {
        request(requestBody);
    }

    @When("^request (.+)")
    public void request(String requestBody) {
        request = Script.eval(requestBody, context);
        logger.trace("request value is: {}", request);
    }

    @When("^def (.+) =$")
    public void defDocString(String name, String expression) {
        def(name, expression);
    }

    @When("^def (.+) = (.+)")
    public void def(String name, String expression) {
        Script.assign(name, expression, context);
    }

    @When("^assert (.+)")
    public void asssertBoolean(String expression) {
        AssertionResult ar = Script.assertBoolean(expression, context);
        handleFailure(ar);
    }

    private Invocation.Builder prepare() {
        hasUrlBeenSet();
        Invocation.Builder builder = target.request();
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                builder = builder.header(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> cookies = context.vars.get(ScriptValueMap.VAR_COOKIES, Map.class);
        if (cookies != null) {
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                builder = builder.cookie(entry.getKey(), entry.getValue());
            }
        }
        return builder;
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
    }

    private void stopTimer() {
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        logger.debug("response time in milliseconds: {}", responseTime);
        context.vars.put(ScriptValueMap.VAR_RESPONSE_TIME, responseTime);
    }

    private String getUserSpecifiedContentType() {
        if (headers != null) {
            String type = (String) headers.get("Content-Type");
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    @When("^method (\\w+)")
    public void method(String method) {
        method = method.toUpperCase();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            if (multiPart != null) {
                String mediaType = getUserSpecifiedContentType();
                if (mediaType == null) {
                    mediaType = MediaType.MULTIPART_FORM_DATA;
                }
                startTimer();
                response = prepare().method(method, Entity.entity(multiPart, mediaType));
                stopTimer();
            } else if (formFields != null) {
                startTimer();
                response = prepare().method(method, Entity.entity(formFields, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                stopTimer();
            } else {
                if (request == null || request.isNull()) {
                    String msg = "request body is requred for a " + method + ", please use the 'request' keyword";
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }
                String mediaType = getUserSpecifiedContentType();
                Entity entity;
                switch (request.getType()) {
                    case JSON:
                        DocumentContext doc = request.getValue(DocumentContext.class);
                        entity = Entity.json(doc.jsonString());
                        break;
                    case MAP:
                        Map<String, Object> map = request.getValue(Map.class);
                        doc = JsonPath.parse(map);
                        entity = Entity.json(doc.jsonString());
                        break;
                    case XML:
                        Node node = request.getValue(Node.class);
                        entity = Entity.xml(XmlUtils.toString(node));
                        break;
                    case INPUT_STREAM:
                        InputStream is = request.getValue(InputStream.class);
                        if (mediaType == null) {
                            mediaType = MediaType.APPLICATION_OCTET_STREAM;
                        }
                        entity = Entity.entity(is, mediaType);
                        break;
                    default:
                        if (mediaType == null) {
                            mediaType = MediaType.TEXT_PLAIN;
                        }
                        entity = Entity.entity(request.getAsString(), mediaType);
                }
                startTimer();
                response = prepare().method(method, entity);
                stopTimer();
            }
        } else {
            startTimer();
            response = prepare().method(method);
            stopTimer();
        }
        unprepare();
    }

    private void unprepare() {
        context.vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        for (Map.Entry<String, NewCookie> entry : response.getCookies().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().getValue();
            getCookies().put(key, value);
            logger.trace("set cookie: {} - {}", key, entry.getValue());
        }
        DocumentContext headers = JsonPath.parse(response.getHeaders());
        logger.trace("set response headers: {}", headers.jsonString());
        context.vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, headers);
        String rawResponse = response.readEntity(String.class);
        if (Script.isJson(rawResponse)) {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, JsonUtils.toJsonDoc(rawResponse));
        } else if (Script.isXml(rawResponse)) {
            try {
                context.vars.put(ScriptValueMap.VAR_RESPONSE, XmlUtils.toXmlDoc(rawResponse));
            } catch (Exception e) {
                logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
                context.vars.put(ScriptValueMap.VAR_RESPONSE, rawResponse);
            }
        } else {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, rawResponse);
        }
        // reset url and some state
        target = context.client.target(url);
        formFields = null;
        multiPart = null;
    }

    @When("^soap action( .+)?")
    public void soapAction(String action) {
        hasUrlBeenSet();
        action = Script.eval(action, context).getAsString();
        if (action == null) {
            action = "";
        }
        logger.trace("soap action: '{}'", action);
        if (request == null || request.isNull()) {
            String msg = "request body is requred for a SOAP request, please use the 'request' keyword";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        String xml;
        switch (request.getType()) {
            case XML:
                Document doc = request.getValue(Document.class);
                xml = XmlUtils.toString(doc);
                break;
            default:
                xml = request.getAsString();
        }
        startTimer();
        response = target.request().header("SOAPAction", action).method("POST", Entity.entity(xml, MediaType.TEXT_XML));
        stopTimer();
        String rawResponse = response.readEntity(String.class);
        try {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, XmlUtils.toXmlDoc(rawResponse));
        } catch (Exception e) {
            logger.warn("xml parsing failed, response data type set to string: {}", e.getMessage());
            context.vars.put(ScriptValueMap.VAR_RESPONSE, rawResponse);
        }
    }

    private MultiPart getMultiPart() {
        if (multiPart == null) {
            multiPart = new MultiPart();
        }
        return multiPart;
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
        MultiPart mp = getMultiPart();
        ScriptValue sv = Script.eval(value, context);
        if (sv.isNull()) {
            throw new RuntimeException("multipart field cannot be null: " + name);
        }
        if (name == null) {
            BodyPart bp;
            switch (sv.getType()) {
                case JSON:
                    DocumentContext dc = sv.getValue(DocumentContext.class);
                    bp = new BodyPart().entity(dc.jsonString()).type(MediaType.APPLICATION_JSON_TYPE);
                    break;
                case XML:
                    Document doc = sv.getValue(Document.class);
                    bp = new BodyPart().entity(XmlUtils.toString(doc)).type(MediaType.APPLICATION_XML_TYPE);
                    break;
                default:
                    bp = new BodyPart().entity(sv.getValue());
            }
            mp.bodyPart(bp);
        } else if (sv.getType() == Type.INPUT_STREAM) {
            InputStream is = (InputStream) sv.getValue();
            StreamDataBodyPart part = new StreamDataBodyPart(name, is);
            mp.bodyPart(part);
        } else {
            mp.bodyPart(new FormDataBodyPart(name, sv.getAsString()));
        }
    }

    @Then("^print (.+)")
    public void print(String exp) {
        String temp = Script.eval(exp, context).getAsString();
        logger.info("[print] {}", temp);
    }

    @Then("^status (\\d+)")
    public void status(int status) {
        if (status != response.getStatus()) {
            AssertionResult ar = AssertionResult.fail("status code was " + response.getStatus() + ", expected " + status);
            handleFailure(ar);
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
            logger.error("result: {}", ar);
            throw new RuntimeException(ar.message);
        }
    }

}
