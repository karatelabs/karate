package com.intuit.karate.http;

import com.intuit.karate.Config;
import com.intuit.karate.core.ScenarioContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CustomDummyHttpClient extends DummyHttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomDummyHttpClient.class);

    private Map<String, Object> userDefined;
    
    @Override
    public void configure(Config config, ScenarioContext context) {
        userDefined = config.getUserDefined();
    }        

    @Override
    protected HttpResponse makeHttpRequest(String entity, ScenarioContext context) {
        HttpResponse response = new HttpResponse(0, 0);
        String message = "hello " + userDefined.get("name");
        response.setBody(message.getBytes());
        return response;
    }        
    
}
