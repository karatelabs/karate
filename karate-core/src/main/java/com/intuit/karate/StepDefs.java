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
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class StepDefs {

    private static final Logger logger = LoggerFactory.getLogger(StepDefs.class);

    private static final String VAR_REQUEST = "_request";    
    private static final String VAR_HEADERS = "_headers";
    private static final String VAR_MULTIPART = "_multipart";
    private static final String VAR_FORM_FIELDS = "_formfields";

    public StepDefs(String featureDir, ClassLoader fileClassLoader, String env) {
        context = new ScriptContext(false, featureDir, fileClassLoader, env);
    }     

    private String url;
    private WebTarget target;
    private Response response;
    private String accept;

    private final ScriptContext context;

    public ScriptContext getContext() {
        return context;
    }        

    @When("^url (.+)")
    public void url(String expression) {
        String temp = Script.preEval(expression, context).getAsString();
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
            String temp = Script.preEval(path, context).getAsString();
            target = target.path(temp);
        }
    }

    @When("^param ([^\\s]+) = (.+)")
    public void param(String name, String value) {
        hasUrlBeenSet();
        String temp = Script.preEval(value, context).getAsString();
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
        String temp = Script.preEval(value, context).getAsString();
        cookies.put(name, temp);
    }

    private Map<String, Object> getHeaders() {
        Map<String, Object> headers = context.vars.get(VAR_HEADERS, Map.class);
        if (headers == null) {
            headers = new HashMap<>();
            context.vars.put(VAR_HEADERS, headers);
        }
        return headers;
    }

    @When("^header ([^\\s]+) = (.+)")
    public void header(String name, String value) {
        Map<String, Object> headers = getHeaders();
        String temp = Script.preEval(value, context).getAsString();
        headers.put(name, temp);
    }

    private MultivaluedMap<String, Object> getFormFields() {
        MultivaluedMap<String, Object> formFields = context.vars.get(VAR_FORM_FIELDS, MultivaluedMap.class);
        if (formFields == null) {
            formFields = new MultivaluedHashMap<>();
            context.vars.put(VAR_FORM_FIELDS, formFields);
        }
        return formFields;
    }
    
    @When("^form field ([^\\s]+) = (.+)")
    public void formField(String name, String value) {
        MultivaluedMap<String, Object> formFields = getFormFields();
        String temp = Script.preEval(value, context).getAsString();
        formFields.add(name, temp);
    }    

    @When("^request$")
    public void requestDocString(String requestBody) {
        request(requestBody);
    }

    @When("^request (.+)")
    public void request(String requestBody) {
        Object value = Script.preEval(requestBody, context).getValue();
        logger.trace("request value is: {}", value);
        context.vars.put(VAR_REQUEST, value);
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
        Map<String, Object> headers = context.vars.get(VAR_HEADERS, Map.class);
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
        if (accept != null) {
            builder.accept(MediaType.valueOf(accept));
        }
        return builder;
    }
    
    @When("^accept (.+)")
    public void accept(String expression) {
        this.accept = expression;
    }    

    @When("^method (\\w+)")
    public void method(String method) {
        method = method.toUpperCase();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            MultivaluedMap<String, Object> formFields = context.vars.get(VAR_FORM_FIELDS, MultivaluedMap.class);
            if (formFields != null) {
                response = prepare().method(method, Entity.entity(formFields, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            } else {
                ScriptValue sv = context.vars.get(VAR_REQUEST);
                Entity entity;
                switch (sv.getType()) {
                    case JSON:
                        DocumentContext request = sv.getValue(DocumentContext.class);
                        entity = Entity.json(request.jsonString());
                        break;
                    case MAP:
                        Map<String, Object> requestMap = sv.getValue(Map.class);
                        request = JsonPath.parse(requestMap);
                        entity = Entity.json(request.jsonString());
                        break;
                    case XML:
                        Node node = sv.getValue(Node.class);
                        entity = Entity.xml(XmlUtils.toString(node));
                        break;
                    default:
                        entity = Entity.text(sv.getAsString());
                }
                response = prepare().method(method, entity);
            }
        } else {
            response = prepare().method(method);
        }
        unprepare();
    }

    private void unprepare() {
        context.vars.put(ScriptValueMap.VAR_RESPONSE_STATUS, response.getStatus());
        context.vars.remove(VAR_FORM_FIELDS);
        for (Map.Entry<String, NewCookie> entry : response.getCookies().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().getValue();
            getCookies().put(key, value);
            logger.trace("set cookie: {} - {}", key, entry.getValue());
        }
        DocumentContext headers = JsonPath.parse(response.getHeaders());
        logger.trace("set response headers: {}", headers.jsonString());
        context.vars.put(ScriptValueMap.VAR_RESPONSE_HEADERS, headers);
        String responseBody = response.readEntity(String.class);
        if (Script.isJson(responseBody)) {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, JsonUtils.toJsonDoc(responseBody));
        } else if (Script.isXml(responseBody)) {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, XmlUtils.toXmlDoc(responseBody));
        } else {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, responseBody);
        }
        reset();
    }

    @When("^reset")
    public void reset() {
        // reset url
        target = context.client.target(url);
        logger.trace("reset to base url: {}", url);
    }

    @When("^soap action( .+)?")
    public void soapAction(String action) {
        hasUrlBeenSet();
        action = Script.preEval(action, context).getAsString();
        if (action == null) {
            action = "";
        }
        logger.trace("soap action: '{}'", action);
        Document doc = context.vars.get(VAR_REQUEST, Document.class);
        String xml = XmlUtils.toString(doc);
        response = target.request().header("SOAPAction", action).method("POST", Entity.entity(xml, MediaType.TEXT_XML));
        String raw = response.readEntity(String.class);
        if (StringUtils.isNotBlank(raw)) {
            context.vars.put(ScriptValueMap.VAR_RESPONSE, XmlUtils.toXmlDoc(raw));
        } else {
            logger.warn("empty soap response, not parsing xml (response variable not set)");
        }
    }

    private MultiPart getMultiPart() {
        MultiPart mp = context.vars.get(VAR_MULTIPART, MultiPart.class);
        if (mp == null) {
            mp = new MultiPart();
            context.vars.put(VAR_MULTIPART, mp);
        }
        return mp;
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
        ScriptValue sv = Script.preEval(value, context);
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

    @When("^multipart post")
    public void multiPartPost() {
        String method = "POST";
        MultiPart mp = getMultiPart();
        MediaType mediaType = MediaType.MULTIPART_FORM_DATA_TYPE; // default
        Map<String, Object> headers = context.vars.get(VAR_HEADERS, Map.class);
        if (headers != null) {
            String type = (String) headers.get("Content-Type");
            if (type != null) { // override with user specified
                mediaType = MediaType.valueOf(type);
            }
        }        
        response = prepare().method(method, Entity.entity(mp, mediaType));
        unprepare();
    }

    @Then("^print (.+)")
    public void print(String exp) {
        String temp = Script.preEval(exp, context).getAsString();
        logger.info("[print] {}", temp);
    }

    @Then("^status (\\d+)")
    public void status(int status) {
        assertEquals(status, response.getStatus());
    }

    @Then("^match ([^\\s]+)( .+)? ==$")
    public void matchVariableDocString(String name, String path, String expected) {
        matchNamed(false, name, path, expected);
    }
    
    @Then("^match ([^\\s]+)( .+)? contains$")
    public void matchContainsDocString(String name, String path, String expected) {
        matchNamed(true, name, path, expected);
    }    

    @Then("^match ([^\\s]+)( .+)? == (.+)")
    public void matchVariable(String name, String path, String expected) {
        matchNamed(false, name, path, expected);
    }
    
    @Then("^match ([^\\s]+)( .+)? contains (.+)")
    public void matchContains(String name, String path, String expected) {
        matchNamed(true, name, path, expected);
    }    

    public void matchNamed(boolean contains, String name, String path, String expected) {
        AssertionResult ar = Script.matchNamed(contains, name, path, expected, context);
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
            fail(ar.message);
        }       
    }

}
