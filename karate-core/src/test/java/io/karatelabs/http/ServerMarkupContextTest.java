package io.karatelabs.http;

import io.karatelabs.common.Resource;
import io.karatelabs.js.JavaInvokable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerMarkupContextTest {

    private HttpRequest request;
    private HttpResponse response;
    private ServerConfig config;
    private ServerMarkupContext context;

    @BeforeEach
    void setUp() {
        request = new HttpRequest();
        request.setUrl("http://localhost/test");
        request.setMethod("GET");
        response = new HttpResponse();
        config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .sessionExpirySeconds(600);
        context = new ServerMarkupContext(request, response, config);
    }

    // MarkupContext tests

    @Test
    void testToJson() {
        Map<String, Object> obj = Map.of("name", "john", "age", 25);
        String json = context.toJson(obj);

        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"john\""));
        assertTrue(json.contains("\"age\""));
        assertTrue(json.contains("25"));
    }

    @Test
    void testFromJson() {
        String json = "{\"name\":\"john\",\"age\":25}";
        Object result = context.fromJson(json);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("john", map.get("name"));
        assertEquals(25, map.get("age"));
    }

    @Test
    void testToJsonViaJsGet() {
        JavaInvokable toJson = (JavaInvokable) context.jsGet("toJson");
        String json = (String) toJson.invoke(Map.of("key", "value"));

        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    void testFromJsonViaJsGet() {
        JavaInvokable fromJson = (JavaInvokable) context.jsGet("fromJson");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fromJson.invoke("{\"key\":\"value\"}");

        assertEquals("value", result.get("key"));
    }

    @Test
    void testReadWithoutResolver() {
        assertThrows(RuntimeException.class, () -> context.read("test.txt"));
    }

    @Test
    void testReadWithResolver() {
        context.setResourceResolver(path -> Resource.text("test content"));
        String content = context.read("test.txt");
        assertEquals("test content", content);
    }

    @Test
    void testReadBytesWithResolver() {
        context.setResourceResolver(path -> Resource.text("test bytes"));
        byte[] bytes = context.readBytes("test.txt");
        assertEquals("test bytes", new String(bytes));
    }

    @Test
    void testTemplateName() {
        assertNull(context.getTemplateName());

        context.setTemplateName("index.html");
        assertEquals("index.html", context.getTemplateName());
        assertEquals("index.html", context.jsGet("template"));
    }

    @Test
    void testCallerTemplateName() {
        assertNull(context.getCallerTemplateName());

        context.setCallerTemplateName("layout.html");
        assertEquals("layout.html", context.getCallerTemplateName());
        assertEquals("layout.html", context.jsGet("caller"));
    }

    // Server-specific method tests

    @Test
    void testRedirect() {
        assertNull(context.getRedirectPath());
        assertFalse(context.hasRedirect());

        context.redirect("/signin");

        assertEquals("/signin", context.getRedirectPath());
        assertTrue(context.hasRedirect());
    }

    @Test
    void testRedirectViaJsGet() {
        JavaInvokable redirect = (JavaInvokable) context.jsGet("redirect");
        redirect.invoke("/dashboard");

        assertEquals("/dashboard", context.getRedirectPath());
    }

    @Test
    void testRedirectViaJsGetNoArgs() {
        JavaInvokable redirect = (JavaInvokable) context.jsGet("redirect");
        assertThrows(RuntimeException.class, () -> redirect.invoke());
    }

    @Test
    void testLog() {
        context.log("message1");
        context.log("message2", "with", "multiple", "args");

        List<String> messages = context.getLogMessages();
        assertEquals(2, messages.size());
        assertEquals("message1", messages.get(0));
        assertEquals("message2 with multiple args", messages.get(1));
    }

    @Test
    void testLogViaJsGet() {
        JavaInvokable log = (JavaInvokable) context.jsGet("log");
        log.invoke("test", "message");

        assertEquals(1, context.getLogMessages().size());
        assertTrue(context.getLogMessages().get(0).contains("test"));
    }

    @Test
    void testLogWithCustomHandler() {
        List<String> capturedLogs = new ArrayList<>();
        ServerConfig configWithHandler = new ServerConfig()
                .logHandler(capturedLogs::add);
        ServerMarkupContext ctxWithHandler = new ServerMarkupContext(request, response, configWithHandler);

        ctxWithHandler.log("custom", "log", "message");

        // Message captured via handler
        assertEquals(1, capturedLogs.size());
        assertEquals("custom log message", capturedLogs.get(0));
        // Also stored in context
        assertEquals(1, ctxWithHandler.getLogMessages().size());
    }

    @Test
    void testUuid() {
        String uuid1 = context.uuid();
        String uuid2 = context.uuid();

        assertNotNull(uuid1);
        assertNotNull(uuid2);
        assertNotEquals(uuid1, uuid2);
        // UUID format: 8-4-4-4-12
        assertTrue(uuid1.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void testUuidViaJsGet() {
        JavaInvokable uuid = (JavaInvokable) context.jsGet("uuid");
        String result = (String) uuid.invoke();

        assertNotNull(result);
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void testInit() {
        assertNull(context.getSession());
        assertTrue(context.isClosed());

        context.init();

        assertNotNull(context.getSession());
        assertNotNull(context.getSession().getId());
        assertFalse(context.isClosed());
    }

    @Test
    void testInitViaJsGet() {
        JavaInvokable init = (JavaInvokable) context.jsGet("init");
        init.invoke();

        assertNotNull(context.getSession());
    }

    @Test
    void testInitWithoutSessionStore() {
        ServerMarkupContext ctx = new ServerMarkupContext(request, response, new ServerConfig());
        ctx.init();
        // Should not throw, just no-op
        assertNull(ctx.getSession());
    }

    @Test
    void testClose() {
        context.init();
        String sessionId = context.getSession().getId();
        assertNotNull(config.getSessionStore().get(sessionId));

        context.close();

        assertNull(context.getSession());
        assertTrue(context.isClosed());
        // Session should be deleted from store
        assertNull(config.getSessionStore().get(sessionId));
    }

    @Test
    void testCloseViaJsGet() {
        context.init();
        JavaInvokable close = (JavaInvokable) context.jsGet("close");
        close.invoke();

        assertTrue(context.isClosed());
    }

    @Test
    void testSwitchTemplate() {
        assertFalse(context.isSwitched());
        assertNull(context.getSwitchTemplate());

        context.switchTemplate("other.html");

        assertTrue(context.isSwitched());
        assertEquals("other.html", context.getSwitchTemplate());
    }

    @Test
    void testSwitchViaJsGet() {
        JavaInvokable switchFn = (JavaInvokable) context.jsGet("switch");
        switchFn.invoke("new-template.html");

        assertTrue(context.isSwitched());
        assertEquals("new-template.html", context.getSwitchTemplate());
    }

    @Test
    void testSwitchViaJsGetNoArgs() {
        JavaInvokable switchFn = (JavaInvokable) context.jsGet("switch");
        assertThrows(RuntimeException.class, () -> switchFn.invoke());
    }

    // Property tests

    @Test
    void testAjaxWithHxRequest() {
        assertFalse(context.isAjax());
        assertFalse((Boolean) context.jsGet("ajax"));

        request.putHeader("HX-Request", "true");

        assertTrue(context.isAjax());
        assertTrue((Boolean) context.jsGet("ajax"));
    }

    @Test
    void testAjaxWithXhr() {
        request.putHeader("X-Requested-With", "XMLHttpRequest");

        assertTrue(context.isAjax());
        assertTrue((Boolean) context.jsGet("ajax"));
    }

    @Test
    void testSessionId() {
        assertNull(context.jsGet("sessionId"));

        context.init();
        String sessionId = context.getSession().getId();

        assertEquals(sessionId, context.jsGet("sessionId"));
    }

    @Test
    void testClosedProperty() {
        // Initially closed (no session)
        assertTrue((Boolean) context.jsGet("closed"));

        context.init();
        assertFalse((Boolean) context.jsGet("closed"));

        context.close();
        assertTrue((Boolean) context.jsGet("closed"));
    }

    @Test
    void testSwitchedProperty() {
        assertFalse((Boolean) context.jsGet("switched"));

        context.switchTemplate("other.html");

        assertTrue((Boolean) context.jsGet("switched"));
    }

    @Test
    void testFlash() {
        @SuppressWarnings("unchecked")
        Map<String, Object> flash = (Map<String, Object>) context.jsGet("flash");
        assertNotNull(flash);
        assertTrue(flash.isEmpty());

        context.getFlash().put("message", "Success!");

        @SuppressWarnings("unchecked")
        Map<String, Object> flash2 = (Map<String, Object>) context.jsGet("flash");
        assertEquals("Success!", flash2.get("message"));
    }

    @Test
    void testFlashPersistToSession() {
        context.init();
        context.getFlash().put("error", "Something went wrong");
        context.getFlash().put("info", "Please try again");

        // Persist flash to session
        context.persistFlashToSession();

        // Verify flash is stored in session
        @SuppressWarnings("unchecked")
        Map<String, Object> storedFlash = (Map<String, Object>) context.getSession().getMember(ServerMarkupContext.FLASH_SESSION_KEY);
        assertNotNull(storedFlash);
        assertEquals("Something went wrong", storedFlash.get("error"));
        assertEquals("Please try again", storedFlash.get("info"));
    }

    @Test
    void testFlashLoadFromSession() {
        context.init();

        // Simulate flash stored from previous request
        Map<String, Object> previousFlash = new java.util.HashMap<>();
        previousFlash.put("success", "Item created!");
        context.getSession().putMember(ServerMarkupContext.FLASH_SESSION_KEY, previousFlash);

        // Load flash from session
        context.loadFlashFromSession();

        // Verify flash is loaded
        assertEquals("Item created!", context.getFlash().get("success"));

        // Verify flash is cleared from session (one-time display)
        assertNull(context.getSession().getMember(ServerMarkupContext.FLASH_SESSION_KEY));
    }

    @Test
    void testFlashPersistWithoutSession() {
        // No session initialized
        context.getFlash().put("message", "Test");

        // Should not throw
        context.persistFlashToSession();
    }

    @Test
    void testFlashLoadWithoutSession() {
        // No session initialized
        // Should not throw
        context.loadFlashFromSession();
        assertTrue(context.getFlash().isEmpty());
    }

    @Test
    void testRequestProperty() {
        assertSame(request, context.jsGet("request"));
    }

    @Test
    void testResponseProperty() {
        assertSame(response, context.jsGet("response"));
    }

    @Test
    void testSessionProperty() {
        assertNull(context.jsGet("session"));

        context.init();

        assertSame(context.getSession(), context.jsGet("session"));
    }

    // Session management tests

    @Test
    void testSetSession() {
        Session session = new Session("test-id", new java.util.HashMap<>(), 0, 0, 0);
        context.setSession(session);

        assertSame(session, context.getSession());
        assertFalse(context.isClosed());
    }

    @Test
    void testSetSessionNull() {
        context.init();
        assertFalse(context.isClosed());

        context.setSession(null);

        assertNull(context.getSession());
        assertTrue(context.isClosed());
    }

    // Getter tests

    @Test
    void testGetters() {
        assertSame(request, context.getRequest());
        assertSame(response, context.getResponse());
        assertSame(config, context.getConfig());
    }

    @Test
    void testJsGetUnknownKey() {
        assertNull(context.jsGet("unknownKey"));
    }

    // CSRF tests

    @Test
    void testCsrfTokenWithSession() {
        context.init();

        CsrfProtection.CsrfToken csrf = (CsrfProtection.CsrfToken) context.jsGet("csrf");

        assertNotNull(csrf);
        assertNotNull(csrf.getToken());
        assertEquals("X-CSRF-Token", csrf.getHeaderName());
        assertEquals("_csrf", csrf.getFieldName());
    }

    @Test
    void testCsrfTokenWithoutSession() {
        // No session initialized
        CsrfProtection.CsrfToken csrf = (CsrfProtection.CsrfToken) context.jsGet("csrf");

        // Should still return token object, but with null token
        assertNotNull(csrf);
        assertNull(csrf.getToken());
    }

    @Test
    void testCsrfTokenDisabled() {
        ServerConfig configNoCsrf = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .csrfEnabled(false);
        ServerMarkupContext contextNoCsrf = new ServerMarkupContext(request, response, configNoCsrf);
        contextNoCsrf.init();

        assertNull(contextNoCsrf.jsGet("csrf"));
    }

    @Test
    void testCsrfTokenViaGetMethod() {
        context.init();

        CsrfProtection.CsrfToken csrf = context.getCsrfToken();

        assertNotNull(csrf);
        assertNotNull(csrf.getToken());
    }

}
