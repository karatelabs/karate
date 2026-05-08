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

import org.thymeleaf.model.IProcessableElementTag;

/**
 * Static helpers for the devmode fragment-trace feature. The runtime mechanic
 * lives in two places that this class glues together:
 *
 * <ul>
 *   <li>{@link KaInsertProcessor} / {@link KaReplaceProcessor} wrap the
 *       structure handler before delegating to Thymeleaf's standard fragment
 *       resolution. The wrapper ({@link TraceWrappingHandler}) intercepts
 *       {@code setBody} / {@code replaceWith} and inserts a synthetic
 *       {@code <ka-trace>} element around the resolved fragment IModel,
 *       carrying a {@link #MARKER_ATTR} payload (kind, fragment expression,
 *       raw {@code th:with} text).</li>
 *   <li>{@link FragmentTraceHandler} (an {@code ITemplateHandler}
 *       post-processor) sees the synthetic {@code <ka-trace>} tags downstream,
 *       emits {@code <!-- ka:fragment-begin/end -->} comments around the
 *       wrapped fragment, and suppresses the {@code <ka-trace>} tags
 *       themselves so the rendered HTML stays clean.</li>
 * </ul>
 *
 * <p>The wrapping approach replaced an earlier attribute-on-host strategy. The
 * earlier approach worked for {@code th:insert} (host preserved → attribute
 * survives) but failed for {@code th:replace} (host removed → attribute lost).
 * Wrapping the inner model uniformly handles both, since the wrapper sits
 * inside the fragment IModel that gets emitted in either case.
 *
 * <p>Gating: trace is silently a no-op unless BOTH {@code devMode} and
 * {@code devTrace} are enabled on {@link MarkupConfig}. Production safety —
 * never leak fragment names or inner template structure to end users.
 */
final class FragmentTrace {

    /** Synthetic wrapper element name. Never matched by any processor, suppressed by the post-processor. */
    static final String WRAPPER_TAG = "ka-trace";

    /** Attribute on the synthetic wrapper carrying the trace payload. */
    static final String MARKER_ATTR = "data-ka-trace";

    private FragmentTrace() {
    }

    static boolean enabled(MarkupConfig config) {
        return config != null && config.isDevMode() && config.isDevTrace();
    }

    /**
     * Build the marker payload string for a fragment-resolution call. Format:
     * {@code "kind|expr"} or {@code "kind|expr|with-text"} (pipe-delimited).
     * The companion {@link FragmentTraceHandler#formatComment} parses it back.
     */
    static String buildPayload(String kind, String fragmentExpr, IProcessableElementTag tag) {
        StringBuilder sb = new StringBuilder(kind).append('|').append(sanitize(fragmentExpr));
        String withExpr = tag.getAttributeValue("th", "with");
        if (withExpr != null && !withExpr.isEmpty()) {
            sb.append('|').append(sanitize(withExpr));
        }
        return sb.toString();
    }

    /**
     * Defang any embedded {@code -->} (would close a trace comment early) and
     * pipe characters (would break the payload delimiter). Belt-and-braces —
     * this runs only in devMode anyway, but cheap and correct.
     */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("-->", "--&gt;").replace("|", "&#124;");
    }

}
