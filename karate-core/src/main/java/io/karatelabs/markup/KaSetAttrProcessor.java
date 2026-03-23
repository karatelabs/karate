/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.markup;

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

class KaSetAttrProcessor extends AbstractElementModelProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaSetAttrProcessor.class);

    private static final String SET = "set";

    KaSetAttrProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, SET, true, 1000);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IModel model, IElementModelStructureHandler sh) {
        int depth = ctx.getElementStack().size();
        IProcessableElementTag tag = ctx.getElementStack().get(depth - 1);
        String name = tag.getAttributeValue(getDialectPrefix(), SET);
        int n = model.size();
        StringBuilder sb = new StringBuilder();
        while (n-- != 0) {
            final ITemplateEvent event = model.get(n);
            if (event instanceof IText) {
                sb.append(((IText) event).getText());
            }
        }
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        kec.setLocal(name, sb.toString().trim());
        model.reset();
    }

}
