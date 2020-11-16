package com.intuit.karate.core;

import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;
import com.intuit.karate.http.ServerHandler;

/**
 *
 * @author pthomas3
 */
public class MockClient implements HttpClient {

    private final ServerHandler handler;
    private Config config = new Config();
    
    public MockClient(ServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public Response invoke(HttpRequest request) {
        return handler.handle(request.toRequest());
    }

}
