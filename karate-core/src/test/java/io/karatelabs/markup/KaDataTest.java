package io.karatelabs.markup;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KaDataProcessor (ka:data attribute).
 * Provides Alpine.js x-data binding from server-side data.
 */
class KaDataTest {

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
    // Form Elements (adds hidden input for submission)
    // =============================================================================

    @Test
    void testFormWithSimpleObject() {
        String html = """
                <form ka:data="form:data">
                    <input x-model="form.name"/>
                </form>""";
        Map<String, Object> vars = Map.of("data", Map.of("name", "John", "email", "john@test.com"));
        String result = markup.processString(html, vars);

        // x-data uses single-quoted attribute with raw JSON (same pattern as ka:vals / hx-vals)
        assertTrue(result.contains("x-data='{ form:"), "x-data not found in: " + result);
        assertTrue(result.contains("\"name\":\"John\""), "name not found in: " + result);
        // hidden input for form submission (uses default double quotes)
        assertTrue(result.contains("type=\"hidden\""));
        assertTrue(result.contains("name=\"form\""));
        assertTrue(result.contains("x-bind:value=\"JSON.stringify(form)\""));
    }

    @Test
    void testFormWithEmptyObject() {
        String html = "<form ka:data=\"form:empty\"><input x-model=\"form.name\"/></form>";
        String result = markup.processString(html, Map.of("empty", Map.of()));

        assertTrue(result.contains("x-data='{ form: {} }'"));
        assertTrue(result.contains("type=\"hidden\""));
    }

    @Test
    void testFormWithArray() {
        String html = "<form ka:data=\"items:list\"><div x-for=\"item in items\"></div></form>";
        String result = markup.processString(html, Map.of("list", List.of("a", "b", "c")));

        assertTrue(result.contains("x-data='{ items:"));
        assertTrue(result.contains("[\"a\",\"b\",\"c\"]"));
        assertTrue(result.contains("x-bind:value=\"JSON.stringify(items)\""));
    }

