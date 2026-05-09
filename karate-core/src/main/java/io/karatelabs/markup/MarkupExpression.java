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
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

class MarkupExpression implements IStandardExpression {

    private static final Logger logger = LoggerFactory.getLogger(MarkupExpression.class);

    // Detects template expressions reaching into `_.<name>` from a template
    // attribute. The script-state Map is flushed into the Thymeleaf scope on
    // every level-increase (see MarkupTemplateContext.increaseLevel), so by the
    // time an attribute expression evaluates, the `_` Map is empty — script-set
    // values must be read by their bare names, not via the `_.` prefix.
    // Negative lookbehind skips legitimate `obj._foo` / `obj_._foo` cases by
    // requiring the `_` not to be preceded by a word char or dot.
    private static final java.util.regex.Pattern UNDERSCORE_REACH_PATTERN =
            java.util.regex.Pattern.compile("(^|[^\\w.])_\\.[A-Za-z_$]");

    private final String input;

    MarkupExpression(String input) {
        if (input.contains("${")) {
            this.input = "`" + input + "`";
        } else {
            this.input = input;
        }
    }

    @Override
    public String getStringRepresentation() {
        return input;
    }

    @Override
    public Object execute(IExpressionContext ctx) {
        if (UNDERSCORE_REACH_PATTERN.matcher(input).find()) {
            throw new io.karatelabs.js.EngineException(
                    "template expression cannot read `_.<name>` — `_` is the script-local state object "
                            + "and is empty by the time attributes evaluate. Set values in ka:scope as "
                            + "`_.foo = ...`, then read them in template attrs as `foo` (no underscore prefix). "
                            + "Source: " + input,
                    null);
        }
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        return kec.evalLocal(input);
    }

    @Override
    public Object execute(IExpressionContext context, StandardExpressionExecutionContext expContext) {
        return execute(context);
    }

}
