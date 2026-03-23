package io.karatelabs.markup;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTMX attribute processors.
 * Covers ka:vals (HxValsProcessor) and generic attributes (HxGenericProcessor).
 */
class HxAttributeTest {

    private Markup markup;

    @BeforeEach
    void setUp() {
        Engine engine = new Engine();
        MarkupConfig config = new MarkupConfig();
        config.setResolver(new RootResourceResolver("classpath:templates"));
        config.setEngineSupplier(() -> engine);
        markup = Markup.init(config, new HxDialect(config));
    }

    // =============================================================================
    // ka:vals (HxValsProcessor)
    // =============================================================================

    @Test
    void testValsSimpleKeyValue() {
        String result = markup.processString("<button ka:vals=\"edit:true\">Edit</button>", Map.of());
        assertEquals("<button hx-vals='{\"edit\":true}'>Edit</button>", result);
    }

    @Test
    void testValsStringValue() {
        String result = markup.processString("<button ka:vals=\"mode:'edit'\">Edit</button>", Map.of());
        assertEquals("<button hx-vals='{\"mode\":\"edit\"}'>Edit</button>", result);
    }

    @Test
    void testValsMultipleKeyValues() {
        String result = markup.processString("<button ka:vals=\"page:1,size:10\">Load</button>", Map.of());
        assertTrue(result.contains("hx-vals='"));
        assertTrue(result.contains("\"page\":1"));
        assertTrue(result.contains("\"size\":10"));
    }

    @Test
    void testValsWithVariableReference() {
        String result = markup.processString("<button ka:vals=\"id:item.id\">View</button>",
                Map.of("item", Map.of("id", 123)));
        assertEquals("<button hx-vals='{\"id\":123}'>View</button>", result);
    }

    @Test
    void testValsCombinedWithMethod() {
        String result = markup.processString("<button ka:get=\"/items\" ka:vals=\"page:1\">Load</button>", Map.of());
        assertTrue(result.contains("hx-get=\"/items\""));
        assertTrue(result.contains("hx-vals='"));
    }

    // =============================================================================
    // Generic HTMX Attributes (HxGenericProcessor)
    // =============================================================================

    @Test
    void testTarget() {
        String result = markup.processString("<button ka:target=\"#results\">Click</button>", Map.of());
        assertEquals("<button hx-target=\"#results\">Click</button>", result);
    }

    @Test
    void testSwap() {
        String result = markup.processString("<button ka:swap=\"outerHTML\">Click</button>", Map.of());
        assertEquals("<button hx-swap=\"outerHTML\">Click</button>", result);
    }

    @Test
    void testTrigger() {
        String result = markup.processString("<input ka:trigger=\"keyup changed delay:500ms\"/>", Map.of());
        assertEquals("<input hx-trigger=\"keyup changed delay:500ms\"/>", result);
    }

    @Test
    void testPushUrl() {
        String result = markup.processString("<a ka:push-url=\"true\" href=\"/page\">Link</a>", Map.of());
        assertTrue(result.contains("hx-push-url=\"true\""));
        assertTrue(result.contains("href=\"/page\""));
    }

    @Test
    void testSelect() {
        String result = markup.processString("<div ka:select=\".content\">Partial</div>", Map.of());
        assertEquals("<div hx-select=\".content\">Partial</div>", result);
    }

    @Test
    void testConfirm() {
        String result = markup.processString("<button ka:confirm=\"Are you sure?\">Delete</button>", Map.of());
        assertEquals("<button hx-confirm=\"Are you sure?\">Delete</button>", result);
    }

    @Test
    void testIndicator() {
        String result = markup.processString("<button ka:indicator=\"#spinner\">Load</button>", Map.of());
        assertEquals("<button hx-indicator=\"#spinner\">Load</button>", result);
    }

    @Test
    void testBoost() {
        String result = markup.processString("<div ka:boost=\"true\"><a href=\"/\">Home</a></div>", Map.of());
        assertEquals("<div hx-boost=\"true\"><a href=\"/\">Home</a></div>", result);
    }

