package com.intuit.karate.http;

import com.intuit.karate.Http;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.MockServer;
import com.intuit.karate.core.ScenarioRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;

class HttpHookTest {
   
    static MockServer server;
    
    @BeforeAll
    static void beforeAll() {
        server = MockServer
                .feature("classpath:com/intuit/karate/http/mock.feature")
                .http(0).build();
    }    

    static class TestRuntimeHook implements RuntimeHook {
        @Override
        public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
            String url = request.getUrl();
            request.setUrl(url + "/foo");
        }

        @Override
        public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
            response.setBody(response.json().set("bar", "baz").toString());
        }
    }

    @Test
    void testInvokeWithoutHook() {
        Response response = Http.to("http://localhost:" + server.getPort() + "/hello").get();
        assertEquals("/hello", response.json().get("path").toString());
    }

    @Test
    void testInvokeWithHook() {
        Response response = Http.to("http://localhost:" + server.getPort() + "/hello")
                .hook(new TestRuntimeHook()).get();
        assertEquals("/hello/foo", response.json().get("path").toString());
        assertEquals("baz", response.json().get("bar").toString());
    }

}
