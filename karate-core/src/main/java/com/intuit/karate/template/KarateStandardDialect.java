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

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;
import org.thymeleaf.standard.expression.StandardExpressionParser;
import org.thymeleaf.standard.processor.StandardAttrTagProcessor;
import org.thymeleaf.standard.processor.StandardAttrappendTagProcessor;
import org.thymeleaf.standard.processor.StandardAttrprependTagProcessor;
import org.thymeleaf.standard.processor.StandardEachTagProcessor;
import org.thymeleaf.standard.processor.StandardWithTagProcessor;

/**
 *
 * @author pthomas3
 */
public class KarateStandardDialect extends StandardDialect implements IStandardVariableExpressionEvaluator, IStandardExpressionParser {

    private static final Logger logger = LoggerFactory.getLogger(KarateStandardDialect.class);

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
        Set<IProcessor> patched = new HashSet(processors.size());
        for (IProcessor p : processors) {
            if (p instanceof StandardEachTagProcessor) {
                p = new KarateEachTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardWithTagProcessor) {
                p = new KarateWithTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrTagProcessor) {
                p = new KarateAttributeTagProcessor.KarateAttrTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrappendTagProcessor) {
                p = new KarateAttributeTagProcessor.KarateAttrappendTagProcessor(p.getTemplateMode(), dialectPrefix);
            }
            if (p instanceof StandardAttrprependTagProcessor) {
                p = new KarateAttributeTagProcessor.KarateAttrprependTagProcessor(p.getTemplateMode(), dialectPrefix);
            }            
            patched.add(p);
        }
        return patched;
    }

    @Override
    public Object evaluate(IExpressionContext ctx, IStandardVariableExpression ve, StandardExpressionExecutionContext ec) {
        // found to be used for th:attrappend="data-parent=${expression}"
        KarateExpression ke = new KarateExpression(ve.getExpression());
        return ke.execute(ctx);
    }

    @Override
    public IStandardExpression parseExpression(IExpressionContext context, String input) {
        if (input.charAt(0) == '~') { // template
            return expressionParser.parseExpression(context, input);
        }
        return new KarateExpression(input);
    }

}
