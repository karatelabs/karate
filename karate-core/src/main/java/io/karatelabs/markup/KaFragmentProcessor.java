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
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.processor.StandardFragmentTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Replacement for Thymeleaf's {@link StandardFragmentTagProcessor} (which is
 * {@code final}, so it must be re-implemented rather than subclassed). Behaviour
 * matches the original — the {@code th:fragment} attribute is a marker that gets
 * stripped after processing — but adds one rule:
 *
 * <p>If the attribute value contains an opening parenthesis, the developer is
 * using the declared-signature form ({@code th:fragment="chip(label, count)"})
 * which karate-markup deliberately does not support. The disallowed form is
 * detected here and rejected with a {@link FragmentSupport#signatureMessage
 * karate-flavoured error} pointing at the convention. Wired in via the
 * instance-of patching loop in {@link MarkupStandardDialect#getProcessors}, so
 * the attribute is intercepted at evaluation time — no template-source scan is
 * needed and only elements that actually carry {@code th:fragment} pay the
 * check.
 */
final class KaFragmentProcessor extends AbstractElementTagProcessor {

    KaFragmentProcessor(TemplateMode templateMode, String dialectPrefix) {
        super(templateMode, dialectPrefix, null, false,
                StandardFragmentTagProcessor.ATTR_NAME, true,
                StandardFragmentTagProcessor.PRECEDENCE);
    }

    @Override
    protected void doProcess(
            ITemplateContext context,
            IProcessableElementTag tag,
            IElementTagStructureHandler structureHandler) {
        AttributeName attributeName = getMatchingAttributeName().getMatchingAttributeName();
        String value = tag.getAttributeValue(attributeName);
        if (value != null && value.indexOf('(') >= 0) {
            throw new TemplateProcessingException(
                    FragmentSupport.signatureMessage(value, null, null));
        }
        // Marker attribute — strip it now that the validation is done.
        structureHandler.removeAttribute(attributeName);
    }

}