    @Test
    void testFormWithNestedObject() {
        String html = "<form ka:data=\"form:nested\"><input x-model=\"form.user.name\"/></form>";
        Map<String, Object> vars = Map.of("nested",
                Map.of("user", Map.of("name", "Alice", "age", 30), "active", true));
        String result = markup.processString(html, vars);

        assertTrue(result.contains("x-data='{ form:"));
        assertTrue(result.contains("\"user\":{"));
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"active\":true"));
    }

    @Test
    void testFormPreservesOtherAttributes() {
        String html = "<form ka:data=\"form:data\" class=\"my-form\" id=\"test-form\"><input/></form>";
        String result = markup.processString(html, Map.of("data", Map.of()));

        // all attributes on the ka:data element use single quotes
        assertTrue(result.contains("class='my-form'"));
        assertTrue(result.contains("id='test-form'"));
        assertTrue(result.contains("x-data='{ form: {} }'"));
    }

    @Test
    void testFormWithNullData() {
        String html = "<form ka:data=\"form:nothing\"><input x-model=\"form.name\"/></form>";
        Map<String, Object> vars = new HashMap<>();
        vars.put("nothing", null);
        String result = markup.processString(html, vars);

        assertTrue(result.contains("x-data='{ form: {} }'"));
    }

    @Test
    void testFormCombinedWithHtmxAttributes() {
        String html = """
                <form ka:data="form:data" ka:post="/api/submit" ka:target="#result">
                    <input x-model="form.email"/>
                    <button type="submit">Save</button>
                </form>""";
        String result = markup.processString(html, Map.of("data", Map.of("email", "")));

        assertTrue(result.contains("x-data='{ form:"), "x-data not found in: " + result);
        assertTrue(result.contains("type=\"hidden\""));
        // hx-post/hx-target are set by their own processors (may use their own quoting)
        assertTrue(result.contains("hx-post="), "hx-post not found in: " + result);
        assertTrue(result.contains("hx-target="), "hx-target not found in: " + result);
    }

    @Test
    void testFormWithComplexData() {
        String html = "<form ka:data=\"form:formData\"><input x-model=\"form.name\"/></form>";
        Map<String, Object> formData = new HashMap<>();
        formData.put("name", "Test User");
        formData.put("email", "test@example.com");
        formData.put("products", List.of("product1", "product2"));
        formData.put("notify", true);
        String result = markup.processString(html, Map.of("formData", formData));

        // raw JSON inside single-quoted x-data attribute
        assertTrue(result.contains("\"name\":\"Test User\""));
        assertTrue(result.contains("\"email\":\"test@example.com\""));
        assertTrue(result.contains("\"products\":[\"product1\",\"product2\"]"));
        assertTrue(result.contains("\"notify\":true"));
    }

    @Test
    void testRealisticManageTeamExample() {
        String html = """
                <form ka:data="form:initialForm">
                    <input x-model="form.email"/>
                    <input type="checkbox" x-model="form.products"/>
                    <input type="checkbox" x-model.boolean="form.notify"/>
                    <button hx-post="manage-team" hx-target="#main-content" ka:vals="action:'addUser'">
                        Add User
                    </button>
                </form>""";
        Map<String, Object> initialForm = new HashMap<>();
        initialForm.put("email", "");
        initialForm.put("role", "user");
        initialForm.put("products", List.of());
        initialForm.put("notify", true);
        String result = markup.processString(html, Map.of("initialForm", initialForm));

        assertTrue(result.contains("x-data='{ form:"), "x-data not found in: " + result);
        assertTrue(result.contains("type=\"hidden\""));
        assertTrue(result.contains("name=\"form\""));
        assertTrue(result.contains("hx-post="), "hx-post not found in: " + result);
        assertTrue(result.contains("hx-vals="), "hx-vals not found in: " + result);
    }

    // =============================================================================
    // Non-Form Elements (read-only x-data, no hidden input)
    // =============================================================================

    @Test
    void testDivAddsXDataOnly() {
        String html = "<div ka:data=\"data:serverData\"><span x-text=\"data.name\"></span></div>";
        Map<String, Object> vars = Map.of("serverData", Map.of("name", "Alice", "count", 42));
        String result = markup.processString(html, vars);

        assertTrue(result.contains("x-data='{ data:"));
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"count\":42"));
        assertFalse(result.contains("type=\"hidden\""));
        assertFalse(result.contains("x-bind:value"));
    }

    @Test
    void testSectionWithArray() {
        String html = "<section ka:data=\"items:list\"><template x-for=\"item in items\"></template></section>";
        String result = markup.processString(html, Map.of("list", List.of("one", "two", "three")));

        assertTrue(result.contains("x-data='{ items:"));
        assertTrue(result.contains("[\"one\",\"two\",\"three\"]"));
        assertFalse(result.contains("type=\"hidden\""));
    }

    @Test
    void testDivPreservesAttributes() {
        String html = "<div ka:data=\"config:settings\" class=\"my-class\" id=\"my-id\"><span x-text=\"config.theme\"></span></div>";
        Map<String, Object> vars = Map.of("settings", Map.of("theme", "dark", "lang", "en"));
        String result = markup.processString(html, vars);

        assertTrue(result.contains("class='my-class'"));
        assertTrue(result.contains("id='my-id'"));
        assertTrue(result.contains("x-data='{ config:"));
        assertTrue(result.contains("\"theme\":\"dark\""));
    }

    @Test
    void testSpanWithNestedData() {
        String html = "<span ka:data=\"user:profile\" x-text=\"user.name\"></span>";
        Map<String, Object> vars = Map.of("profile", Map.of(
                "name", "Bob",
                "address", Map.of("city", "NYC", "zip", "10001")));
        String result = markup.processString(html, vars);

        assertTrue(result.contains("x-data='{ user:"));
        assertTrue(result.contains("\"name\":\"Bob\""));
        assertTrue(result.contains("\"address\":{"));
        assertTrue(result.contains("\"city\":\"NYC\""));
    }

    // =============================================================================
    // Integration with ka:scope="local"
    // =============================================================================

    @Test
    void testWithKaScopeLocal() {
        String html = """
                <div>
                    <script ka:scope="local">_.formData = {name: 'John', email: 'john@test.com'}</script>
                    <form ka:data="form:formData">
                        <input x-model="form.name"/>
                    </form>
                </div>""";
        String result = markup.processString(html, Map.of());

        assertTrue(result.contains("x-data='{ form:"), "x-data not found in: " + result);
        assertTrue(result.contains("\"name\":\"John\""), "name not found in: " + result);
        assertTrue(result.contains("\"email\":\"john@test.com\""), "email not found in: " + result);
    }

    @Test
    void testWithKaScopeLocalArray() {
        String html = """
                <div>
                    <script ka:scope="local">_.items = ['apple', 'banana', 'cherry']</script>
                    <section ka:data="list:items">
                        <template x-for="item in list"><span x-text="item"></span></template>
                    </section>
                </div>""";
        String result = markup.processString(html, Map.of());

        assertTrue(result.contains("x-data='{ list:"), "x-data not found in: " + result);
        assertTrue(result.contains("[\"apple\",\"banana\",\"cherry\"]"), "array not found in: " + result);
    }

}
