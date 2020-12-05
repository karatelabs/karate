package com.intuit.karate.template;

import com.intuit.karate.graal.JsEngine;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;

/**
 *
 * @author pthomas3
 */
class TemplateTest {

    static final Logger logger = LoggerFactory.getLogger(TemplateTest.class);

    @Test
    void testHtml() {
        JsEngine je = JsEngine.global();
        je.put("message", "hello world");
        ITemplateEngine engine = TemplateUtils.createEngine(je);
        String rendered = engine.process("<h1 th:text=\"message\">replace me</h1>", TemplateContext.LOCALE_US);
        assertEquals("<h1>hello world</h1>", rendered);
    }

}
