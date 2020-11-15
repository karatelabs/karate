/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.template;

import com.intuit.karate.graal.JsValue;
import com.intuit.karate.http.RequestCycle;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 *
 * @author pthomas3
 */
public class KaHxVarsAttrProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaHxVarsAttrProcessor.class);

    public KaHxVarsAttrProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, "vars", true, 1000, true);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag, AttributeName an, String av, IElementTagStructureHandler sh) {
        JsValue jv = RequestCycle.get().eval("({" + av + "})");
        if (!jv.isObject()) {
            logger.warn("value did not evaluate to map: {}", av);
        } else {
            StringBuilder sb = new StringBuilder();
            Map<String, Object> map = jv.getAsMap();
            map.forEach((k, v) -> {
                if (sb.length() != 0) {
                    sb.append(',');
                }
                sb.append(k).append(':');
                if (v instanceof String) {
                    sb.append('\'').append(v).append('\'');
                } else {
                    sb.append(v);
                }
            });
            sh.setAttribute("hx-vars", sb.toString());
        }
    }

}
