package io.karatelabs.markup;

import io.karatelabs.common.Json;
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

    @Test
    void testEncoding() {
        String result = markup.processString("<form ka:encoding=\"multipart/form-data\">Form</form>", Map.of());
        assertEquals("<form hx-encoding=\"multipart/form-data\">Form</form>", result);
    }

    @Test
    void testHistory() {
        String result = markup.processString("<div ka:history=\"false\">Content</div>", Map.of());
        assertEquals("<div hx-history=\"false\">Content</div>", result);
    }

    @Test
    void testHistoryElt() {
        String result = markup.processString("<div ka:history-elt=\"true\">Content</div>", Map.of());
        assertEquals("<div hx-history-elt=\"true\">Content</div>", result);
    }

    @Test
    void testRequest() {
        String result = markup.processString("<div ka:request=\"timeout:5000\">Content</div>", Map.of());
        assertEquals("<div hx-request=\"timeout:5000\">Content</div>", result);
    }

    // =============================================================================
    // ka:vals Edge Cases
    // =============================================================================

    @Test
    void testValsWithActionAndId() {
        // Common studio pattern: action + entity ID
        String result = markup.processString(
                "<button ka:vals=\"action:'delete',teamId:team.teamId\">Delete</button>",
                Map.of("team", Map.of("teamId", "abc-123")));
        assertTrue(result.contains("\"action\":\"delete\""));
        assertTrue(result.contains("\"teamId\":\"abc-123\""));
    }

    @Test
    void testValsWithNestedObjectAccess() {
        String result = markup.processString(
                "<button ka:vals=\"name:user.profile.name\">Click</button>",
                Map.of("user", Map.of("profile", Map.of("name", "Alice"))));
        assertTrue(result.contains("\"name\":\"Alice\""));
    }

    @Test
    void testValsWithBooleanAndNumber() {
        String result = markup.processString(
                "<button ka:vals=\"active:true,count:42,label:'test'\">Click</button>", Map.of());
        assertTrue(result.contains("\"active\":true"));
        assertTrue(result.contains("\"count\":42"));
        assertTrue(result.contains("\"label\":\"test\""));
    }

    @Test
    void testValsEscapesAttributeValue() {
        String value = "O'Brien & <script>";
        String result = markup.processString(
                "<button ka:vals=\"name:user.name\">Click</button>", Map.of("user", Map.of("name", value)));
        assertEquals("<button hx-vals='{\"name\":\"O&#39;Brien &amp; &lt;script>\"}'>Click</button>", result);
        String hxVals = KaDataTest.decode(KaDataTest.attr(result, "hx-vals"));
        assertEquals(value, Json.of(hxVals).asMap().get("name"));
    }

    @Test
    void testDispatchEscapesAttributeValue() {
        String result = markup.processString(
                "<button ka:dispatch=\"pick\" ka:vals=\"name:user.name\">Click</button>",
                Map.of("user", Map.of("name", "O'Brien")));
        String onclick = KaDataTest.decode(KaDataTest.attr(result, "onclick"));
        assertTrue(onclick.contains("detail: {\"name\":\"O'Brien\"}"), result);
    }

    @Test
    void testDispatchOnTriggerEscapesAttributeValue() {
        String result = markup.processString(
                "<select ka:dispatch=\"pick @ change\" ka:vals=\"name:user.name\"></select>",
                Map.of("user", Map.of("name", "O'Brien & <b>")));
        String handler = KaDataTest.decode(KaDataTest.attr(result, "hx-on:change"));
        assertTrue(handler.contains("detail: {\"name\":\"O'Brien & <b>\"}"), result);
    }

    @Test
    void testGenericIntoExistingSingleQuotedTarget() {
        String result = markup.processString(
                "<button hx-confirm='old' ka:confirm=\"${msg}\">X</button>", Map.of("msg", "O'Brien & co"));
        assertEquals("O'Brien & co", KaDataTest.decode(KaDataTest.attr(result, "hx-confirm")));
    }

    @Test
    void testMethodIntoExistingSingleQuotedTarget() {
        String result = markup.processString(
                "<button hx-get='old' ka:get=\"${url}\">X</button>", Map.of("url", "/find?q=O'Brien&x=1"));
        assertEquals("/find?q=O'Brien&x=1", KaDataTest.decode(KaDataTest.attr(result, "hx-get")));
    }

    @Test
    void testGenericWithColocatedKaData() {
        String result = markup.processString(
                "<form ka:data=\"form:data\" ka:confirm=\"${msg}\" ka:post=\"${url}\"><input/></form>",
                Map.of("data", Map.of(), "msg", "O'Brien", "url", "/save?who=O'Brien"));
        assertEquals("O'Brien", KaDataTest.decode(attrAnyQuote(result, "hx-confirm")));
        assertEquals("/save?who=O'Brien", KaDataTest.decode(attrAnyQuote(result, "hx-post")));
    }

    @Test
    void testGenericWithColocatedKaDataExistingTarget() {
        String result = markup.processString(
                "<form ka:data=\"form:data\" hx-confirm=\"old\" ka:confirm=\"${msg}\"><input/></form>",
                Map.of("data", Map.of(), "msg", "O'Brien"));
        assertEquals("O'Brien", KaDataTest.decode(attrAnyQuote(result, "hx-confirm")));
    }

    @Test
    void testGenericExpressionWithQuotesAndEntityText() {
        String msg = "say \"hi\" &amp; <b>";
        String result = markup.processString("<button ka:confirm=\"${msg}\">X</button>", Map.of("msg", msg));
        assertEquals(msg, KaDataTest.decode(attrAnyQuote(result, "hx-confirm")));
    }

    @Test
    void testGenericLiteralEntityInSource() {
        String result = markup.processString(
                "<button ka:confirm=\"Tom &amp; Jerry's\">X</button>", Map.of());
        assertEquals("Tom & Jerry's", KaDataTest.decode(attrAnyQuote(result, "hx-confirm")));
    }

    @Test
    void testGenericPlainValueUnchanged() {
        String result = markup.processString("<button ka:confirm=\"Sure?\" ka:get=\"/items\">X</button>", Map.of());
        assertEquals("<button hx-confirm=\"Sure?\" hx-get=\"/items\">X</button>", result);
    }

    static String attrAnyQuote(String html, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(" " + java.util.regex.Pattern.quote(name) + "=(?:'([^']*)'|\"([^\"]*)\")").matcher(html);
        assertTrue(m.find(), name + " not found in: " + html);
        return m.group(1) != null ? m.group(1) : m.group(2);
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
