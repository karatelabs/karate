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
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Processor for {@code ka:data-mirror} — declarative outer-scope form mirror.
 *
 * <p>Usage:
 * <pre>{@code <input ka:data-mirror="form"/>}</pre>
 *
 * <p>Rewrites the host element to:
 * <pre>{@code <input type="hidden" name="form" :value="JSON.stringify(form)"/>}</pre>
 *
 * <p>This is the inverse companion to {@code ka:data} — where {@code ka:data}
 * ships server state into Alpine, {@code ka:data-mirror} ships Alpine state back
 * to the server. The expression value can be a top-level scope name or any
 * sub-path (e.g. {@code ka:data-mirror="form.contact"}).
 *
 * <p>Use it inside payload-driven Alpine modals where the form lives in an
 * outer {@code x-data} scope (a row click hydrates it via {@code openIt(payload)}),
 * so the inner {@code ka:data} pattern would shadow it. The reactive
 * {@code :value} binding keeps the hidden input in sync with every keystroke,
 * so the POST body always matches the live UI state. The server reads
 * {@code request.paramJson('<expr>')} as it would for any other JSON field.
 *
 * <p>See {@code markup/skill.md} Gotcha #20 in the karate-skills repo for the
 * outer-scope shadowing pitfall this directive collapses.
 */
class KaDataMirrorProcessor extends AbstractAttributeTagProcessor {

    private static final String DATA_MIRROR = "data-mirror";
    private static final int PRECEDENCE = 1000;

    KaDataMirrorProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, DATA_MIRROR, true, PRECEDENCE, true);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IProcessableElementTag tag,
                             AttributeName attributeName, String attributeValue,
                             IElementTagStructureHandler structureHandler) {
        String expr = attributeValue;
        structureHandler.setAttribute("type", "hidden", AttributeValueQuotes.DOUBLE);
        structureHandler.setAttribute("name", expr, AttributeValueQuotes.DOUBLE);
        structureHandler.setAttribute(":value", "JSON.stringify(" + expr + ")", AttributeValueQuotes.DOUBLE);
    }

}
