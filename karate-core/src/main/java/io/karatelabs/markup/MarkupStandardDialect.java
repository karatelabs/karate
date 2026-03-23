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
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.expression.*;
import org.thymeleaf.standard.processor.*;

import java.util.HashSet;
import java.util.Set;

class MarkupStandardDialect extends StandardDialect implements IStandardVariableExpressionEvaluator, IStandardExpressionParser {

    private static final Logger logger = LoggerFactory.getLogger(MarkupStandardDialect.class);

    private final StandardExpressionParser expressionParser = new StandardExpressionParser();

    @Override
    public IStandardVariableExpressionEvaluator getVariableExpressionEvaluator() {
        return this;
    }

    @Override
    public IStandardExpressionParser getExpressionParser() {
        return this;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = StandardDialect.createStandardProcessorsSet(dialectPrefix);
        Set<IProcessor> patched = new HashSet<>(processors.size());
        for (IProcessor p : processors) {
            if (p instanceof StandardEachTagProcessor) {
                p = new KaEachProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardWithTagProcessor) {
                p = new KaWithProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrTagProcessor) {
                p = new KaAttributeProcessor.KarateAttrTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrappendTagProcessor) {
                p = new KaAttributeProcessor.KarateAttrappendTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrprependTagProcessor) {
                p = new KaAttributeProcessor.KarateAttrprependTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardInsertTagProcessor) {
                p = new KaInsertProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardReplaceTagProcessor) {
                p = new KaReplaceProcessor(p.getTemplateMode(), dialectPrefix);
            }
            patched.add(p);
        }
        return patched;
    }

    @Override
    public Object evaluate(IExpressionContext ctx, IStandardVariableExpression ve, StandardExpressionExecutionContext ec) {
        // found to be used for th:attrappend="data-parent=${expression}"
        MarkupExpression ke = new MarkupExpression(ve.getExpression());
        return ke.execute(ctx);
    }

    @Override
    public IStandardExpression parseExpression(IExpressionContext context, String input) {
        if (input.charAt(0) == '~') { // template
            return expressionParser.parseExpression(context, input);
        }
        return new MarkupExpression(input);
    }

}
