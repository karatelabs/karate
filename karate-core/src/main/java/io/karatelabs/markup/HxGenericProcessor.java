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

import io.karatelabs.markup.MarkupTemplateContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Generic processor for HTMX pass-through attributes.
 * Converts ka:* to hx-* (e.g., ka:target → hx-target, ka:swap → hx-swap).
 *
 * Supports JavaScript expression evaluation for dynamic values when ${...} is used.
 *
 * Common attributes handled:
 * - ka:target → hx-target (CSS selector for element to update)
 * - ka:swap → hx-swap (how content should be swapped: innerHTML, outerHTML, etc.)
 * - ka:trigger → hx-trigger (what triggers the request)
 * - ka:push-url → hx-push-url (update browser URL)
 * - ka:select → hx-select (select content from response)
 * - ka:confirm → hx-confirm (confirmation dialog)
 * - ka:indicator → hx-indicator (loading indicator selector)
 * - ka:boost → hx-boost (enhance regular links)
 * - ka:headers → hx-headers (additional headers)
 * - ka:include → hx-include (include additional inputs)
 * - ka:sync → hx-sync (synchronization strategy)
 * - ka:disabled-elt → hx-disabled-elt (disable elements during request)
 * - ka:encoding → hx-encoding (form encoding)
 * - ka:ext → hx-ext (extensions)
 * - ka:history → hx-history (history behavior)
 * - ka:history-elt → hx-history-elt (history element)
 * - ka:preserve → hx-preserve (preserve element)
 * - ka:prompt → hx-prompt (prompt dialog)
 * - ka:replace-url → hx-replace-url (replace URL in history)
 * - ka:request → hx-request (request configuration)
 * - ka:validate → hx-validate (form validation)
 */
class HxGenericProcessor extends AbstractAttributeTagProcessor {

    private final String attributeName;

    HxGenericProcessor(String dialectPrefix, String attributeName) {
        super(TemplateMode.HTML, dialectPrefix, null, false, attributeName, true, 1000, true);
        this.attributeName = attributeName;
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag,
                             AttributeName attrName, String attributeValue,
                             IElementTagStructureHandler structureHandler) {

        String value = resolveValue(ctx, attributeValue);

        // Convert ka:target to hx-target (attribute name may contain hyphens)
        structureHandler.setAttribute("hx-" + attributeName, value);
    }

    private String resolveValue(ITemplateContext ctx, String value) {
        // Handle expressions that contain ${...}
        if (value.contains("${")) {
            MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
            Object result = kec.evalLocal("`" + value + "`");
            return result != null ? result.toString() : "";
        }

        // Plain string value
        return value;
    }

}
