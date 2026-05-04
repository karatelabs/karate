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

}
