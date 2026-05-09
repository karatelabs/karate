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
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IText;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processor for {@code ka:island} — JSON-island hydration plumbing for Alpine
 * widgets that read a server-side payload at init time.
 *
 * <p>Usage:
 * <pre>{@code <div ka:island="users"/>}</pre>
 *
 * <p>Replaces the host element with:
 * <pre>{@code <script type="application/json" id="users-data">[{"userId":"u1",...}]</script>}</pre>
 *
 * <p>The expression value is treated as a simple identifier (or any
 * server-side JS expression) and evaluated once at template-render time;
 * the result is JSON-stringified and embedded as the script body. The
 * {@code id} attribute defaults to {@code &lt;name&gt;-data} matching the
 * established playground convention; pass {@code ka:island="varName:custom-id"}
 * to override.
 *
 * <p>Client-side hydration is unchanged from the hand-rolled pattern:
 * <pre>{@code
 *   init() {
 *     this.users = JSON.parse(document.getElementById('users-data').textContent);
 *   }
 * }</pre>
 *
 * <p>Resolves Gotcha #14 in {@code MARKUP_SKILL.md} (the boilerplate {@code <script
 * type="application/json" id="x" th:utext="xJson">[]</script>} pattern).
 *
 * <p>Null values render as the literal {@code null} (valid JSON); call sites
 * that prefer an empty-array default should set {@code _.users = _.users || [];}
 * before referencing them in the directive.
 */
class KaIslandProcessor extends AbstractElementModelProcessor {

    private static final String ISLAND = "island";
    private static final int PRECEDENCE = 1000;

    KaIslandProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, ISLAND, true, PRECEDENCE);
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IModel model, IElementModelStructureHandler sh) {
        if (model.size() == 0) {
            return;
        }
        IProcessableElementTag openTag = (IProcessableElementTag) model.get(0);
        String attrValue = openTag.getAttributeValue(getDialectPrefix(), ISLAND);
        if (attrValue == null || attrValue.isEmpty()) {
            return;
        }
        String expr = attrValue.trim();
        // Optional `:custom-id` suffix overrides the default `<expr>-data` id.
        // The naive last-colon split is enough for simple expression + id pairs;
        // for complex JS expressions containing `:` (ternaries etc.), pre-compute
        // on `_` and reference the resulting var here.
        String customId = null;
        int lastColon = expr.lastIndexOf(':');
        if (lastColon > 0) {
            String idCandidate = expr.substring(lastColon + 1).trim();
            // Accept as id only if it's a valid HTML id-ish token — letters,
            // digits, hyphen, underscore. Avoids misinterpreting ternaries.
            if (!idCandidate.isEmpty() && idCandidate.matches("[A-Za-z0-9_-]+")) {
                customId = idCandidate;
                expr = expr.substring(0, lastColon).trim();
            }
        }
        String scriptId = customId != null ? customId : expr + "-data";

        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        Object value = kec.evalLocal(expr);
        String jsonContent = value != null ? Json.stringifyStrict(value) : "null";

        IModelFactory f = ctx.getModelFactory();
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("type", "application/json");
        attrs.put("id", scriptId);
        IOpenElementTag scriptOpen = f.createOpenElementTag("script", attrs, AttributeValueQuotes.DOUBLE, false);
        IText body = f.createText(jsonContent);
        ICloseElementTag scriptClose = f.createCloseElementTag("script");

        model.reset();
        model.add(scriptOpen);
        model.add(body);
        model.add(scriptClose);
    }

}
