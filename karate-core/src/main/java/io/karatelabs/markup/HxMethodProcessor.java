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

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Processor for HTMX method attributes: ka:get, ka:post, ka:put, ka:patch, ka:delete.
 * Converts to hx-get, hx-post, etc.
 *
 * Features:
 * - "this" keyword resolves to current template path
 * - Context path prepending if configured
 * - JavaScript expression evaluation for dynamic URLs
 */
class HxMethodProcessor extends AbstractAttributeTagProcessor {

    private final String method;
    private final String contextPath;

    HxMethodProcessor(String dialectPrefix, String method, String contextPath) {
        super(TemplateMode.HTML, dialectPrefix, null, false, method, true, 1000, true);
        this.method = method;
        this.contextPath = contextPath;
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag,
                             AttributeName attributeName, String attributeValue,
                             IElementTagStructureHandler structureHandler) {

        String url = resolveUrl(ctx, attributeValue);

        // Prepend context path if configured
        if (contextPath != null && !contextPath.isEmpty() && url.startsWith("/")) {
            url = contextPath + url;
        }

        // Set the hx-* attribute
        structureHandler.setAttribute("hx-" + method, url);
    }

    private String resolveUrl(ITemplateContext ctx, String value) {
        // Handle "this" keyword - resolves to current template path
        if ("this".equals(value)) {
            return getTemplatePath(ctx);
        }

        // Handle expressions that contain ${...}
        if (value.contains("${")) {
            MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
            Object result = kec.evalLocal("`" + value + "`");
            return result != null ? result.toString() : "";
        }

        // Plain string value
        return value;
    }

    private String getTemplatePath(ITemplateContext ctx) {
        String template = ctx.getTemplateData().getTemplate();
        // Remove .html extension if present
        if (template.endsWith(".html")) {
            template = template.substring(0, template.length() - 5);
        }
        // Ensure it starts with /
        if (!template.startsWith("/")) {
            template = "/" + template;
        }
        return template;
    }

}
