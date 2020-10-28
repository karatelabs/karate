package com.intuit.karate.runtime;

import com.intuit.karate.server.HttpClient;
import com.intuit.karate.server.HttpRequest;
import com.intuit.karate.server.Response;

/**
 *
 * @author pthomas3
 */
public class DummyClient implements HttpClient {

    private Config config = new Config();

    @Override
    public void configChanged(String name) {

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
        throw new UnsupportedOperationException("not implemented");
    }

}
