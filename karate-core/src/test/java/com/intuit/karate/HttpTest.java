package com.intuit.karate;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpTest {

    static class TestRuntimeHook implements RuntimeHook {
        @Override
        public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
            String url = request.getUrl();
            request.setUrl(url.substring(0, url.length() - 1) + "2");
        }

        @Override
        public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
            response.setBody(response.json().set("username", "Karate").toString());
        }
    }

    @Test
    void testInvokeWithoutHook() {
        Response response = Http.to("https://jsonplaceholder.typicode.com/users/1").header("Accept", "application/json").get();
        assertEquals("1", response.json().get("id").toString());
        assertEquals("Bret", response.json().get("username").toString());
    }

    @Test
    void testInvokeWithHook() {
        Response response = Http.to("https://jsonplaceholder.typicode.com/users/1").header("Accept", "application/xml").hook(new TestRuntimeHook()).get();
        assertEquals("2", response.json().get("id").toString());
        assertEquals("Karate", response.json().get("username").toString());
    }

}
