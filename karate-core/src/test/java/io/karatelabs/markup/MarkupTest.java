package io.karatelabs.markup;

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;
import io.karatelabs.js.ExternalBridge;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class MarkupTest {

    static final Logger logger = LoggerFactory.getLogger(MarkupTest.class);

    private static String render(String filename) {
        Engine js = new Engine();
        RootResourceResolver resolver = new RootResourceResolver("classpath:markup");
        Markup markup = Markup.init(js, resolver);
        return markup.processPath(filename, null);
    }

    @Test
    void testHtmlString() {
        Engine js = new Engine();
        js.put("message", "hello world");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div><div th:text=\"message\"></div><div th:replace=\"/temp.html\"></div></div>";
        String rendered = markup.processString(html, null);
        assertEquals("<div><div>hello world</div><div>temp</div></div>", rendered);
    }

    @Test
    void testHtmlFile() {
        String rendered = render("main.html");
        assertFalse(rendered.contains("<script foo=\"bar\" ka:scope=\"local\"></script>"));
        assertTrue(rendered.contains("<div id=\"local_js\">local.js called</div>"));
        assertTrue(rendered.contains("<div id=\"global_js\">global.js called</div>"));
        assertTrue(rendered.contains("<div id=\"before_one\"><span>js_one</span></div>"));
        assertTrue(rendered.contains("<div id=\"called_one\">called_one</div>"));
        assertTrue(rendered.contains("<div id=\"after_one\"><span>js_one</span></div>"));
    }

    @Test
    void testKaSet() {
        String rendered = render("ka-set.html");
        assertEquals("<div>"
                + "first line\n"
                + "second line"
                + "</div>", rendered.replaceAll("\\r", "").trim());
    }

    @Test
    void testWith() {
        String rendered = render("with");
        assertTrue(rendered.contains("<div>bar</div>"));
        assertTrue(rendered.contains("<div>hello world</div>"));
        assertTrue(rendered.contains("<div>with</div>"));
    }

    @Test
    void testAttr() {
        String rendered = render("attr.html");
        assertTrue(rendered.contains("<div foo=\"a\">normal</div>"));
        assertTrue(rendered.contains("<div foo=\"xa\">append</div>"));
        assertTrue(rendered.contains("<div foo=\"ax\">prepend</div>"));
    }

    @Test
    void testNoCache() {
        Resource resource = Resource.path("classpath:markup/temp.js");
        String rendered = render("nocache.html");
        assertTrue(rendered.contains("<script src=\"temp.js?ts=" + resource.getLastModified() + "\"></script>"));
    }

    @Test
    void testNoCacheLinkHref() {
        // ka:nocache on <link href> must work the same as on <script src> — both
        // get ?ts=<lastModified> appended and the ka:nocache attribute scrubbed.
        Resource resource = Resource.path("classpath:markup/temp.css");
        String rendered = render("nocache.html");
        assertTrue(rendered.contains("href=\"temp.css?ts=" + resource.getLastModified() + "\""),
                "link href should carry ?ts=<lastModified>: " + rendered);
        assertFalse(rendered.contains("ka:nocache"),
                "ka:nocache attribute must be removed from rendered output: " + rendered);
    }

    @Test
    void testNoCacheInlineLink() {
        // Same shape as testNoCacheLinkHref but via processString to lock down
        // the contract for callers that don't load template files.
        Resource resource = Resource.path("classpath:markup/temp.css");
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String rendered = markup.processString(
                "<link href=\"temp.css\" rel=\"stylesheet\" ka:nocache=\"true\"/>", null);
        assertTrue(rendered.contains("href=\"temp.css?ts=" + resource.getLastModified() + "\""),
                "rendered: " + rendered);
        assertFalse(rendered.contains("ka:nocache"), "rendered: " + rendered);
    }

    static String MY_COLON = "my:";

    @Test
    void testCustomResolverAndThis() {
        Engine js = new Engine();
        RootResourceResolver resolver = new RootResourceResolver("classpath:markup") {
            @Override
            public Resource resolve(String path, Resource caller) {
                if (path.startsWith(MY_COLON)) {
                    path = "custom/" + path.substring(MY_COLON.length());
                    return super.resolve(path, caller);
                }
                return super.resolve(path, caller);
            }
        };
        Markup markup = Markup.init(js, resolver);
        String rendered = markup.processPath("custom", null);
        assertEquals("<div><div>caller</div>\n<div><div>called</div></div></div>", rendered.replaceAll("\\r", ""));
    }

    // ========== MarkupContext Tests ==========

    @Test
    void testContextRead() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        // context.read() in th:text expression
        String html = "<div th:text=\"context.read('test-data.json')\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("karate"));
        assertTrue(rendered.contains("version"));
    }

    @Test
    void testContextMethods() {
        // Test context.read, context.toJson, context.template using file-based template
        String rendered = render("context-test.html");
        // context.template returns current template name
        assertTrue(rendered.contains("<div id=\"template\">context-test.html</div>"), "template not found in: " + rendered);
        // context.read returns file content (quotes are HTML-encoded in output)
        assertTrue(rendered.contains("karate") && rendered.contains("version"), "read content not found in: " + rendered);
        // context.toJson returns JSON string (quotes HTML-encoded as &quot;)
        assertTrue(rendered.contains("msg") && rendered.contains("hello"), "json not found in: " + rendered);
    }

    @Test
    void testTemplateErrorLogging() {
        // Test that template errors produce clear error messages with line info
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // Template with intentional error - undefined variable
        String template = """
            <div>
                <script ka:scope="global">
                    _.value = undefinedVar.foo
                </script>
                <span th:text="value">test</span>
            </div>
            """;

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            markup.processString(template, null);
        });

        // Verify the root cause contains useful error info
        Throwable rootCause = ex;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        assertTrue(message.contains("undefinedVar"), "Error should mention the undefined variable: " + message);
    }

    // ========== Java Interop Tests ==========

    @Test
    void testJavaInterop() {
        // Test Java.type() in templates
        Engine js = new Engine();
        js.setExternalBridge(new ExternalBridge() {});
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // Test using Java.type() to create a UUID
        String html = """
            <div>
                <script ka:scope="global">
                    var UUID = Java.type('java.util.UUID');
                    _.uuid = UUID.randomUUID().toString();
                    _.uuidLen = _.uuid.length;
                </script>
                <span th:text="uuidLen">0</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        // UUID string is 36 characters (8-4-4-4-12 format)
        assertTrue(rendered.contains("<span>36</span>"), "UUID length should be 36: " + rendered);
    }

    @Test
    void testJavaInteropDateFormatting() {
        // Test Java interop for date formatting as documented
        Engine js = new Engine();
        js.setExternalBridge(new ExternalBridge() {});
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <div>
                <script ka:scope="global">
                    var SimpleDateFormat = Java.type('java.text.SimpleDateFormat');
                    var Date = Java.type('java.util.Date');
                    var formatter = new SimpleDateFormat('yyyy-MM-dd');
                    // Use a fixed date for testing: Jan 15, 2024
                    var date = new Date(1705276800000);
                    _.formatted = formatter.format(date);
                </script>
                <span th:text="formatted">date</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("2024-01-15"), "Formatted date should be 2024-01-15: " + rendered);
    }

    @Test
    void testJavaInteropDirectClassPath() {
        // Test direct class path access (without Java.type)
        Engine js = new Engine();
        js.setExternalBridge(new ExternalBridge() {});
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <div>
                <script ka:scope="global">
                    // Math.max returns double, cast to int for clean output
                    _.result = parseInt(java.lang.Math.max(10, 20));
                </script>
                <span th:text="result">0</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>20</span>"), "Math.max should return 20: " + rendered);
    }

    @Test
    void testIterationStatusWithJavaInterop() {
        // Test that Thymeleaf's IterationStatus works with Java interop
        // The iter variable should be accessible directly without conversion
        Engine js = new Engine();
        js.setExternalBridge(new ExternalBridge() {});
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <ul>
                <li th:each="item, iter: ['a', 'b', 'c']">
                    <span th:text="iter.index">0</span>-<span th:text="item">x</span>
                </li>
            </ul>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>0</span>-<span>a</span>"), "First item index should be 0: " + rendered);
        assertTrue(rendered.contains("<span>1</span>-<span>b</span>"), "Second item index should be 1: " + rendered);
        assertTrue(rendered.contains("<span>2</span>-<span>c</span>"), "Third item index should be 2: " + rendered);
    }

    // ========== XSS Prevention Tests ==========
    // These tests verify that th:text properly escapes HTML to prevent XSS attacks.
    // This is Thymeleaf's built-in behavior - these tests document and verify it.

    @Test
    void testThTextEscapesScriptTags() {
        Engine js = new Engine();
        js.put("userInput", "<script>alert('xss')</script>");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:text=\"userInput\">placeholder</div>";
        String rendered = markup.processString(html, null);

        // Script tags should be escaped, not executable
        assertFalse(rendered.contains("<script>"), "Script tag should be escaped");
        assertTrue(rendered.contains("&lt;script&gt;"), "Should contain escaped script tag");
        assertTrue(rendered.contains("&lt;/script&gt;"), "Should contain escaped closing tag");
    }

    @Test
    void testThTextEscapesHtmlEntities() {
        Engine js = new Engine();
        js.put("userInput", "<b>bold</b> & \"quoted\" 'apostrophe'");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:text=\"userInput\">placeholder</div>";
        String rendered = markup.processString(html, null);

        // All special HTML characters should be escaped
        assertFalse(rendered.contains("<b>"), "HTML tags should be escaped");
        assertTrue(rendered.contains("&lt;b&gt;"), "Should contain escaped <b>");
        assertTrue(rendered.contains("&amp;"), "Ampersand should be escaped");
        assertTrue(rendered.contains("&quot;") || rendered.contains("&#34;"), "Quotes should be escaped");
    }

    @Test
    void testThTextEscapesEventHandlers() {
        Engine js = new Engine();
        js.put("userInput", "<img src=x onerror=alert('xss')>");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:text=\"userInput\">placeholder</div>";
        String rendered = markup.processString(html, null);

        // Event handler injection should be escaped - the < becomes &lt; so it's not an HTML tag
        assertFalse(rendered.contains("<img"), "IMG tag should be escaped");
        assertTrue(rendered.contains("&lt;img"), "Should contain escaped img tag");
        // The onerror= text is present but not executable because it's inside escaped text
        assertTrue(rendered.contains("onerror="), "Text contains onerror but it's escaped");
    }

    @Test
    void testThTextEscapesJavascriptUrl() {
        Engine js = new Engine();
        js.put("userInput", "<a href=\"javascript:alert('xss')\">click</a>");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:text=\"userInput\">placeholder</div>";
        String rendered = markup.processString(html, null);

        // Javascript URL should be escaped as text
        assertFalse(rendered.contains("<a href"), "Anchor tag should be escaped");
        assertTrue(rendered.contains("&lt;a"), "Should contain escaped anchor");
    }

    @Test
    void testThUtextDoesNotEscape() {
        // Document that th:utext does NOT escape - should only be used with trusted content
        Engine js = new Engine();
        js.put("trustedHtml", "<b>bold</b>");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:utext=\"trustedHtml\">placeholder</div>";
        String rendered = markup.processString(html, null);

        // th:utext renders HTML as-is (use only with trusted content!)
        assertTrue(rendered.contains("<b>bold</b>"), "th:utext should render HTML unescaped");
    }

    @Test
    void testThAttrEscapesAttributeValues() {
        // Test escaping in th:title (standard attribute) context
        Engine js = new Engine();
        js.put("userTitle", "Title with \"quotes\" and <tags>");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:title=\"userTitle\">content</div>";
        String rendered = markup.processString(html, null);

        // Attribute values should be properly escaped
        assertTrue(rendered.contains("title="), "Should have title attribute");
        // Quotes and angle brackets should be escaped in attribute value
        assertFalse(rendered.contains("<tags>"), "Tags in attr should be escaped");
    }

    // ========== Map Iteration Tests ==========

    @Test
    void testMapIterationWithKeyValue() {
        // Test that Maps can be iterated with entry.key and entry.value like Thymeleaf
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <script ka:scope="global">
                _.colors = { red: '#FF0000', green: '#00FF00', blue: '#0000FF' };
            </script>
            <ul>
                <li th:each="entry : colors">
                    <span th:text="entry.key">name</span>: <span th:text="entry.value">value</span>
                </li>
            </ul>
            """;
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("<span>red</span>: <span>#FF0000</span>"), "Should have red entry: " + rendered);
        assertTrue(rendered.contains("<span>green</span>: <span>#00FF00</span>"), "Should have green entry: " + rendered);
        assertTrue(rendered.contains("<span>blue</span>: <span>#0000FF</span>"), "Should have blue entry: " + rendered);
    }

    @Test
    void testMapIterationWithNestedValues() {
        // Test Map iteration where values are arrays/lists
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <script ka:scope="global">
                _.tags = {
                    '@smoke': ['test1', 'test2'],
                    '@api': ['test3']
                };
            </script>
            <div th:each="entry : tags">
                <h3 th:text="entry.key">tag</h3>
                <span th:text="entry.value.length">0</span>
            </div>
            """;
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("<h3>@smoke</h3>"), "Should have @smoke tag: " + rendered);
        assertTrue(rendered.contains("<h3>@api</h3>"), "Should have @api tag: " + rendered);
    }

    @Test
    void testThAttrWithHyphenatedAttributeNames() {
        // Test that hyphenated attribute names work when quoted
        Engine js = new Engine();
        js.put("itemId", "42");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // Hyphenated attributes must be quoted to avoid parsing as subtraction
        String html = "<button th:attr=\"'data-bs-target':'#item-' + itemId, 'data-id':itemId\">Click</button>";
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("data-bs-target=\"#item-42\""), "Should have data-bs-target: " + rendered);
        assertTrue(rendered.contains("data-id=\"42\""), "Should have data-id: " + rendered);
    }

    @Test
    void testThAttrUnquotedHyphenatedKeyHintsAtQuoting() {
        // When a th:attr (or ka:with / hx-vals / ka:dispatch) value contains
        // an unquoted attribute key with hyphens or colons, the JS
        // object-literal parser fails with a generic SyntaxError. We
        // augment that with a hint that names the offending keys and shows
        // the corrected (quoted) form so the fix is one read away.
        Engine js = new Engine();
        js.put("itemId", "42");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // Single hyphenated key.
        String htmlSingle = "<button th:attr=\"data-id: itemId\">Click</button>";
        RuntimeException thrownSingle = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlSingle, null),
                "unquoted hyphenated attr key must throw");
        assertHintContains(thrownSingle,
                "data-id", "must be quoted", "'data-id'");

        // Multiple hyphenated and colon-bearing keys at once.
        String htmlMulti = "<a th:attr=\"data-item-id: itemId, hx-target: 'body', ka:get: '/x'\"></a>";
        RuntimeException thrownMulti = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlMulti, null),
                "multiple unquoted hyphenated/colon keys must throw");
        assertHintContains(thrownMulti,
                "data-item-id", "hx-target", "ka:get", "must be quoted",
                "'data-item-id'", "'hx-target'", "'ka:get'");
    }

    private static void assertHintContains(Throwable thrown, String... needles) {
        Throwable t = thrown;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                boolean all = true;
                for (String n : needles) {
                    if (!msg.contains(n)) {
                        all = false;
                        break;
                    }
                }
                if (all) return;
            }
            t = t.getCause();
        }
        throw new AssertionError("no exception in cause chain contained all of "
                + java.util.Arrays.toString(needles)
                + "; top message was: " + thrown.getMessage());
    }

    // ========== th:each (KaEachProcessor replaces StandardEachTagProcessor) ==========

    @Test
    void testThEachWithoutColonRejected() {
        // Karate-markup's th:each (handled by KaEachProcessor) requires the
        // explicit `name : expression` form — the bare-form shorthand that
        // implicitly named the iteration variable `_` was a karate-specific
        // invention that clashed with the underscore-namespace discipline.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <script ka:scope="global">
                _.items = ['a', 'b'];
            </script>
            <ul><li th:each="_.items">x</li></ul>
            """;
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null),
                "bare th:each form must throw");
        assertHintContains(thrown,
                "th:each requires", "name : expression");
    }

    // ========== Iteration Variable Tests ==========

    @Test
    void testImplicitIterationStatus() {
        // Thymeleaf default: th:each="item: list" creates implicit status var "itemStat"
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<li th:each=\"item: ['a', 'b']\" th:text=\"itemStat.index\">0</li>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<li>0</li>"), "Implicit itemStat.index should work: " + rendered);
        assertTrue(rendered.contains("<li>1</li>"), "Implicit itemStat.index for second: " + rendered);
    }

    @Test
    void testUnderscoreReadOfIterationStatusReturnsJsFriendlyMap() {
        // `_.iter.first` (and similar) must see the same JS-friendly shape
        // that bare `iter.first` already gets — the underscoreView's
        // fall-through read routes through the outer getVariable so
        // Thymeleaf's IterationStatusVar gets converted to a {first, last,
        // index, ...} Map. Without the conversion, `_.iter.first` would
        // surface the raw IterationStatusVar object and fail at the JS
        // layer (no `.first` accessor on the Java object).
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <ul>
                <li th:each="item, iter: ['a', 'b', 'c']" th:text="_.iter.first ? 'is-first' : _.iter.last ? 'is-last' : 'mid'">0</li>
            </ul>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<li>is-first</li>"), "iter.first via _ access: " + rendered);
        assertTrue(rendered.contains("<li>mid</li>"), "iter middle via _ access: " + rendered);
        assertTrue(rendered.contains("<li>is-last</li>"), "iter.last via _ access: " + rendered);
    }

    @Test
    void testIterationCount() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <ul>
                <li th:each="item, iter: ['a', 'b', 'c']" th:text="iter.count">0</li>
            </ul>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<li>1</li>"), "First count should be 1: " + rendered);
        assertTrue(rendered.contains("<li>2</li>"), "Second count should be 2: " + rendered);
        assertTrue(rendered.contains("<li>3</li>"), "Third count should be 3: " + rendered);
    }

    @Test
    void testIterationFirstLast() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:each="item, iter: ['a', 'b', 'c']">
                <span th:if="iter.first" th:text="'first:' + item">first</span>
                <span th:if="iter.last" th:text="'last:' + item">last</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("first:a"), "First item marked: " + rendered);
        assertTrue(rendered.contains("last:c"), "Last item marked: " + rendered);
        assertFalse(rendered.contains("first:b"), "Middle should not be first: " + rendered);
        assertFalse(rendered.contains("last:b"), "Middle should not be last: " + rendered);
    }

    @Test
    void testIterationEvenOdd() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <tr th:each="item, iter: ['a', 'b', 'c', 'd']"
                th:class="iter.even ? 'even' : 'odd'">
                <td th:text="item">x</td>
            </tr>
            """;
        String rendered = markup.processString(html, null);
        // index 0 = even, 1 = odd, 2 = even, 3 = odd
        assertTrue(rendered.contains("class=\"even\""), "Should have even rows: " + rendered);
        assertTrue(rendered.contains("class=\"odd\""), "Should have odd rows: " + rendered);
    }

    // ========== ka:scope="local" Inside th:each ==========

    @Test
    void testLocalScopeInsideEach() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.items = [{ name: 'Apple', price: 1.50 }, { name: 'Banana', price: 0.75 }];
                _.taxRate = 0.1;
            </script>
            <div th:each="item: items">
                <script ka:scope="local">
                    _.total = item.price * (1 + taxRate);
                    _.totalFormatted = '$' + _.total.toFixed(2);
                </script>
                <span th:text="item.name">Name</span>: <span th:text="totalFormatted">$0.00</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>Apple</span>: <span>$1.65</span>"), "Apple total: " + rendered);
        assertTrue(rendered.contains("<span>Banana</span>: <span>$0.82</span>") ||
                   rendered.contains("<span>Banana</span>: <span>$0.83</span>"), "Banana total: " + rendered);
    }

    // ========== context.get(name, default?) + dual-lookup `_` ==========

    @Test
    void testContextGetReturnsThWithBoundValue() {
        // context.get(name) walks the underscore map first then the
        // wrapped Thymeleaf scope, so caller-passed th:with values resolve
        // through the same accessor used for fragment-optional params.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="spacing: 'mt-3'">
                <span th:text="context.get('spacing')"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>mt-3</span>"), "rendered: " + rendered);
    }

    @Test
    void testContextGetReturnsDefaultWhenMissing() {
        // No binding for the name → second-arg default is returned.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div>
                <span th:text="context.get('spacing', 'mb-4')"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>mb-4</span>"), "rendered: " + rendered);
    }

    @Test
    void testContextGetReturnsNullWhenMissingAndNoDefault() {
        // Single-arg form returns null on miss (no exception).
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div>
                <span th:text="context.get('spacing') == null ? 'is-null' : 'other'"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>is-null</span>"), "rendered: " + rendered);
    }

    @Test
    void testContextMethodsRejectNullPathArg() {
        // context.read(null) / readBytes(null) / fromJson(null) /
        // context.get(null) used to NPE on args[0].toString(). They now
        // throw the same actionable error as the missing-arg case.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String htmlGet = "<div th:text=\"context.get(null)\"></div>";
        RuntimeException thrownGet = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlGet, null));
        assertHintContains(thrownGet, "context.get() requires a name argument");

        String htmlRead = "<div th:text=\"context.read(null)\"></div>";
        RuntimeException thrownRead = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlRead, null));
        assertHintContains(thrownRead, "read() requires a path argument");

        String htmlFromJson = "<div th:text=\"context.fromJson(null)\"></div>";
        RuntimeException thrownFromJson = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlFromJson, null));
        assertHintContains(thrownFromJson, "fromJson() requires a JSON string argument");
    }

    @Test
    void testContextGetReadsUnderscoreMap() {
        // `_` writes are visible via context.get too.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div>
                <script ka:scope="local">
                    _.theme = 'dark';
                </script>
                <span th:text="context.get('theme', 'light')"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>dark</span>"), "rendered: " + rendered);
    }

    @Test
    void testUnderscoreReadFallsThroughToThWithScope() {
        // `_.spacing` (read) falls through to Thymeleaf scope when
        // the underscore map doesn't have the name. Caller's th:with-bound
        // value is reachable as `_.spacing` from inside a fragment, no
        // typeof guard needed.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="spacing: 'mt-3'">
                <span th:text="_.spacing"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>mt-3</span>"), "rendered: " + rendered);
    }

    @Test
    void testUnderscoreObjectKeysScopedToNamespace() {
        // Object.keys(_) is intentionally scoped to the underscore namespace
        // itself — only names written via `_.x = ...`. Read fall-through to
        // the wrapped Thymeleaf scope is a convenience for `_.foo` reads but
        // does not extend to enumeration. Asserted inside a script block so
        // both the writes and the keys check evaluate in the same scope.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="parentName: 'mt-3'">
                <script ka:scope="local">
                    _.localName = 'lx';
                    // Read fall-through: parentName comes from th:with
                    _.parentSeen = _.parentName;
                    // Enumeration: only underscore writes
                    _.namespaceKeys = Object.keys(_).sort().join(',');
                </script>
                <span th:text="parentSeen">_</span>
                <span th:text="namespaceKeys">_</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>mt-3</span>"),
                "_.parentName must read through to th:with binding: " + rendered);
        // Object.keys(_) sees writes assigned BEFORE the call (the sort()/join
        // result is itself the value being assigned to namespaceKeys, so
        // namespaceKeys is not yet present). Critically: parentName (a
        // wrapped-scope binding, reachable via read fall-through) is NOT
        // enumerated — Object.keys is scoped to the underscore namespace.
        assertTrue(rendered.contains("<span>localName,parentSeen</span>"),
                "Object.keys must show only underscore writes, not fall-through names: " + rendered);
    }

    @Test
    void testUnderscoreReadReturnsNullWhenTrulyMissing() {
        // Bare `_.missing` access on a fully unset name returns null
        // (no ReferenceError). Distinguishes the dual-lookup `_` from the
        // strict bare-name path.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div>
                <span th:text="_.missing == null ? 'is-null' : 'other'"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>is-null</span>"), "rendered: " + rendered);
    }

    @Test
    void testUnderscoreWritePreservesExplicitNull() {
        // Explicit `_.foo = null` is preserved on read (does not
        // fall through to wrapped scope). Distinguishes "set to null"
        // from "never set".
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="foo: 'parent-value'">
                <script ka:scope="local">
                    _.foo = null;
                </script>
                <span th:text="_.foo == null ? 'explicit-null' : _.foo"></span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>explicit-null</span>"),
                "explicit `_.foo = null` must win over a th:with-bound parent value: " + rendered);
    }

    // ========== th:with Parameter Passing ==========

    @Test
    void testWithMultipleParams() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="name: 'Alice', age: 30, role: 'admin'">
                <span th:text="name">n</span>
                <span th:text="age">0</span>
                <span th:text="role">r</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>Alice</span>"), "Name param: " + rendered);
        assertTrue(rendered.contains("<span>30</span>"), "Age param: " + rendered);
        assertTrue(rendered.contains("<span>admin</span>"), "Role param: " + rendered);
    }

    @Test
    void testWithExpressionParams() {
        Engine js = new Engine();
        js.put("session", java.util.Map.of("state", "ready", "id", "abc-123"));
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <div th:with="state: session.state, label: 'Session: ' + session.id">
                <span th:text="state">s</span>
                <span th:text="label">l</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>ready</span>"), "State from session: " + rendered);
        assertTrue(rendered.contains("<span>Session: abc-123</span>"), "Label computed: " + rendered);
    }

    @Test
    void testWithInsideEach() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.items = [{ name: 'A', status: 'ready' }, { name: 'B', status: 'error' }];
            </script>
            <div th:each="item: items">
                <span th:with="css: item.status == 'ready' ? 'bg-success' : 'bg-danger'"
                      th:class="css" th:text="item.name">x</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("class=\"bg-success\""), "Ready badge: " + rendered);
        assertTrue(rendered.contains("class=\"bg-danger\""), "Error badge: " + rendered);
    }

    @Test
    void testWithOnSameElementAsEach() {
        // th:with and th:each on the same element — th:with (precedence 50) runs before
        // th:each (200), so the iteration variable isn't defined on first pass. KaWithProcessor
        // must gracefully defer; th:each re-processes the element per iteration with the var set.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.data = [{ name: 'Alice', role: 'admin' }, { name: 'Bob', role: 'user' }];
            </script>
            <div th:each="item: data" th:with="isAdmin: item.role == 'admin'">
                <span th:if="isAdmin" th:text="item.name + ' (admin)'">x</span>
                <span th:unless="isAdmin" th:text="item.name">x</span>
            </div>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("Alice (admin)"), "Admin label: " + rendered);
        assertTrue(rendered.contains(">Bob<"), "Non-admin label: " + rendered);
        assertFalse(rendered.contains("Bob (admin)"), "Bob should not be admin: " + rendered);
    }

    // ========== Subdirectory Fragment Resolution ==========

    @Test
    void testSubdirectoryFragmentInsert() {
        // Test the components/badge pattern used in real apps
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <span th:insert="~{components/badge :: badge}" th:with="state: 'ready'">placeholder</span>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("bg-success"), "Badge should render with success class: " + rendered);
        assertTrue(rendered.contains("ready"), "Badge should show state text: " + rendered);
    }

    @Test
    void testSubdirectoryFragmentInsertWithParams() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<span th:insert=\"~{components/badge :: badge}\" th:with=\"state: 'error'\">placeholder</span>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("bg-danger"), "Badge should render with danger class: " + rendered);
        assertTrue(rendered.contains("error"), "Badge should show state text: " + rendered);
    }

    @Test
    void testSubdirectoryFragmentReplaceWithParams() {
        // th:replace removes the host element, so th:with variables are lost.
        // Use th:insert + th:with instead, or wrap in a parent element with th:with.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<span th:with=\"state: 'error'\"><span th:replace=\"~{components/badge :: badge}\">placeholder</span></span>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("bg-danger"), "Badge should render with danger class: " + rendered);
        assertTrue(rendered.contains("error"), "Badge should show state text: " + rendered);
    }

    @Test
    void testSubdirectoryFragmentInLoop() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.sessions = [{ name: 'S1', state: 'ready' }, { name: 'S2', state: 'error' }, { name: 'S3', state: 'running' }];
            </script>
            <table>
                <tr th:each="s: sessions">
                    <td th:text="s.name">name</td>
                    <td th:with="state: s.state"><span th:replace="~{components/badge :: badge}">badge</span></td>
                </tr>
            </table>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("bg-success"), "Ready badge: " + rendered);
        assertTrue(rendered.contains("bg-danger"), "Error badge: " + rendered);
        assertTrue(rendered.contains("bg-secondary"), "Running badge (default): " + rendered);
    }

    @Test
    void testSubdirectoryWholeFileInsert() {
        // Test including a whole file from a subdirectory (no :: fragment selector)
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:insert=\"components/footer\">placeholder</div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("Built with Karate"), "Footer content should be inserted: " + rendered);
    }

    // ========== Fragment Syntax Tests ==========

    @Test
    void testInsertWithSimpleSyntax() {
        // Test th:insert with simple syntax (auto-wrapped with ~{})
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:insert=\"temp.html\">placeholder</div>";
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("temp"), "Should include temp.html content: " + rendered);
    }

    @Test
    void testInsertWithFragmentSyntax() {
        // Test th:insert with full ~{} fragment syntax (should not double-wrap)
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:insert=\"~{fragment-test :: nav}\">placeholder</div>";
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("nav-content"), "Should include nav fragment: " + rendered);
    }

    @Test
    void testReplaceWithSimpleSyntax() {
        // Test th:replace with simple syntax (auto-wrapped with ~{})
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:replace=\"temp.html\">placeholder</div>";
        String rendered = markup.processString(html, null);

        assertEquals("<div>temp</div>", rendered.trim());
    }

    @Test
    void testReplaceWithFragmentSyntax() {
        // Test th:replace with full ~{} fragment syntax (should not double-wrap)
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:replace=\"~{fragment-test :: footer}\">placeholder</div>";
        String rendered = markup.processString(html, null);

        assertEquals("<div>footer-content</div>", rendered.trim());
    }

    // ========== Truthiness Tests ==========
    // Thymeleaf's EvaluationUtils.evaluateAsBoolean() handles th:if truthiness.
    // IMPORTANT: Thymeleaf truthiness differs from JavaScript:
    //   - Empty string "" is TRUTHY in Thymeleaf (only "false", "off", "no" are falsy)
    //   - Empty string "" is FALSY in JavaScript
    //   - null/undefined is falsy in both
    //   - 0 is falsy in both
    //   - false is falsy in both
    // Use explicit checks like th:if="value.length > 0" for empty string detection.

    @Test
    void testThIfEmptyStringIsTruthy() {
        // Thymeleaf treats "" as truthy (only "false", "off", "no" are falsy strings)
        // This differs from JavaScript where "" is falsy
        Engine js = new Engine();
        js.put("value", "");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"value\">shown</div><div th:unless=\"value\">hidden</div>";
        String rendered = markup.processString(html, null);

        // Empty string IS truthy in Thymeleaf
        assertTrue(rendered.contains("shown"), "Empty string is truthy in Thymeleaf th:if: " + rendered);
        assertFalse(rendered.contains("hidden"), "Empty string is truthy in Thymeleaf th:unless: " + rendered);
    }

    @Test
    void testThIfEmptyStringWorkaroundWithLength() {
        // Use .length > 0 to check for non-empty strings in th:if
        Engine js = new Engine();
        js.put("value", "");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"value.length > 0\">shown</div><div th:unless=\"value.length > 0\">hidden</div>";
        String rendered = markup.processString(html, null);

        assertFalse(rendered.contains("shown"), "Empty string length check should be falsy: " + rendered);
        assertTrue(rendered.contains("hidden"), "Empty string length check should work with th:unless: " + rendered);
    }

    @Test
    void testThIfFalseStringIsFalsy() {
        // Thymeleaf treats "false", "off", "no" as falsy strings
        Engine js = new Engine();
        js.put("value", "false");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"value\">shown</div>";
        String rendered = markup.processString(html, null);

        assertFalse(rendered.contains("shown"), "String 'false' should be falsy: " + rendered);
    }

    @Test
    void testThIfNonEmptyStringIsTruthy() {
        Engine js = new Engine();
        js.put("value", "hello");
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"value\">shown</div>";
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("shown"), "Non-empty string should be truthy: " + rendered);
    }

    @Test
    void testThIfZeroIsFalsy() {
        Engine js = new Engine();
        js.put("count", 0);
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"count\">shown</div><div th:unless=\"count\">hidden</div>";
        String rendered = markup.processString(html, null);

        assertFalse(rendered.contains("shown"), "Zero should be falsy: " + rendered);
        assertTrue(rendered.contains("hidden"), "Zero should be truthy for th:unless: " + rendered);
    }

    @Test
    void testThIfBooleanFalseIsFalsy() {
        Engine js = new Engine();
        js.put("flag", false);
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:if=\"flag\">shown</div><div th:unless=\"flag\">hidden</div>";
        String rendered = markup.processString(html, null);

        assertFalse(rendered.contains("shown"), "Boolean false should be falsy: " + rendered);
        assertTrue(rendered.contains("hidden"), "Boolean false should be truthy for th:unless: " + rendered);
    }

    @Test
    void testThIfEmptyStringFromMapIsTruthy() {
        // Documents the Thymeleaf behavior: empty string from map property is truthy
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <script ka:scope="global">
                _.env = { model: '', provider: 'openrouter', hasApiKey: true };
                _.hasModel = _.env.model.length > 0;
            </script>
            <div th:if="env.model">model-truthy</div>
            <div th:if="hasModel">has-model</div>
            <div th:unless="hasModel">no-model</div>
            """;
        String rendered = markup.processString(html, null);

        // env.model (empty string) is truthy in Thymeleaf
        assertTrue(rendered.contains("model-truthy"), "Empty string from map is truthy in Thymeleaf: " + rendered);
        // But the JS-computed hasModel (using && and .length) is correctly false
        assertFalse(rendered.contains("has-model"), "JS length check should be falsy: " + rendered);
        assertTrue(rendered.contains("no-model"), "JS length check should work: " + rendered);
    }

    @Test
    void testFragmentSignatureWithParamsThrowsFriendlyError() {
        // karate-markup deliberately does not support param lists in
        // th:fragment signatures. The convention is plain `th:fragment="name"`
        // + th:with at the call site (unset names evaluate to null).
        // If a developer accidentally writes `th:fragment="chip(label, count)"`,
        // the engine surfaces a karate-flavoured error that points at the
        // convention rather than at Thymeleaf's strict-matching internals.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:insert=\"~{fragment-params :: chip}\"></div>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null),
                "calling a fragment whose signature declares params must surface as an error");

        // Walk the cause chain — karate-core wraps Thymeleaf's exception in its
        // own RuntimeException for logging.
        Throwable t = thrown;
        boolean foundFriendlyMessage = false;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("karate-markup does not support param lists in th:fragment")
                    && msg.contains("th:fragment=\"name\"")) {
                foundFriendlyMessage = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(foundFriendlyMessage,
                "error must point at karate-markup convention; got: " + thrown.getMessage());
    }

    @Test
    void testFragmentSignatureWithKeywordArgsAlsoThrowsFriendlyError() {
        // Defensive check: even when the caller uses Thymeleaf's keyword-argument
        // form (`~{... :: chip(label='x', count=3)}`), which Thymeleaf accepts
        // against a parameterised signature without raising "Cannot resolve
        // fragment. Signature ...", karate-markup must still surface the
        // convention violation. Without this proactive load-time check the
        // template renders silently with `label` and `count` resolving to null
        // because karate-markup's JS expression engine never sees Thymeleaf's
        // private parameter scope.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = "<div th:insert=\"~{fragment-params :: chip(label='x', count=3)}\"></div>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null),
                "keyword-arg call against a parameterised fragment must surface the convention error");

        Throwable t = thrown;
        boolean foundFriendlyMessage = false;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("karate-markup does not support param lists in th:fragment")) {
                foundFriendlyMessage = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(foundFriendlyMessage,
                "error must point at karate-markup convention; got: " + thrown.getMessage());
    }

    @Test
    void testFragmentMissingThWithVarThrowsWithHint() {
        // Strict-ReferenceError path with hint fallback. A fragment that reads a name
        // the caller didn't bind via th:with now throws a real ReferenceError,
        // wrapped with an actionable hint pointing at the th:with call-site
        // pattern. Templates are no longer expected to silently null-fallback.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // 1. Caller doesn't pass `target` at all. Must throw with hint.
        String htmlNoTarget = "<div th:insert=\"~{fragment-no-params :: optional}\"></div>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(htmlNoTarget, null),
                "fragment reading an unbound bare name must throw");

        // Walk the cause chain — the hint message must point at th:with.
        Throwable t = thrown;
        boolean foundHint = false;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("'target' is not defined")
                    && msg.contains("th:with")) {
                foundHint = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(foundHint,
                "error must hint at the th:with call-site pattern; got: " + thrown.getMessage());

        // 2. Caller passes target via th:with — fragment uses it (no change).
        String htmlWithTarget = "<div th:insert=\"~{fragment-no-params :: optional}\" th:with=\"target: 'hit'\"></div>";
        String renderedWithTarget = markup.processString(htmlWithTarget, null);
        assertTrue(renderedWithTarget.contains("hit"),
                "th:if='target' branch must use the bound value: " + renderedWithTarget);
    }

    @Test
    void testIntraScriptBareReadAfterUnderscoreWriteHintsAtUnderscore() {
        // Within a single ka:scope block, after `_.foo = 'bar'` the
        // bare name `foo` does NOT auto-resolve (this preserves the `_`
        // namespace discipline — writes to template state must always go
        // through `_` and reads must match). The error message points the
        // developer at `_.foo`.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        // Within the same script body, the level-flush hasn't happened yet —
        // bare `foo` is unbound, but `_.foo` exists in the underscore namespace.
        String html = """
            <div>
                <script ka:scope="local">
                    _.foo = 'hello';
                    _.bar = foo + ' world';
                </script>
                <span th:text="bar"></span>
            </div>
            """;
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null),
                "bare `foo` after `_.foo = ...` in the same block must throw");

        Throwable t = thrown;
        boolean foundHint = false;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("'foo' is not defined")
                    && msg.contains("_.foo")) {
                foundHint = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(foundHint,
                "error must hint at `_.foo`; got: " + thrown.getMessage());
    }

    @Test
    void testCommentHandling() {
        // Documents Thymeleaf's three comment shapes as they apply to karate-markup.
        // Use <!--/* */--> for documentation-only comments that must NOT ship to
        // the browser. Plain <!-- --> deliberately passes through (HTML comments
        // are sometimes semantic). The third <!--/*/ /*/--> "prototype-only"
        // wrapper hides its content from a static-HTML preview but EXPOSES the
        // content for template processing — useful for stub data in prototypes,
        // not relevant for hiding doc-strings from production output.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));

        String html = """
            <div>
              <!-- plain html comment, passes through -->
              <!--/* parser-level comment, stripped */-->
              <!--/*/ <span>prototype-only content, surfaces on render</span> /*/-->
              <p>hello</p>
            </div>
            """;
        String rendered = markup.processString(html, null);

        assertTrue(rendered.contains("plain html comment"),
                "Plain <!-- --> comments must pass through untouched: " + rendered);
        assertFalse(rendered.contains("parser-level comment"),
                "Thymeleaf <!--/* */--> parser-level comments must be stripped: " + rendered);
        assertTrue(rendered.contains("<span>prototype-only content"),
                "Thymeleaf <!--/*/ /*/--> exposes its inner content during render: " + rendered);
        assertFalse(rendered.contains("/*/"),
                "Prototype-only markers must be stripped from render: " + rendered);
        assertTrue(rendered.contains("<p>hello</p>"),
                "Surrounding markup must render normally: " + rendered);
    }

    // ========== Declarative Custom-Event Dispatch (`ka:dispatch`) ==========

    @Test
    void testKaDispatchEmitsOnClickWithDetail() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.userId = 'u-42';
                _.roleStr = 'admin';
            </script>
            <button ka:dispatch="open-edit-user" ka:vals="userId:userId,role:roleStr">Edit</button>
            """;
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("window.dispatchEvent(new CustomEvent("),
                "rendered must call window.dispatchEvent: " + rendered);
        assertTrue(rendered.contains("\"open-edit-user\""),
                "event name must be inlined as a JS string literal: " + rendered);
        assertTrue(rendered.contains("\"userId\":\"u-42\""),
                "ka:vals expression must be evaluated server-side: " + rendered);
        assertTrue(rendered.contains("\"role\":\"admin\""), rendered);
        assertTrue(rendered.contains("bubbles: true, composed: true"),
                "CustomEvent must bubble and pierce shadow DOM: " + rendered);
        assertFalse(rendered.contains("ka:dispatch"),
                "ka:dispatch attribute must be removed from rendered output: " + rendered);
    }

    @Test
    void testKaDispatchWithoutValsHasEmptyDetail() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<button ka:dispatch=\"open-add-user\">Add</button>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("\"open-add-user\""), rendered);
        assertTrue(rendered.contains("detail: {}"),
                "missing ka:vals must produce an empty detail object: " + rendered);
    }

    @Test
    void testKaDispatchEscapesEventName() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        // Hostile event name with embedded quote — must be JS-escaped, not break the attribute
        String html = "<button ka:dispatch='evt\"x'>X</button>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("\\\"") || rendered.contains("&quot;"),
                "double-quote in event name must be safely escaped: " + rendered);
    }

    // ========== `ka:dispatch="event @ trigger"` ==========

    @Test
    void testKaDispatchAtHtmxAfterSwap() {
        // `ka:dispatch="<event> @ <trigger>"` re-binds the dispatch
        // trigger from the default `click` to the named DOM/htmx event,
        // emitted via htmx's `hx-on:<trigger>`. Most useful for re-firing
        // CustomEvents after an htmx swap (e.g. notification-stack listeners).
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div ka:dispatch=\"users-refreshed @ htmx:afterSwap\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("hx-on:htmx:afterSwap"),
                "@-trigger must emit hx-on:<event>: " + rendered);
        assertTrue(rendered.contains("window.dispatchEvent(new CustomEvent("),
                "must still emit a CustomEvent dispatch: " + rendered);
        assertTrue(rendered.contains("\"users-refreshed\""),
                "event name must be inlined: " + rendered);
        assertFalse(rendered.contains("onclick"),
                "@-trigger must replace onclick, not co-emit it: " + rendered);
        assertFalse(rendered.contains("ka:dispatch"),
                "ka: attributes must be scrubbed: " + rendered);
    }

    @Test
    void testKaDispatchAtPlainDomEvent() {
        // Works for plain DOM events too (change, submit, etc.).
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<select ka:dispatch=\"role-changed@change\"></select>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("hx-on:change"),
                "plain DOM events also flow through hx-on: " + rendered);
        assertFalse(rendered.contains("onclick"),
                "no onclick when @-trigger is set: " + rendered);
        assertTrue(rendered.contains("\"role-changed\""),
                "LHS of @ is the dispatched event name: " + rendered);
    }

    @Test
    void testKaDispatchAtTolerantOfWhitespace() {
        // Whitespace around the @ delimiter is optional and trimmed.
        // Both `users-refreshed@htmx:afterSwap` and the spaced form work.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String tight = "<div ka:dispatch=\"users-refreshed@htmx:afterSwap\"></div>";
        String spaced = "<div ka:dispatch=\"  users-refreshed   @   htmx:afterSwap  \"></div>";
        String renderedTight = markup.processString(tight, null);
        String renderedSpaced = markup.processString(spaced, null);
        assertTrue(renderedTight.contains("hx-on:htmx:afterSwap"), "tight form: " + renderedTight);
        assertTrue(renderedTight.contains("\"users-refreshed\""), "tight event name: " + renderedTight);
        assertTrue(renderedSpaced.contains("hx-on:htmx:afterSwap"), "spaced form: " + renderedSpaced);
        assertTrue(renderedSpaced.contains("\"users-refreshed\""),
                "spaced event name must be trimmed: " + renderedSpaced);
    }

    @Test
    void testKaDispatchDefaultsToOnClick() {
        // Sanity — without an @ delimiter, the original click-only
        // behavior holds, no htmx dependency introduced.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<button ka:dispatch=\"open-modal\">Open</button>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("onclick"),
                "default behavior is onclick: " + rendered);
        assertFalse(rendered.contains("hx-on:"),
                "no hx-on attribute when @ is absent: " + rendered);
    }

    @Test
    void testKaDispatchRejectsEmptyEventName() {
        // Bare `@trigger` is rejected — an empty event name would dispatch
        // a CustomEvent with no name, which the browser refuses anyway.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<button ka:dispatch=\"@click\">x</button>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null));
        assertHintContains(thrown, "ka:dispatch requires an event name");
    }

    @Test
    void testKaDispatchRejectsEmptyTriggerAfterAt() {
        // `event @` alone is rejected — the trailing `@` signals trigger
        // override, and an empty trigger silently degrades to onclick which
        // is misleading.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<button ka:dispatch=\"open-modal @ \">x</button>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null));
        assertHintContains(thrown, "ka:dispatch trigger must be non-empty");
    }

    @Test
    void testKaDispatchRejectsMultipleAtDelimiters() {
        // `event @ trigger @ extra` is rejected — only one `@` is supported.
        // (lastIndexOf would silently lose information; ambiguous either way.)
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<button ka:dispatch=\"open-modal @ click @ extra\">x</button>";
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> markup.processString(html, null));
        assertHintContains(thrown, "single `event @ trigger` delimiter");
    }

    // ========== Outer-scope Form Mirror (`ka:data-mirror`) ==========

    @Test
    void testKaDataMirrorSimpleScope() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<input ka:data-mirror=\"form\"/>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("type=\"hidden\""),
                "must emit type=hidden: " + rendered);
        assertTrue(rendered.contains("name=\"form\""),
                "must emit name attr from expression: " + rendered);
        assertTrue(rendered.contains(":value=\"JSON.stringify(form)\""),
                "must emit Alpine :value bind that JSON-stringifies expression: " + rendered);
        assertFalse(rendered.contains("ka:data-mirror"),
                "ka:data-mirror attribute must be removed from rendered output: " + rendered);
    }

    @Test
    void testKaDataMirrorSubPath() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<input ka:data-mirror=\"form.contact\"/>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("name=\"form.contact\""),
                "sub-path expression must round-trip into name attr: " + rendered);
        assertTrue(rendered.contains(":value=\"JSON.stringify(form.contact)\""),
                "sub-path must round-trip into JSON.stringify call: " + rendered);
    }

    // ========== JSON-island Hydration (`ka:island`) ==========

    @Test
    void testKaIslandSimple() {
        Engine js = new Engine();
        js.put("users", java.util.List.of(
                java.util.Map.of("userId", "u1", "name", "Alice"),
                java.util.Map.of("userId", "u2", "name", "Bob")));
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div ka:island=\"users\"/>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<script type=\"application/json\" id=\"users-data\">"),
                "must emit script tag with type=application/json and default id=<expr>-data: " + rendered);
        assertTrue(rendered.contains("</script>"),
                "script tag must be closed properly: " + rendered);
        assertTrue(rendered.contains("\"userId\":\"u1\"") && rendered.contains("\"name\":\"Alice\""),
                "must serialize the bound List<Map> as JSON inside the script body: " + rendered);
        assertFalse(rendered.contains("ka:island"),
                "ka:island attribute must not leak to output: " + rendered);
        assertFalse(rendered.contains("<div"),
                "host element must be replaced, not retained: " + rendered);
    }

    @Test
    void testKaIslandCustomId() {
        Engine js = new Engine();
        js.put("users", java.util.List.of());
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div ka:island=\"users:custom-id\"/>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("id=\"custom-id\""),
                "custom id must override the default <expr>-data: " + rendered);
        assertFalse(rendered.contains("id=\"users-data\""),
                "default id must be skipped when custom id is provided: " + rendered);
    }

    @Test
    void testKaIslandNullValue() {
        Engine js = new Engine();
        js.put("missing", null);
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div ka:island=\"missing\"/>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<script type=\"application/json\" id=\"missing-data\">null</script>"),
                "null value must render as the literal `null` (valid JSON): " + rendered);
    }

    // ========== th:with parameter forwarding (built-in Thymeleaf) ==========
    //
    // The existing th:with already handles parameter forwarding cleanly:
    //
    //   • `th:with="foo: 'a', bar: 'b'"` — explicit assignments (Map literal).
    //   • `th:with="foo, bar"`           — JS object shorthand → {foo: foo, bar: bar},
    //                                       reading values from the enclosing scope.
    //
    // Both forms route through MarkupTemplateContext.evalLocalAsObject which wraps
    // the attribute value in `({...})` and evaluates as JS. ES6 shorthand object
    // properties are a standard JS feature; the karate JS engine supports it.

    @Test
    void testThWithMultipleAssignments() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:with=\"foo: 'a', bar: 'b'\" th:text=\"foo + ' ' + bar\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains(">a b<"),
                "th:with must support multiple comma-separated assignments: " + rendered);
    }

    @Test
    void testThWithObjectSpread() {
        // JS spread operator inside an object literal: {...obj} dumps obj's
        // properties as own keys. KaWithProcessor wraps the value in `({...})`
        // and forEach-binds each key as a local var, so `th:with="...obj"`
        // forwards every property of obj as a named template var.
        Engine js = new Engine();
        js.put("ctx", java.util.Map.of("title", "Edit User", "size", "md"));
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:with=\"...ctx\" th:text=\"title + ' (' + size + ')'\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains(">Edit User (md)<"),
                "th:with=\"...obj\" must spread obj's properties as local vars: " + rendered);
    }

    @Test
    void testThWithSpreadCombinedWithExplicitOverride() {
        // Spread plus explicit override: later keys win, just like JS.
        Engine js = new Engine();
        js.put("ctx", java.util.Map.of("title", "Default", "size", "md"));
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:with=\"...ctx, size: 'lg'\" th:text=\"title + ' (' + size + ')'\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains(">Default (lg)<"),
                "explicit assignment after spread must override the spread value: " + rendered);
    }

    @Test
    void testThWithObjectShorthandForwardsParentScope() {
        // Alternative: forward parent's foo + bar via th:with into a nested element
        // using JS object shorthand, no engine support needed beyond what already exists.
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:with=\"foo: 'a', bar: 'b'\">"
                + "<span th:with=\"foo, bar\" th:text=\"foo + ' ' + bar\"></span>"
                + "</div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<span>a b</span>"),
                "th:with with shorthand `foo, bar` must forward parent-scope values: " + rendered);
    }

    // ========== `_.foo` in template attrs (dual-lookup `_`) ==========
    //
    // Under the dual-lookup `_` ObjectLike, `_.foo` reads in template
    // attributes resolve correctly: the underscore map is checked first and
    // any miss falls through to the wrapped Thymeleaf scope (which carries
    // level-flushed values). This replaces the prior "underscore-reach"
    // guard that rejected these reads — both `_.foo` and bare `foo` now
    // work in template attrs after a script's `_.foo = ...` write.

    @Test
    void testUnderscoreReachInTemplateAttrIsSupported() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<script ka:scope=\"global\">_.foo = 'bar';</script>"
                + "<div th:text=\"_.foo\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<div>bar</div>"),
                "`_.foo` in a template attr must resolve via dual-lookup: " + rendered);
    }

    @Test
    void testUnderscoreReachWithDollarBraceWrapperIsSupported() {
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<script ka:scope=\"global\">_.foo = 'bar';</script>"
                + "<div th:text=\"${_.foo}\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<div>bar</div>"),
                "wrapped ${_.foo} must also resolve via dual-lookup: " + rendered);
    }

    @Test
    void testUnderscoreReachAllowsLegitimateUnderscoreNames() {
        // `obj._foo` (underscore-prefixed property) and `_a.foo` (identifier
        // starting with underscore) must not be mistaken for `_.foo` reach.
        Engine js = new Engine();
        js.put("obj", java.util.Map.of("_foo", "ok-prefixed"));
        js.put("_a", java.util.Map.of("foo", "ok-id"));
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = "<div th:text=\"obj._foo + ' / ' + _a.foo\"></div>";
        String rendered = markup.processString(html, null);
        assertTrue(rendered.contains("<div>ok-prefixed / ok-id</div>"),
                "legitimate underscore property/identifier names must not trigger the check: " + rendered);
    }

    private static String collectMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            sb.append(cur.getMessage()).append('\n');
            cur = cur.getCause();
        }
        return sb.toString();
    }

    // ========== `devTrace` Fragment Composition Trace ==========

    private static Markup tracingMarkup(boolean devMode, boolean devTrace) {
        MarkupConfig cfg = new MarkupConfig();
        cfg.setResolver(new RootResourceResolver("classpath:markup"));
        cfg.setEngineSupplier(Engine::new);
        cfg.setDevMode(devMode);
        cfg.setDevTrace(devTrace);
        return Markup.init(cfg);
    }

    @Test
    void testDevTraceOffEmitsNoComments() {
        Markup markup = tracingMarkup(false, false);
        String rendered = markup.processPath("with.html", null);
        assertFalse(rendered.contains("ka:fragment"),
                "default render must not emit trace comments: " + rendered);
        assertFalse(rendered.contains("data-ka-trace"),
                "marker attribute must not leak when trace is off: " + rendered);
    }

    @Test
    void testDevTraceMarksInsertResolution() {
        Markup markup = tracingMarkup(true, true);
        String rendered = markup.processPath("with.html", null);
        // with.html renders a fragment via th:insert with th:with — middleware
        // must wrap the host element with begin/end comments carrying the
        // fragment expression, depth, and the raw th:with payload.
        assertTrue(rendered.contains("ka:fragment-begin (insert) this:with-called"),
                "devTrace must emit a begin comment: " + rendered);
        assertTrue(rendered.contains("ka:fragment-end (insert) this:with-called"),
                "devTrace must emit a paired end comment: " + rendered);
        assertTrue(rendered.contains("with={foo: 'bar', msg: msg}"),
                "trace must surface the call-site th:with expression: " + rendered);
        assertTrue(rendered.contains("depth=0"),
                "depth must be tracked: " + rendered);
        int begin = rendered.indexOf("ka:fragment-begin");
        int firstDiv = rendered.indexOf("<div>bar</div>");
        int end = rendered.indexOf("ka:fragment-end");
        assertTrue(begin >= 0 && firstDiv > begin && end > firstDiv,
                "comments must wrap the fragment content: begin=" + begin
                        + " fragment=" + firstDiv + " end=" + end + "\n" + rendered);
        // marker attribute must be stripped from rendered output
        assertFalse(rendered.contains("data-ka-trace"),
                "internal trace marker attr must not leak: " + rendered);
    }

    @Test
    void testDevTraceRequiresDevMode() {
        // devTrace=true, devMode=false → no trace comments (production safety gate)
        Markup markup = tracingMarkup(false, true);
        String rendered = markup.processPath("with.html", null);
        assertFalse(rendered.contains("ka:fragment"),
                "trace must be a no-op when devMode is false: " + rendered);
        assertFalse(rendered.contains("data-ka-trace"),
                "marker attribute must not leak when devMode is false: " + rendered);
    }

    @Test
    void testDevTraceWrapsReplaceResolution() {
        // th:replace removes the host entirely — the wrapper-element approach
        // (TraceWrappingHandler) handles this case by injecting a synthetic
        // <ka-trace> around the fragment IModel, which becomes the replacement.
        Markup markup = tracingMarkup(true, true);
        String rendered = markup.processPath("trace-replace.html", null);
        assertTrue(rendered.contains("ka:fragment-begin (replace) this:trace-leaf"),
                "th:replace must emit a begin comment: " + rendered);
        assertTrue(rendered.contains("ka:fragment-end (replace) this:trace-leaf"),
                "th:replace must emit a paired end comment: " + rendered);
        // synthetic wrapper element must not leak
        assertFalse(rendered.contains("<ka-trace"),
                "synthetic <ka-trace> wrapper open tag must not leak: " + rendered);
        assertFalse(rendered.contains("</ka-trace>"),
                "synthetic </ka-trace> wrapper close tag must not leak: " + rendered);
        assertFalse(rendered.contains("data-ka-trace"),
                "internal trace marker attr must not leak: " + rendered);
        // begin must precede fragment content; end must follow
        int begin = rendered.indexOf("ka:fragment-begin");
        int leaf = rendered.indexOf("<span>leaf</span>");
        int end = rendered.indexOf("ka:fragment-end");
        assertTrue(begin >= 0 && leaf > begin && end > leaf,
                "comments must wrap fragment content: begin=" + begin + " leaf=" + leaf
                        + " end=" + end + "\n" + rendered);
    }

    @Test
    void testDevTraceTracksNestedDepth() {
        // Nested fragments: parent.html inserts inner.html, inner.html inserts leaf.html.
        // Parent is depth=0, inner=1, leaf=2 — middleware must increment as it descends.
        Markup markup = tracingMarkup(true, true);
        String rendered = markup.processPath("trace-parent.html", null);
        assertTrue(rendered.contains("ka:fragment-begin (insert) this:trace-inner depth=0"), rendered);
        assertTrue(rendered.contains("ka:fragment-begin (insert) this:trace-leaf depth=1"), rendered);
        assertTrue(rendered.contains("ka:fragment-end (insert) this:trace-leaf depth=1"), rendered);
        assertTrue(rendered.contains("ka:fragment-end (insert) this:trace-inner depth=0"), rendered);
        // depths must be properly nested in document order
        int innerBegin = rendered.indexOf("ka:fragment-begin (insert) this:trace-inner");
        int leafBegin = rendered.indexOf("ka:fragment-begin (insert) this:trace-leaf");
        int leafEnd = rendered.indexOf("ka:fragment-end (insert) this:trace-leaf");
        int innerEnd = rendered.indexOf("ka:fragment-end (insert) this:trace-inner");
        assertTrue(innerBegin < leafBegin && leafBegin < leafEnd && leafEnd < innerEnd,
                "comments must nest properly: innerBegin=" + innerBegin + " leafBegin=" + leafBegin
                        + " leafEnd=" + leafEnd + " innerEnd=" + innerEnd + "\n" + rendered);
    }

    @Test
    void testKaDataMirrorInsideEach() {
        // verify the processor renders correctly per iteration when nested in th:each
        Engine js = new Engine();
        Markup markup = Markup.init(js, new RootResourceResolver("classpath:markup"));
        String html = """
            <script ka:scope="global">
                _.rows = [{id: 'a'}, {id: 'b'}];
            </script>
            <form th:each="row, iter: rows">
                <input ka:data-mirror="row"/>
            </form>
            """;
        String rendered = markup.processString(html, null);
        // Two rows → two mirror inputs
        int count = rendered.split(":value=\"JSON.stringify\\(row\\)\"", -1).length - 1;
        assertEquals(2, count, "should emit one mirror input per iteration: " + rendered);
    }

}
