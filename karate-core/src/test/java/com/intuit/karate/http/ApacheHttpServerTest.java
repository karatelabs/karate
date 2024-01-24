package com.intuit.karate.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.intuit.karate.core.Config;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.Variable;

class ApacheHttpServerTest {
    
    private ScenarioEngine engine;
    private Config config;
    private HttpHost host;
    private HttpContext context;

    private ApacheHttpClient client;

    @BeforeEach
    void configure() {
        engine = mock(ScenarioEngine.class);
        config = new Config();
        Mockito.when(engine.getConfig()).thenReturn(config);
        host = new HttpHost("foo.com");
        context = mock(HttpContext.class);

        client = new ApacheHttpClient(engine);        
    }

    @Test
    void noProxy() {
        HttpRoute route = determineRoute(host);
        Assertions.assertNull(route.getProxyHost());
        assertNull(route.getLocalAddress());
    }

    @Test
    void proxy() {
        config.configure("proxy", new Variable("http://proxy:80"));
        HttpRoute route =  determineRoute(host);
        assertEquals("http://proxy:80", route.getProxyHost().toURI());
    }

    @Test
    void nonProxyHosts() {
        Map<String, Object> proxyConfiguration = new HashMap<>();
        proxyConfiguration.put("uri", "http://proxy:80");
        proxyConfiguration.put("nonProxyHosts", Collections.singletonList("foo.com"));
        config.configure("proxy", new Variable(proxyConfiguration));

        HttpRoute nonProxiedRoute = determineRoute(host);
        assertNull(nonProxiedRoute.getProxyHost());

        HttpRoute proxiedRoute = determineRoute(new HttpHost("bar.com"));
        assertEquals("http://proxy:80", proxiedRoute.getProxyHost().toURI());
    }

    // From a Karate perspective, localAddress is primarily designed to be used with Gatling and is not related to proxy.
    // However, in apache client 5, it is handled by the RoutePlanner. 
    @Test
    void localAddress() {
        config.configure("localAddress", new Variable("localhost"));

        HttpRoute route = determineRoute(host);

        assertNull(route.getProxyHost());
        assertEquals("localhost", route.getLocalAddress().getHostName());
    }

    private HttpRoute determineRoute(HttpHost host) {
        try {
            return client.buildRoutePlanner(config).determineRoute(host, context);
        } catch (HttpException e) {
            throw new RuntimeException(e);
        }
    }
}
