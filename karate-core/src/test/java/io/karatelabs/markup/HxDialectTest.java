package io.karatelabs.markup;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HxDialect and HxMethodProcessor.
 * Covers dialect registration and HTTP method attributes (ka:get, ka:post, ka:put, ka:patch, ka:delete).
 */
class HxDialectTest {

    @Test
    void testDialectDefaults() {
        HxDialect dialect = new HxDialect();
        assertEquals("Htmx", dialect.getName());
        assertEquals("ka", dialect.getPrefix());
        assertNull(dialect.getContextPath());
    }

    @Test
    void testDialectWithContextPath() {
        HxDialect dialect = new HxDialect("/app");
        assertEquals("/app", dialect.getContextPath());
    }

    @Test
    void testDialectWithMarkupConfig() {
        MarkupConfig config = new MarkupConfig();
        config.setContextPath("/myapp");
        HxDialect dialect = new HxDialect(config);
        assertEquals("/myapp", dialect.getContextPath());
    }

    @Test
    void testDialectProcessorsCount() {
        HxDialect dialect = new HxDialect();
        // 5 method processors + 1 vals processor + 21 generic processors + 1 data processor
        assertEquals(28, dialect.getProcessors("ka").size());
    }

    @Test
    void testDialectRegistrationWithMarkup() {
        Markup markup = createMarkup();
        String result = markup.processString("<div th:text=\"${name}\">placeholder</div>", Map.of("name", "test"));
        assertEquals("<div>test</div>", result);
    }

    // =============================================================================
    // HTTP Method Processors (ka:get, ka:post, ka:put, ka:patch, ka:delete)
    // =============================================================================

    private Markup createMarkup() {
        return createMarkup(null);
    }

    private Markup createMarkup(String contextPath) {
        Engine engine = new Engine();
        MarkupConfig config = new MarkupConfig();
        config.setResolver(new RootResourceResolver("classpath:templates"));
        config.setContextPath(contextPath);
        config.setEngineSupplier(() -> engine);
        return Markup.init(config, new HxDialect(config));
    }

    @Test
    void testKaGetConvertsToHxGet() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:get=\"/users\">Load</button>", Map.of());
        assertEquals("<button hx-get=\"/users\">Load</button>", result);
    }

    @Test
    void testKaPostConvertsToHxPost() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:post=\"/users\">Save</button>", Map.of());
        assertEquals("<button hx-post=\"/users\">Save</button>", result);
    }

    @Test
    void testKaPutConvertsToHxPut() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:put=\"/users/1\">Update</button>", Map.of());
        assertEquals("<button hx-put=\"/users/1\">Update</button>", result);
    }

    @Test
    void testKaPatchConvertsToHxPatch() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:patch=\"/users/1\">Patch</button>", Map.of());
        assertEquals("<button hx-patch=\"/users/1\">Patch</button>", result);
    }

    @Test
    void testKaDeleteConvertsToHxDelete() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:delete=\"/users/1\">Delete</button>", Map.of());
        assertEquals("<button hx-delete=\"/users/1\">Delete</button>", result);
    }

    @Test
    void testMethodWithContextPath() {
        Markup markup = createMarkup("/app");
        String result = markup.processString("<button ka:get=\"/users\">Load</button>", Map.of());
        assertEquals("<button hx-get=\"/app/users\">Load</button>", result);
    }

    @Test
    void testExternalUrlIgnoresContextPath() {
        Markup markup = createMarkup("/app");
        String result = markup.processString("<button ka:get=\"https://example.com/api\">Load</button>", Map.of());
        assertEquals("<button hx-get=\"https://example.com/api\">Load</button>", result);
    }

    @Test
    void testMethodWithExpression() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:get=\"/users/${id}\">Load</button>", Map.of("id", "123"));
        assertEquals("<button hx-get=\"/users/123\">Load</button>", result);
    }

    @Test
    void testMethodWithComplexExpression() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:get=\"/items/${category}/${id}\">View</button>",
                Map.of("category", "books", "id", "42"));
        assertEquals("<button hx-get=\"/items/books/42\">View</button>", result);
    }

    @Test
    void testMultipleMethodAttributes() {
        Markup markup = createMarkup();
        String result = markup.processString("<button ka:get=\"/data\" ka:post=\"/save\">Multi</button>", Map.of());
        assertTrue(result.contains("hx-get=\"/data\""));
        assertTrue(result.contains("hx-post=\"/save\""));
    }

    @Test
    void testMethodPreservesOtherAttributes() {
        Markup markup = createMarkup();
        String result = markup.processString("<button id=\"btn\" class=\"primary\" ka:get=\"/users\">Load</button>", Map.of());
        assertTrue(result.contains("id=\"btn\""));
        assertTrue(result.contains("class=\"primary\""));
        assertTrue(result.contains("hx-get=\"/users\""));
    }

    @Test
    void testThisKeyword() {
        MarkupConfig config = new MarkupConfig();
        config.setResolver(new RootResourceResolver("classpath:markup"));
        config.setEngineSupplier(Engine::new);
        Markup markup = Markup.init(config, new HxDialect(config));

        String result = markup.processPath("htmx-this.html", Map.of());
        assertEquals("<button hx-get=\"/htmx-this\">Reload</button>", result);
    }

    @Test
    void testThisKeywordWithContextPath() {
        MarkupConfig config = new MarkupConfig();
        config.setResolver(new RootResourceResolver("classpath:markup"));
        config.setContextPath("/app");
        config.setEngineSupplier(Engine::new);
        Markup markup = Markup.init(config, new HxDialect(config));

        String result = markup.processPath("htmx-this.html", Map.of());
        assertEquals("<button hx-get=\"/app/htmx-this\">Reload</button>", result);
    }

}
