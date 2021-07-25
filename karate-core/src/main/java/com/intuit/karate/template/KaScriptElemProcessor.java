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

import com.intuit.karate.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.IText;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 *
 * @author pthomas3
 */
public class KaScriptElemProcessor extends AbstractElementModelProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaScriptElemProcessor.class);
    
    protected static final String SCOPE = "scope";
    protected static final String LOCAL = "local";
    protected static final String HEAD = "head";
    protected static final String NOCACHE = "nocache";

    public KaScriptElemProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, "script", false, SCOPE, true, 1000);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IModel model, IElementModelStructureHandler sh) {        
        int depth = ctx.getElementStack().size();
        IProcessableElementTag tag = ctx.getElementStack().get(depth - 1);
        String scope = tag.getAttributeValue(getDialectPrefix(), SCOPE);
        int n = model.size();
        boolean isHead = TemplateUtils.hasAncestorElement(ctx, HEAD);
        IModel headModel = null;
        while (n-- != 0) {
            final ITemplateEvent event = model.get(n);
            if (event instanceof IText) {
                String text = StringUtils.trimToNull(((IText) event).getText());
                if (text != null) {
                    if (LOCAL.equals(scope)) {
                        KarateEngineContext.get().evalLocal(text, false);
                    } else {
                        KarateEngineContext.get().evalGlobal(text);
                    }
                }
                if (isHead && headModel == null) {
                    headModel = TemplateUtils.generateHeadScriptTag(ctx);
                }
            }
        }
        model.reset();
        if (headModel != null) {
            model.addModel(headModel);
        }
    }

}
