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

import io.karatelabs.common.Json;
import io.karatelabs.markup.MarkupTemplateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Map;

/**
 * Processor for ka:vals attribute.
 * Converts to hx-vals with JSON-encoded values.
 *
 * Usage:
 * - ka:vals="key:value" -> hx-vals='{"key":"value"}'
 * - ka:vals="key:${expr}" -> hx-vals='{"key":evaluated}'
 * - ka:vals="k1:v1,k2:v2" -> hx-vals='{"k1":"v1","k2":"v2"}'
 * - ka:vals="${object}" -> hx-vals='{"key":"value",...}'
 */
class HxValsProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HxValsProcessor.class);

    HxValsProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, "vals", true, 1000, true);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag,
                             AttributeName attributeName, String attributeValue,
                             IElementTagStructureHandler structureHandler) {
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        Object result = kec.evalLocalAsObject(attributeValue);
        if (!(result instanceof Map)) {
            logger.warn("ka:vals did not evaluate to json: {}", attributeValue);
        } else {
            String json = Json.of(result).toString();
            structureHandler.setAttribute("hx-vals", json, AttributeValueQuotes.SINGLE);
        }
    }

}
