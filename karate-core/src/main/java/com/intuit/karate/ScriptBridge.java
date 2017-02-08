package com.intuit.karate;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author pthomas3
 */
public class ScriptBridge {
    
    private final ScriptContext context;
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptBridge.class);    
    
    public ScriptBridge(ScriptContext context) {
        this.context = context;       
    }
    
    public Map<String, Object> request(Map<String, Object> in) {
        String method = (String) in.get("method");
        if (method == null) {
            method = "get";
        }
        method = method.toUpperCase();
        String url = (String) in.get("url");
        Map<String, Object> body = (Map) in.get("body");
        Response resp = null;
        if (body != null) {
            String json = JsonPath.parse(body).jsonString();
            resp = context.client.target(url).request().method(method, Entity.json(json));
        } else {
            resp = context.client.target(url).request().method(method);
        }
        Map<String, Object> out = new HashMap();
        out.put("status", resp.getStatus());
        String responseString = resp.readEntity(String.class);
        responseString = StringUtils.trimToNull(responseString);
        if (responseString != null) {
            if (Script.isJson(responseString)) {
                Map<String, Object> responseMap = JsonUtils.toJsonDoc(responseString).read("$");
                out.put("body", responseMap);
            } else if (Script.isXml(responseString)) {
                Document doc = XmlUtils.toXmlDoc(responseString);
                out.put("body", XmlUtils.toMap(doc));
            } else {
                out.put("body", responseString);
            }
        }
        return out;
    }
    
    public Object read(String fileName) {
        ScriptValue sv = FileUtils.readFile(fileName, context);
        return sv.getValue();
    }
    
    public void set(String name, Object o) {
        context.vars.put(name, o);
    }
    
    public Object get(String name) {
        ScriptValue sv = context.vars.get(name);
        if (sv != null) {
            return sv.getAfterConvertingToMapIfNeeded();
        } else {
            logger.trace("variable is null or does not exist: {}", name);
            return null;
        }
    }    
    
    public String getEnv() {
        return context.env;
    }
    
    public Properties getProperties() {
        return System.getProperties();
    }
    
    public void log(Object ... objects) {
        logger.info("{}", new LogWrapper(objects));
    }        
    
    // make sure toString() is lazy
    static class LogWrapper {
        
        private final Object[] objects;
        
        LogWrapper(Object ... objects) {
            this.objects = objects;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Object o : objects) {
                sb.append(o).append(' ');                
            }
            return sb.toString();
        }
                
    }
    
}
