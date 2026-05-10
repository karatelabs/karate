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

import org.thymeleaf.exceptions.TemplateProcessingException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Friendly error translation for the unsupported {@code th:fragment="name(params)"}
 * declared-signature shape.
 *
 * <p>karate-markup intentionally does not support param lists in {@code th:fragment}
 * declarations. The convention is plain {@code th:fragment="chip"} — callers pass
 * values via {@code th:with}, the fragment reads them as ordinary scope variables,
 * and unset names resolve to {@code null} (see Gotcha #21 + #22 in MARKUP_SKILL.md).
 *
 * <p>If a developer accidentally writes {@code th:fragment="chip(label, count)"},
 * one of two things happens:
 * <ul>
 *   <li>Thymeleaf fires a strict signature-matching error — caught and re-thrown
 *       with a karate-flavoured message by {@link #translateSignatureError}.</li>
 *   <li>The caller uses Thymeleaf's keyword-argument form
 *       ({@code ~{... :: chip(label='x', count=3)}}) — Thymeleaf accepts the call,
 *       binds the parameters into Thymeleaf-private scope, but karate-markup's
 *       JS expression engine never sees them. The fragment renders with
 *       {@code label} / {@code count} resolving to {@code null} and the failure is
 *       silent. {@link #checkDeclaredSignature} catches this case proactively at
 *       template-load time before any rendering happens.</li>
 * </ul>
 */
final class FragmentSupport {

    /**
     * Matches a {@code th:fragment} (or {@code data-th-fragment}) attribute whose
     * value contains an opening parenthesis — the disallowed declared-signature
     * shape. The capturing group returns the offending attribute value so the
     * error message can quote it back to the developer.
     *
     * <p>Constraints:
     * <ul>
     *   <li>Word-boundary before {@code th:fragment} avoids matching inside other
     *       attribute names (none currently end in {@code th:fragment} but
     *       defensive).</li>
     *   <li>Tolerates whitespace around the {@code =} (rare but legal HTML).</li>
     *   <li>Accepts double or single quotes.</li>
     *   <li>Requires a {@code (} somewhere inside the quoted value before the
     *       matching close quote.</li>
     * </ul>
     */
    private static final Pattern DECLARED_SIGNATURE = Pattern.compile(
            "\\b(?:data-)?th:fragment\\s*=\\s*[\"']([^\"']*\\([^\"']*)[\"']");

    private FragmentSupport() {
    }

    /**
     * If the given Thymeleaf exception originated from strict-signature matching,
     * throw a {@link TemplateProcessingException} whose message points at the
     * karate-markup convention. Otherwise return without throwing — caller
     * propagates the original.
     */
    static void translateSignatureError(TemplateProcessingException e) {
        String msg = e.getMessage();
        if (msg == null || !msg.contains("Cannot resolve fragment. Signature")) {
            return;
        }
        throw new TemplateProcessingException(unsupportedSignatureMessage(null, null, msg), e);
    }

    /**
     * Scan a raw template body for {@code th:fragment="name(params)"} and throw
     * a friendly {@link TemplateProcessingException} if any are found. Called
     * from the template resource readers so the failure surfaces at template
     * load time rather than as silent {@code null} bindings during render.
     *
     * @param html       the template source about to be parsed
     * @param resourceDescription a human-readable resource path for the error
     *                            (e.g. {@code "components/mega-menu.html"})
     */
    static void checkDeclaredSignature(String html, String resourceDescription) {
        if (html == null) {
            return;
        }
        // Cheap pre-filter — the regex is only worth running on templates that
        // actually contain the literal `th:fragment` (or its HTML5 data-attr
        // alias). Page templates almost never declare fragments, so a plain
        // indexOf scan keeps the happy path at near-zero cost; fragment files
        // (components/) pay the regex once at template-load time.
        if (html.indexOf("th:fragment") < 0 && html.indexOf("th-fragment") < 0) {
            return;
        }
        Matcher m = DECLARED_SIGNATURE.matcher(html);
        if (m.find()) {
            String offending = m.group(1);
            throw new TemplateProcessingException(
                    unsupportedSignatureMessage(offending, resourceDescription, null));
        }
    }

    private static String unsupportedSignatureMessage(String offending, String resource, String thymeleafMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("karate-markup does not support param lists in th:fragment signatures.\n");
        if (offending != null) {
            sb.append("  Found    th:fragment=\"").append(offending).append("\"");
            if (resource != null) {
                sb.append("   in ").append(resource);
            }
            sb.append("\n");
        }
        sb.append("  Change   th:fragment=\"name(p1, p2)\"   ➜   th:fragment=\"name\"\n");
        sb.append("  Pass values via th:with at the call site; the fragment reads them as\n");
        sb.append("  ordinary scope variables. Unset names resolve to null (no typeof guard\n");
        sb.append("  needed). See MARKUP_SKILL.md Gotcha #21.");
        if (thymeleafMsg != null) {
            sb.append("\n  Original Thymeleaf error: ").append(thymeleafMsg);
        }
        return sb.toString();
    }
}
