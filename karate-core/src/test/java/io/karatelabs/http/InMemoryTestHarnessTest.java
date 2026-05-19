package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTestHarnessTest {

    @Test
    void testBasicRequestResponse() {
        InMemoryTestHarness harness = new InMemoryTestHarness(request ->
                HttpResponse.text("hello " + request.getParam("name")));

        HttpResponse response = harness.get("/test?name=world");
        assertEquals("hello world", response.getBodyString());
    }

    @Test
    void testPathMatching() {
        InMemoryTestHarness harness = new InMemoryTestHarness(request -> {
            if (request.pathMatches("/token")) {
                return HttpResponse.text("token-response");
            } else if (request.pathMatches("/api/{id}")) {
                return HttpResponse.text("api-response");
            } else {
                HttpResponse response = new HttpResponse();
                response.setStatus(404);
                return response;
            }
        });

        assertEquals("token-response", harness.get("/token").getBodyString());
        assertEquals("api-response", harness.get("/api/test").getBodyString());
        assertEquals(404, harness.get("/other").getStatus());
    }

    @Test
    void testSwappableHandler() {
        InMemoryTestHarness harness = new InMemoryTestHarness();

        // First handler
        harness.setHandler(req -> HttpResponse.text("handler1"));
        assertEquals("handler1", harness.get("/").getBodyString());

        // Swap handler
        harness.setHandler(req -> HttpResponse.text("handler2"));
        assertEquals("handler2", harness.get("/").getBodyString());
    }

    @Test
    void testPostWithBody() {
        InMemoryTestHarness harness = new InMemoryTestHarness(request ->
                HttpResponse.text("received: " + request.getBodyString()));

        HttpResponse response = harness.post("/submit", "test-body");
        assertEquals("received: test-body", response.getBodyString());
    }

    @Test
    void testNoHandler() {
        InMemoryTestHarness harness = new InMemoryTestHarness();
        HttpResponse response = harness.get("/test");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testHandlerException() {
        InMemoryTestHarness harness = new InMemoryTestHarness(request -> {
            throw new RuntimeException("simulated error");
        });
        HttpResponse response = harness.get("/test");
        assertEquals(500, response.getStatus());
        assertTrue(response.getBodyString().contains("simulated error"));
    }

}