    @Test
    void testInclude() {
        String result = markup.processString("<button ka:include=\"[name='extra']\">Submit</button>", Map.of());
        assertEquals("<button hx-include=\"[name='extra']\">Submit</button>", result);
    }

    @Test
    void testSync() {
        String result = markup.processString("<button ka:sync=\"closest form:abort\">Submit</button>", Map.of());
        assertEquals("<button hx-sync=\"closest form:abort\">Submit</button>", result);
    }

    @Test
    void testDisabledElt() {
        String result = markup.processString("<button ka:disabled-elt=\"this\">Submit</button>", Map.of());
        assertEquals("<button hx-disabled-elt=\"this\">Submit</button>", result);
    }

    @Test
    void testReplaceUrl() {
        String result = markup.processString("<a ka:replace-url=\"true\" href=\"/new\">Link</a>", Map.of());
        assertTrue(result.contains("hx-replace-url=\"true\""));
        assertTrue(result.contains("href=\"/new\""));
    }

    @Test
    void testValidate() {
        String result = markup.processString("<form ka:validate=\"true\">Form</form>", Map.of());
        assertEquals("<form hx-validate=\"true\">Form</form>", result);
    }

    @Test
    void testPrompt() {
        String result = markup.processString("<button ka:prompt=\"Enter value:\">Prompt</button>", Map.of());
        assertEquals("<button hx-prompt=\"Enter value:\">Prompt</button>", result);
    }

    @Test
    void testHeaders() {
        String result = markup.processString("<button ka:headers=\"js:{Authorization:'Bearer token'}\">Auth</button>", Map.of());
        assertEquals("<button hx-headers=\"js:{Authorization:'Bearer token'}\">Auth</button>", result);
    }

    @Test
    void testExt() {
        String result = markup.processString("<div ka:ext=\"json-enc\">Content</div>", Map.of());
        assertEquals("<div hx-ext=\"json-enc\">Content</div>", result);
    }

    @Test
    void testPreserve() {
        String result = markup.processString("<video ka:preserve=\"true\">Video</video>", Map.of());
        assertEquals("<video hx-preserve=\"true\">Video</video>", result);
    }

    // =============================================================================
    // Expression Support in Generic Attributes
    // =============================================================================

    @Test
    void testTargetWithExpression() {
        String result = markup.processString("<button ka:target=\"#item-${id}\">Click</button>", Map.of("id", "123"));
        assertEquals("<button hx-target=\"#item-123\">Click</button>", result);
    }

    @Test
    void testSwapWithExpression() {
        String result = markup.processString("<button ka:swap=\"${swapMethod}\">Click</button>", Map.of("swapMethod", "innerHTML"));
        assertEquals("<button hx-swap=\"innerHTML\">Click</button>", result);
    }

    // =============================================================================
    // Combined Attributes
    // =============================================================================

    @Test
    void testCombinedAttributes() {
        String result = markup.processString(
                "<button ka:get=\"/data\" ka:target=\"#results\" ka:swap=\"innerHTML\">Load</button>", Map.of());
        assertTrue(result.contains("hx-get=\"/data\""));
        assertTrue(result.contains("hx-target=\"#results\""));
        assertTrue(result.contains("hx-swap=\"innerHTML\""));
    }

    @Test
    void testComplexForm() {
        String html = """
                <form ka:post="/api/submit" ka:target="#result" ka:swap="outerHTML" ka:indicator="#loading">
                    <input name="name" ka:trigger="keyup changed delay:300ms"/>
                    <button ka:disabled-elt="this" ka:confirm="Submit form?">Submit</button>
                </form>""";
        String result = markup.processString(html, Map.of());
        assertTrue(result.contains("hx-post=\"/api/submit\""));
        assertTrue(result.contains("hx-target=\"#result\""));
        assertTrue(result.contains("hx-swap=\"outerHTML\""));
        assertTrue(result.contains("hx-indicator=\"#loading\""));
        assertTrue(result.contains("hx-trigger=\"keyup changed delay:300ms\""));
        assertTrue(result.contains("hx-disabled-elt=\"this\""));
        assertTrue(result.contains("hx-confirm=\"Submit form?\""));
    }

}
