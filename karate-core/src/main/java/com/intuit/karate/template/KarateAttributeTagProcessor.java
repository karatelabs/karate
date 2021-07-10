/*
 * The MIT License
 *
 * Copyright 2021 Intuit Inc.
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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.processor.StandardConditionalFixedValueTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.util.ArrayUtils;
import org.thymeleaf.util.EscapedAttributeUtils;

/**
 * derived from
 * org.thymeleaf.standard.processor.AbstractStandardMultipleAttributeModifierTagProcessor
 *
 * @author pthomas3
 */
abstract class KarateAttributeTagProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KarateAttributeTagProcessor.class);

    protected enum ModificationType {
        SUBSTITUTION, APPEND, PREPEND, APPEND_WITH_SPACE, PREPEND_WITH_SPACE
    }

    private final ModificationType modificationType;

    protected KarateAttributeTagProcessor(
            final TemplateMode templateMode, final String dialectPrefix,
            final String attrName, final int precedence,
            final ModificationType modificationType) {
        super(templateMode, dialectPrefix, null, false, attrName, true, precedence, true);
        this.modificationType = modificationType;
    }

    @Override
    protected final void doProcess(
            final ITemplateContext context,
            final IProcessableElementTag tag,
            final AttributeName attributeName, final String av,
            final IElementTagStructureHandler structureHandler) {
        JsValue jv = KarateEngineContext.get().evalLocal("({" + av + "})", true);
        if (!jv.isObject()) {
            logger.warn("value did not evaluate to json: {}", av);
            return;
        }
        Map<String, Object> map = jv.getAsMap();
        map.forEach((k, v) -> {
            if (getTemplateMode() == TemplateMode.HTML
                    && this.modificationType == ModificationType.SUBSTITUTION
                    && ArrayUtils.contains(StandardConditionalFixedValueTagProcessor.ATTR_NAMES, k)) {
                // is a fixed-value conditional one, like "selected", which can only
                // appear as selected="selected" or not appear at all.
                if (JsValue.isTruthy(v)) {
                    structureHandler.setAttribute(k, k);
                } else {
                    structureHandler.removeAttribute(k);
                }
            } else {
                // is a "normal" attribute, not a fixed-value conditional one - or we are not just replacing
                final String newAttributeValue
                        = EscapedAttributeUtils.escapeAttribute(getTemplateMode(), v == null ? null : v.toString());
                if (newAttributeValue == null || newAttributeValue.length() == 0) {
                    if (this.modificationType == ModificationType.SUBSTITUTION) {
                        // equivalent to simply removing
                        structureHandler.removeAttribute(k);
                    }
                    // prepend and append simply ignored in this case
                } else {
                    if (this.modificationType == ModificationType.SUBSTITUTION
                            || !tag.hasAttribute(k)
                            || tag.getAttributeValue(k).length() == 0) {
                        // normal value replace
                        structureHandler.setAttribute(k, newAttributeValue);
                    } else {
                        String currentValue = tag.getAttributeValue(k);
                        if (this.modificationType == ModificationType.APPEND) {
                            structureHandler.setAttribute(k, currentValue + newAttributeValue);
                        } else if (this.modificationType == ModificationType.APPEND_WITH_SPACE) {
                            structureHandler.setAttribute(k, currentValue + ' ' + newAttributeValue);
                        } else if (this.modificationType == ModificationType.PREPEND) {
                            structureHandler.setAttribute(k, newAttributeValue + currentValue);
                        } else { // modification type is PREPEND_WITH_SPACE
                            structureHandler.setAttribute(k, newAttributeValue + ' ' + currentValue);
                        }
                    }

                }
            }
        });
    }

    public static class KarateAttrTagProcessor extends KarateAttributeTagProcessor {

        public KarateAttrTagProcessor(final TemplateMode templateMode, final String dialectPrefix) {
            super(templateMode, dialectPrefix, "attr", 700, ModificationType.SUBSTITUTION);
        }

    }

    public static class KarateAttrappendTagProcessor extends KarateAttributeTagProcessor {

        public KarateAttrappendTagProcessor(final TemplateMode templateMode, final String dialectPrefix) {
            super(templateMode, dialectPrefix, "attrappend", 900, ModificationType.APPEND);
        }

    }

    public static class KarateAttrprependTagProcessor extends KarateAttributeTagProcessor {

        public KarateAttrprependTagProcessor(final TemplateMode templateMode, final String dialectPrefix) {
            super(templateMode, dialectPrefix, "attrprepend", 900, ModificationType.PREPEND);
        }

    }

}
