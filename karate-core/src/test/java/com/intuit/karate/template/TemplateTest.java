package com.intuit.karate.template;

import com.intuit.karate.graal.JsEngine;
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

    @Test
    void testHtmlString() {
        JsEngine je = JsEngine.global();
        je.put("message", "hello world");
        KarateTemplateEngine engine = TemplateUtils.forStrings(je);
        String rendered = engine.process("<h1 th:text=\"message\">replace me</h1>");
        assertEquals("<h1>hello world</h1>", rendered);
    }
    
    @Test
    void testHtmlFile() {
        JsEngine je = JsEngine.local();
        KarateTemplateEngine engine = TemplateUtils.forResourcePath(je, "classpath:com/intuit/karate/template");
        String rendered = engine.process("main.html");
        // logger.debug("rendered: {}", rendered);
        assertTrue(rendered.contains("<div id=\"before_one\"><span>js_one</span></div>"));
        assertTrue(rendered.contains("<div id=\"called_one\">called_one</div>"));
        assertTrue(rendered.contains("<div id=\"after_one\"><span>js_one</span></div>"));
    }    

}
