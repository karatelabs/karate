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

import com.intuit.karate.FileUtils;
import com.intuit.karate.server.ServerConfig;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.server.RequestCycle;
import com.intuit.karate.server.ResourceResolver;
import java.io.InputStream;
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
public class KaScriptAttrProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaScriptAttrProcessor.class);
    private final ResourceResolver resourceResolver;

    public KaScriptAttrProcessor(String dialectPrefix, ServerConfig config) {
        super(TemplateMode.HTML, dialectPrefix, null, false, "src", true, 1000, true);
        resourceResolver = config.getResourceResolver();
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag, AttributeName an, String av, IElementTagStructureHandler sh) {
        InputStream is = resourceResolver.read(av);
        String src = FileUtils.toString(is);
        if (TemplateUtils.hasAncestorElement(ctx, "head")) {
            JsEngine.evalGlobal(src);
            sh.replaceWith(TemplateUtils.generateScriptTags(ctx), false);
        } else {
            RequestCycle.get().evalAndQueue(src);
            sh.removeElement();
        }
    }

}
