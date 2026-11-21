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
import io.karatelabs.markup.MarkupTemplateContext;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.HashMap;
import java.util.Map;

/**
 * Processor for ka:data attribute that bridges Alpine.js and server-side data.
 *
 * <p>On a &lt;form&gt; element, transforms:</p>
 * <pre>
 * &lt;form ka:data="form:_.initialData"&gt;
 *   &lt;input x-model="form.email"/&gt;
 * &lt;/form&gt;
 * </pre>
 *
 * <p>Into (single-quoted attribute to safely embed JSON; a {@code '}, {@code &amp;} or {@code <}
 * in a value is entity-escaped and the browser decodes it back, so pass raw data and never pre-escape):</p>
 * <pre>
 * &lt;form x-data='{ form: {"email":""} }'&gt;
 *   &lt;input type="hidden" name="form" x-bind:value="JSON.stringify(form)"&gt;
 *   &lt;input x-model="form.email"/&gt;
 * &lt;/form&gt;
 * </pre>
 *
 * <p>Syntax: <code>ka:data="varName:serverExpression"</code></p>
 */
class KaDataProcessor extends AbstractElementModelProcessor {

    private static final String DATA = "data";

    KaDataProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, null, false, DATA, true, 900);
    }

    /** The page runs the CSP build of Alpine: read off the render's own {@link MarkupConfig}. */
    static boolean alpineCsp(ITemplateContext ctx) {
        return ctx instanceof MarkupTemplateContext mtc && mtc.isAlpineCsp();
    }

    /** The form mirror's binding: JSON is a global, out of the CSP build's scope, so it uses the $json magic. */
    static String mirrorBinding(String expr, boolean alpineCsp) {
        return (alpineCsp ? "$json(" : "JSON.stringify(") + expr + ")";
    }

    /**
     * Rewrites JSON so the CSP build's tokenizer reads it as the standard build reads it as a JS
     * object literal: a number with an exponent (json-smart writes 1e7 as {@code 1.0E7}) becomes
     * plain digits, a bare {@code NaN} / {@code Infinity} (json-smart writes those too) becomes
     * {@code null} as {@code JSON.stringify} would, and the {@code \b} / {@code \f} escapes, which
     * it decodes as the letters, become {@code \}{@code u} escapes. String contents are otherwise
     * left alone.
     */
    static String cspSafeJson(String json) {
        StringBuilder sb = null;
        int n = json.length();
        int copied = 0;
        boolean inString = false;
        for (int i = 0; i < n; i++) {
            char c = json.charAt(i);
            String replacement = null;
            int end = i + 1;
            if (inString) {
                if (c == '\\' && i + 1 < n) {
                    char e = json.charAt(i + 1);
                    if (e == 'b' || e == 'f') {
                        replacement = e == 'b' ? "\\u0008" : "\\u000c";
                    }
                    end = i + 2;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (json.startsWith("NaN", i)) {
                replacement = "null";
                end = i + 3;
            } else if (json.startsWith("Infinity", i) || json.startsWith("-Infinity", i)) {
                replacement = "null";
                end = i + (c == '-' ? 9 : 8);
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                boolean exponent = false;
                while (end < n) {
                    char d = json.charAt(end);
                    if (d == 'e' || d == 'E') {
                        exponent = true;
                    } else if (!((d >= '0' && d <= '9') || d == '.' || d == '+' || d == '-')) {
                        break;
                    }
                    end++;
                }
                if (exponent) {
                    replacement = plainNumber(json.substring(i, end));
                }
            }
            if (replacement != null) {
                if (sb == null) sb = new StringBuilder(n + 16);
                sb.append(json, copied, i).append(replacement);
                copied = end;
            }
            i = end - 1;
        }
        if (sb == null) return json;
        return sb.append(json, copied, n).toString();
    }

    /**
     * An exponent number as the plain digits JS reads the same: out of the double range it is
     * what JS makes of it ({@code null} for an overflow, as JSON.stringify writes Infinity; 0 for
     * an underflow), and within it the digits are bounded (a double's exponent is at most 324).
     */
    private static String plainNumber(String token) {
        double d;
        try {
            d = Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return token;
        }
        if (Double.isInfinite(d) || Double.isNaN(d)) return "null";
        if (d == 0) return "0";
        return new java.math.BigDecimal(token).toPlainString();
    }

    @Override
    protected void doProcess(ITemplateContext ctx, IModel model, IElementModelStructureHandler sh) {
        if (model.size() == 0) {
            return;
        }

        // Get the opening tag
        IProcessableElementTag openTag = (IProcessableElementTag) model.get(0);
        String elementName = openTag.getElementCompleteName().toLowerCase();

        // Get attribute value: "varName:serverExpression"
        String attrValue = openTag.getAttributeValue(getDialectPrefix(), DATA);
        if (attrValue == null || attrValue.isEmpty()) {
            return;
        }

        // Parse "varName:expression"
        int colonIndex = attrValue.indexOf(':');
        if (colonIndex <= 0) {
            throw new RuntimeException("ka:data requires format 'varName:expression', got: " + attrValue);
        }

        String varName = attrValue.substring(0, colonIndex).trim();
        String expression = attrValue.substring(colonIndex + 1).trim();

        // Evaluate the server expression to get initial data
        MarkupTemplateContext kec = (MarkupTemplateContext) ctx;
        Object initialData = kec.evalLocal(expression);
        String jsonData = initialData != null ? Json.stringifyStrict(initialData) : "{}";
        boolean alpineCsp = alpineCsp(ctx);
        if (alpineCsp) {
            jsonData = cspSafeJson(jsonData);
        }

        IModelFactory modelFactory = ctx.getModelFactory();

        // Build new attributes map (common for all elements)
        Map<String, String> newAttrs = new HashMap<>();
        for (var attr : openTag.getAllAttributes()) {
            String name = attr.getAttributeCompleteName();
            if (!name.equals(getDialectPrefix() + ":" + DATA)) {
                // getValue() is the source text, entities intact: only a raw ' needs escaping
                String value = attr.getValue();
                newAttrs.put(name, value == null ? null : value.replace("'", "&#39;"));
            }
        }

        // Add x-data with raw JSON — use SINGLE quotes on the attribute
        // to safely embed JSON double quotes (same pattern as ka:vals / hx-vals)
        String xDataValue = "{ " + varName + ": " + jsonData + " }";
        newAttrs.put("x-data", escapeSingleQuoted(xDataValue));

        // Create new opening tag with SINGLE-quoted attributes
        IOpenElementTag newOpenTag = modelFactory.createOpenElementTag(
                openTag.getElementCompleteName(), newAttrs, AttributeValueQuotes.SINGLE, false);
        model.replace(0, newOpenTag);

        // On <form>: Also inject hidden input for form submission
        if ("form".equals(elementName)) {
            Map<String, String> hiddenAttrs = new HashMap<>();
            hiddenAttrs.put("type", "hidden");
            hiddenAttrs.put("name", varName);
            hiddenAttrs.put("x-bind:value", mirrorBinding(varName, alpineCsp));
            IStandaloneElementTag hiddenInput = modelFactory.createStandaloneElementTag(
                    "input", hiddenAttrs, null, false, true);
            model.insert(1, hiddenInput);
        }
    }

    /**
     * Thymeleaf writes attribute values verbatim; escapes what would end a single-quoted
     * attribute or start an entity/tag. JSON's double quotes stay raw.
     */
    static String escapeSingleQuoted(String s) {
        return escape(s, '\'');
    }

    /**
     * Sets a decoded value, keeping an existing target's quote style (else double) and escaping for it.
     */
    static void setEscaped(IProcessableElementTag tag, IElementTagStructureHandler sh, String name, String value) {
        IAttribute existing = tag.getAttribute(name);
        AttributeValueQuotes quotes = existing != null && existing.getValueQuotes() == AttributeValueQuotes.SINGLE
                ? AttributeValueQuotes.SINGLE : AttributeValueQuotes.DOUBLE;
        sh.setAttribute(name, escape(value, quotes == AttributeValueQuotes.SINGLE ? '\'' : '"'), quotes);
    }

    private static String escape(String s, char delimiter) {
        StringBuilder sb = null;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            String rep = c == '&' ? "&amp;" : c == '<' ? "&lt;"
                    : c != delimiter ? null : c == '\'' ? "&#39;" : "&quot;";
            if (rep != null && sb == null) {
                sb = new StringBuilder(n + 16).append(s, 0, i);
            }
            if (sb != null) {
                if (rep != null) sb.append(rep);
                else sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

}
