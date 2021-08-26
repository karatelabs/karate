package com.intuit.karate.template;

import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.resource.ResourceResolver;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class TemplateTest {

    static final Logger logger = LoggerFactory.getLogger(TemplateTest.class);

    private static String render(String resource) {
        JsEngine je = JsEngine.local();
        KarateTemplateEngine engine = TemplateUtils.forResourceRoot(je, "classpath:com/intuit/karate/template");
        return engine.process(resource);
    }

    @Test
    void testHtmlString() {
        JsEngine je = JsEngine.global();
        je.put("message", "hello world");
        KarateTemplateEngine engine = TemplateUtils.forStrings(je, new ResourceResolver("classpath:com/intuit/karate/template"));
        String rendered = engine.process("<div><div th:text=\"message\"></div><div th:replace=\"root:temp.html\"></div></div>");
        assertEquals("<div><div>hello world</div><div>temp</div></div>", rendered);
    }

    @Test
    void testHtmlFile() {
        String rendered = render("main.html");
        assertTrue(rendered.contains("<div id=\"before_one\"><span>js_one</span></div>"));
        assertTrue(rendered.contains("<div id=\"called_one\">called_one</div>"));
        assertTrue(rendered.contains("<div id=\"after_one\"><span>js_one</span></div>"));
    }

    @Test
    void testKaSet() {
        String rendered = render("ka-set.html");
        assertEquals(rendered.replaceAll("\\r", "").trim(), "<div>"
                + "first line\n"
                + "second line"
                + "</div>");
    }

    @Test
    void testWith() {
        String rendered = render("with.html");
        assertTrue(rendered.contains("<div>bar</div>"));
        assertTrue(rendered.contains("<div>hello world</div>"));
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
        File file = ResourceUtils.getFileRelativeTo(getClass(), "temp.js");
        String rendered = render("nocache.html");
        assertTrue(rendered.contains("<script src=\"temp.js?ts=" + file.lastModified() + "\"></script>"));
    }

}
