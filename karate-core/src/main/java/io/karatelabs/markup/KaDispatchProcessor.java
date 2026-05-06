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
 * Processor for {@code ka:dispatch} — declarative custom-event dispatch on click.
 *
 * <p>Usage:
 * <pre>{@code <button ka:dispatch="open-edit-user" ka:vals="userId:u.userId,role:u.role">Edit</button>}</pre>
 *
 * <p>Emits an {@code onclick} attribute that fires a {@code CustomEvent} with the
 * event name from {@code ka:dispatch} and a {@code detail} object built from the
 * (optional) co-located {@code ka:vals} expression. Server-side values are
 * evaluated at template-render time and inlined directly into the emitted JS —
 * no {@code data-*} round-trip is required.
 *
 * <p>The CustomEvent is fired with {@code bubbles: true, composed: true} so
 * listeners attached at {@code window} or {@code document} level (typical for
 * "open modal" patterns) receive it.
 *
 * <p>For now {@code ka:dispatch} only fires on {@code click}. If a non-click
 * trigger is needed (change, keydown, etc.), open an issue — a follow-up
 * {@code ka:dispatch-on} attribute would extend this.
 */
class KaDispatchProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaDispatchProcessor.class);

    private static final String DISPATCH = "dispatch";
    private static final String VALS = "vals";

    KaDispatchProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, DISPATCH, true, 1000, true);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag,
                             AttributeName attributeName, String attributeValue,
                             IElementTagStructureHandler structureHandler) {
        String eventName = attributeValue;
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        String detailJson = "{}";
        String vals = tag.getAttributeValue(getDialectPrefix(), VALS);
        if (vals != null && !vals.isEmpty()) {
            Object result = kec.evalLocalAsObject(vals);
            if (result instanceof Map) {
                detailJson = Json.of(result).toString();
            } else if (result != null) {
                logger.warn("ka:dispatch ignored ka:vals — did not evaluate to an object: {}", vals);
            }
        }
        String js = "window.dispatchEvent(new CustomEvent(\""
                + escapeJsString(eventName)
                + "\", {detail: " + detailJson + ", bubbles: true, composed: true}))";
        structureHandler.setAttribute("onclick", js, AttributeValueQuotes.SINGLE);
    }

    private static String escapeJsString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

}
