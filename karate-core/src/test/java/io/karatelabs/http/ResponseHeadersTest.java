package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: bracket-set on {@code response.headers} used to silently 500
 * because {@code Map<String, List<String>>.put(name, "stringValue")} corrupted
 * the entry and Netty's serializer threw a {@code ClassCastException} far from
 * the JS handler. The JS-side setters now route through {@code setHeader},
 * coercing strings / lists / null appropriately.
 *
 * <p>Backed by {@code demo/api/headers.js} which exercises:
 * <ul>
 *   <li>{@code response.header('X', 'v')} (2-arg setter)</li>
 *   <li>{@code response.headers['X'] = 'v'} (bracket-set string)</li>
 *   <li>{@code response.headers['X'] = ['a','b']} (bracket-set list)</li>
 *   <li>{@code response.headers['X'] = null} (remove)</li>
 * </ul>
 */
class ResponseHeadersTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .apiPrefix("/api/")
                .devMode(true)
                .csrfEnabled(false);
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testHeaderSetPathsAllSucceed() {
        HttpResponse r = harness.get("/api/headers");
        assertEquals(200, r.getStatus(), "API handler must complete cleanly, not silent-500");
        assertTrue(r.getBodyString().contains("\"ok\":true"),
                "JS body assignment must round-trip: " + r.getBodyString());
        assertTrue(r.getBodyString().contains("\"echoed\":\"one\""),
                "header read-back after set must return the assigned value: " + r.getBodyString());
    }

    @Test
    void testTwoArgHeaderSetterReachesWire() {
        HttpResponse r = harness.get("/api/headers");
        assertEquals("one", r.getHeader("X-Setter"),
                "response.header(name, value) 2-arg form must set the header");
    }

    @Test
    void testBracketSetStringReachesWire() {
        HttpResponse r = harness.get("/api/headers");
        assertEquals("two", r.getHeader("X-Bracket"),
                "response.headers['X'] = 'v' must set the header (bracket-set regression)");
    }

    @Test
    void testBracketSetListReachesWire() {
        HttpResponse r = harness.get("/api/headers");
        List<String> values = r.getHeaderValues("X-Multi");
        assertEquals(List.of("a", "b"), values,
                "response.headers['X'] = ['a','b'] must preserve the list shape on wire");
    }

    @Test
    void testNullRemovesHeader() {
        HttpResponse r = harness.get("/api/headers");
        assertNull(r.getHeader("X-Removed"),
                "response.headers['X'] = null must remove the header");
    }
}
