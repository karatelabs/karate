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
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class KaEachProcessor extends AbstractAttributeTagProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KaEachProcessor.class);

    private static final int PRECEDENCE = 200;
    private static final String ATTR_NAME = "each";

    KaEachProcessor(final TemplateMode templateMode, final String dialectPrefix) {
        super(templateMode, dialectPrefix, null, false, ATTR_NAME, true, PRECEDENCE, true);
    }

    @Override
    protected void doProcess(
            final ITemplateContext ctx,
            final IProcessableElementTag tag,
            final AttributeName attributeName, String av,
            final IElementTagStructureHandler structureHandler) {
        int pos = av.indexOf(':');
        String iterVarName;
        String statusVarName = null;
        if (pos == -1) {
            iterVarName = "_";
        } else {
            String varPart = av.substring(0, pos).trim();
            av = av.substring(pos + 1).trim();
            // Check for status variable: "item, iter" or just "item"
            int commaPos = varPart.indexOf(',');
            if (commaPos != -1) {
                iterVarName = varPart.substring(0, commaPos).trim();
                statusVarName = varPart.substring(commaPos + 1).trim();
            } else {
                iterVarName = varPart;
            }
        }
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        Object value = kec.evalLocal(av);
        // Convert Map to list of entry objects with 'key' and 'value' properties
        // This enables Thymeleaf-style iteration: th:each="entry : someMap"
        // where entry.key and entry.value are accessible
        if (value instanceof Map<?, ?> map) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("key", entry.getKey());
                entryMap.put("value", entry.getValue());
                entries.add(entryMap);
            }
            value = entries;
        }
        structureHandler.iterateElement(iterVarName, statusVarName, value);
    }

}
