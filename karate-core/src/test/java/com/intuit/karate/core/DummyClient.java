package com.intuit.karate.core;

import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;

/**
 *
 * @author pthomas3
 */
public class DummyClient implements HttpClient {

    private Config config = new Config();
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
        throw new UnsupportedOperationException("not implemented");
    }   

}
