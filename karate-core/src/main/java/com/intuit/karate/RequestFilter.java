package com.intuit.karate;

import static com.intuit.karate.ScriptValue.Type.JS_FUNCTION;
import com.jayway.jsonpath.DocumentContext;
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

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        ScriptContext context = (ScriptContext) ctx.getProperty(ScriptContext.KARATE_DOT_CONTEXT);
        ScriptValue headersValue = context.headers;
        Map<String, Object> headersMap;
        switch (headersValue.getType()) {
            case JS_FUNCTION:
                ScriptObjectMirror som = headersValue.getValue(ScriptObjectMirror.class);
                ScriptValue sv = Script.evalFunctionCall(som, null, context);
                switch (sv.getType()) {
                    case JS_OBJECT:
                        headersMap = Script.toMap(sv.getValue(ScriptObjectMirror.class));
                        break;
                    case MAP:
                        headersMap = sv.getValue(Map.class);
                        break;
                    default:
                        logger.trace("custom headers function returned: {}", sv);
                        return; // abort           
                }
                break;
            case JSON:
                DocumentContext json = headersValue.getValue(DocumentContext.class);
                headersMap = json.read("$");
                break;
            default:
                logger.trace("configured 'headers' is not a map-like object or js function: {}", headersValue);
                return;
        }
        MultivaluedMap headers = ctx.getHeaders();
        for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
            logger.trace("setting header: {}", entry);
            headers.putSingle(entry.getKey(), entry.getValue());
        }
    }

}
