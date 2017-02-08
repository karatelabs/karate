package com.intuit.karate;

import java.io.IOException;
import java.util.Map;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RequestFilter implements ClientRequestFilter { 
    
    private static final Logger logger = LoggerFactory.getLogger(RequestFilter.class);
    
    private final ScriptContext context;
    
    public RequestFilter(ScriptContext context) {
        this.context = context;
    }

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {        
        ScriptValue sv = Script.call(ScriptValueMap.VAR_HEADERS, null, context);
        Map<String, Object> callResult;
        switch (sv.getType()) {
            case JS_OBJECT:
                callResult = Script.toMap(sv.getValue(ScriptObjectMirror.class));
                break;
            case MAP:
                callResult = sv.getValue(Map.class);
                break;
            default:
                logger.trace("custom headers function returned: {}", sv);
                return; // abort           
        }
        MultivaluedMap headers = ctx.getHeaders();
        for (Map.Entry<String, Object> entry : callResult.entrySet()) {
            logger.trace("setting header: {}", entry);
            headers.putSingle(entry.getKey(), entry.getValue());
        }
    }
    
}
